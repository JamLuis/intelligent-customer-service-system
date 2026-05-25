#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/.runtime"
LOG_DIR="$RUNTIME_DIR/logs"
PID_DIR="$RUNTIME_DIR/pids"
GEMMA_MODEL_PATH="${GEMMA_MODEL_PATH:-/Users/lucas/Work/Personal/llama.cpp-kleidiai/models/gemma-4-E4B-it-Q4_0.gguf}"
GEMMA_OLLAMA_PORT="${GEMMA_OLLAMA_PORT:-11435}"
GEMMA_OLLAMA_MODEL="${GEMMA_OLLAMA_MODEL:-gemma-4-E4B-it-Q4_0}"
GEMMA_OLLAMA_MODELS_DIR="${GEMMA_OLLAMA_MODELS_DIR:-$RUNTIME_DIR/ollama-gemma/models}"
GEMMA_MIN_BYTES="${GEMMA_MIN_BYTES:-536870912}"
STARTUP_TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-60}"
PID_FILE="$PID_DIR/ollama-gemma.pid"
LOG_FILE="$LOG_DIR/ollama-gemma.log"
MODELFILE="$RUNTIME_DIR/ollama-gemma/Modelfile"

mkdir -p "$LOG_DIR" "$PID_DIR" "$(dirname "$MODELFILE")" "$GEMMA_OLLAMA_MODELS_DIR"

log() {
  printf '[gemma-ollama] %s\n' "$1"
}

base_url() {
  printf 'http://127.0.0.1:%s' "$GEMMA_OLLAMA_PORT"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "missing command: $1"
    exit 1
  fi
}

is_pid_running() {
  [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" >/dev/null 2>&1
}

http_ready() {
  curl -fsS --max-time 2 "$(base_url)/api/tags" >/dev/null 2>&1
}

wait_http() {
  local deadline
  deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
  log "waiting for Ollama: $(base_url)"
  until http_ready; do
    if (( SECONDS >= deadline )); then
      log "Ollama did not become ready within ${STARTUP_TIMEOUT_SECONDS}s"
      log "check log: $LOG_FILE"
      return 1
    fi
    sleep 1
  done
  log "Ollama ready: $(base_url)"
}

write_modelfile() {
  if [[ ! -f "$GEMMA_MODEL_PATH" ]]; then
    log "GGUF model file not found: $GEMMA_MODEL_PATH"
    exit 1
  fi

  local byte_size
  byte_size="$(wc -c < "$GEMMA_MODEL_PATH" | tr -d ' ')"
  if (( byte_size < GEMMA_MIN_BYTES )); then
    log "GGUF model file looks incomplete: $GEMMA_MODEL_PATH"
    log "file size is ${byte_size} bytes, expected at least ${GEMMA_MIN_BYTES} bytes for this Gemma GGUF preset"
    log "please re-download the GGUF model file, then run: $0 recreate"
    exit 1
  fi

  cat > "$MODELFILE" <<EOF_MODELFILE
FROM $GEMMA_MODEL_PATH
PARAMETER temperature 0.2
PARAMETER num_ctx 8192
EOF_MODELFILE
}

model_exists() {
  OLLAMA_HOST="$(base_url)" OLLAMA_MODELS="$GEMMA_OLLAMA_MODELS_DIR" ollama list 2>/dev/null | awk '{print $1}' | grep -qx "$GEMMA_OLLAMA_MODEL"
}

ensure_model() {
  write_modelfile
  if model_exists; then
    log "model ready: $GEMMA_OLLAMA_MODEL"
    return
  fi

  log "creating Ollama model '$GEMMA_OLLAMA_MODEL' from GGUF"
  if ! OLLAMA_HOST="$(base_url)" OLLAMA_MODELS="$GEMMA_OLLAMA_MODELS_DIR" ollama create "$GEMMA_OLLAMA_MODEL" -f "$MODELFILE"; then
    log "failed to create Ollama model from GGUF"
    log "if Ollama reports tensor offset/size errors, the GGUF file is usually truncated or corrupted"
    exit 1
  fi
  log "model created: $GEMMA_OLLAMA_MODEL"
}

start_server() {
  require_command ollama

  if http_ready; then
    log "Ollama already running: $(base_url)"
  else
    if is_pid_running; then
      log "stale process exists but HTTP is not ready, pid $(cat "$PID_FILE")"
      stop_server
    fi
    log "starting Ollama for Gemma on port $GEMMA_OLLAMA_PORT"
    (
      cd "$ROOT_DIR"
      nohup env OLLAMA_HOST="127.0.0.1:$GEMMA_OLLAMA_PORT" OLLAMA_MODELS="$GEMMA_OLLAMA_MODELS_DIR" ollama serve > "$LOG_FILE" 2>&1 &
      echo $! > "$PID_FILE"
    )
    log "pid $(cat "$PID_FILE"), log $LOG_FILE"
    wait_http
  fi

  ensure_model
  log "OpenAI-compatible base URL: $(base_url)/v1"
  log "model name: $GEMMA_OLLAMA_MODEL"
}

stop_server() {
  if is_pid_running; then
    log "stopping Ollama Gemma, pid $(cat "$PID_FILE")"
    kill "$(cat "$PID_FILE")" >/dev/null 2>&1 || true
  fi
  rm -f "$PID_FILE"

  local pids
  pids="$(lsof -tiTCP:"$GEMMA_OLLAMA_PORT" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    log "stopping listeners on port $GEMMA_OLLAMA_PORT: $pids"
    kill $pids >/dev/null 2>&1 || true
  fi
}

status_server() {
  if http_ready; then
    log "running, HTTP ready: $(base_url)"
    if model_exists; then
      log "model ready: $GEMMA_OLLAMA_MODEL"
    else
      log "model missing: $GEMMA_OLLAMA_MODEL"
    fi
  else
    log "stopped"
  fi
}

recreate_model() {
  start_server
  log "recreating model: $GEMMA_OLLAMA_MODEL"
  OLLAMA_HOST="$(base_url)" OLLAMA_MODELS="$GEMMA_OLLAMA_MODELS_DIR" ollama rm "$GEMMA_OLLAMA_MODEL" >/dev/null 2>&1 || true
  ensure_model
}

smoke_chat() {
  start_server
  curl -fsS "$(base_url)/v1/chat/completions" \
    -H 'Content-Type: application/json' \
    -d "{\"model\":\"$GEMMA_OLLAMA_MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"请用一句话回答：你已经启动了吗？\"}],\"max_tokens\":64,\"temperature\":0.2}" \
    | python3 -m json.tool
}

usage() {
  cat <<EOF_USAGE
Usage: $0 {start|stop|restart|status|recreate|smoke}

Environment variables:
  GEMMA_MODEL_PATH=$GEMMA_MODEL_PATH
  GEMMA_OLLAMA_PORT=$GEMMA_OLLAMA_PORT
  GEMMA_OLLAMA_MODEL=$GEMMA_OLLAMA_MODEL
  GEMMA_OLLAMA_MODELS_DIR=$GEMMA_OLLAMA_MODELS_DIR
  GEMMA_MIN_BYTES=$GEMMA_MIN_BYTES

The service exposes OpenAI-compatible API at:
  $(base_url)/v1
EOF_USAGE
}

case "${1:-}" in
  start)
    start_server
    ;;
  stop)
    stop_server
    ;;
  restart)
    stop_server
    start_server
    ;;
  status)
    status_server
    ;;
  recreate)
    recreate_model
    ;;
  smoke)
    smoke_chat
    ;;
  *)
    usage
    exit 1
    ;;
esac
