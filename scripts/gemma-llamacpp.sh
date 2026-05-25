#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/.runtime"
LOG_DIR="$RUNTIME_DIR/logs"
PID_DIR="$RUNTIME_DIR/pids"
GEMMA_MODEL_PATH="${GEMMA_MODEL_PATH:-/Users/lucas/Work/Personal/llama.cpp-kleidiai/models/gemma-4-E4B-it-Q4_0.gguf}"
GEMMA_LLAMA_PORT="${GEMMA_LLAMA_PORT:-11435}"
GEMMA_LLAMA_MODEL="${GEMMA_LLAMA_MODEL:-gemma-4-E4B-it-Q4_0}"
GEMMA_LLAMA_HOST="${GEMMA_LLAMA_HOST:-127.0.0.1}"
GEMMA_LLAMA_CTX_SIZE="${GEMMA_LLAMA_CTX_SIZE:-8192}"
GEMMA_MIN_BYTES="${GEMMA_MIN_BYTES:-536870912}"
STARTUP_TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-90}"
PID_FILE="$PID_DIR/llamacpp-gemma.pid"
LOG_FILE="$LOG_DIR/llamacpp-gemma.log"

mkdir -p "$LOG_DIR" "$PID_DIR"

log() {
  printf '[gemma-llamacpp] %s\n' "$1"
}

base_url() {
  printf 'http://%s:%s' "$GEMMA_LLAMA_HOST" "$GEMMA_LLAMA_PORT"
}

llama_server_bin() {
  if [[ -n "${LLAMA_SERVER_BIN:-}" && -x "$LLAMA_SERVER_BIN" ]]; then
    printf '%s' "$LLAMA_SERVER_BIN"
    return
  fi
  for candidate in llama-server llama-cpp-server; do
    if command -v "$candidate" >/dev/null 2>&1; then
      command -v "$candidate"
      return
    fi
  done
  return 1
}

is_pid_running() {
  [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" >/dev/null 2>&1
}

models_json() {
  curl -fsS --max-time 2 "$(base_url)/v1/models" 2>/dev/null || true
}

http_ready() {
  models_json | grep -q "\"id\"[[:space:]]*:[[:space:]]*\"$GEMMA_LLAMA_MODEL\""
}

port_has_openai_api() {
  [[ -n "$(models_json)" ]]
}

check_model_file() {
  if [[ ! -f "$GEMMA_MODEL_PATH" ]]; then
    log "GGUF model file not found: $GEMMA_MODEL_PATH"
    exit 1
  fi

  local byte_size
  byte_size="$(wc -c < "$GEMMA_MODEL_PATH" | tr -d ' ')"
  if (( byte_size < GEMMA_MIN_BYTES )); then
    log "GGUF model file looks incomplete: $GEMMA_MODEL_PATH"
    log "file size is ${byte_size} bytes, expected at least ${GEMMA_MIN_BYTES} bytes for this Gemma GGUF preset"
    log "please re-download the GGUF model file, then run: $0 start"
    exit 1
  fi
}

wait_http() {
  local deadline
  deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
  log "waiting for llama.cpp server: $(base_url)"
  until http_ready; do
    if (( SECONDS >= deadline )); then
      log "llama.cpp server did not become ready within ${STARTUP_TIMEOUT_SECONDS}s"
      log "check log: $LOG_FILE"
      return 1
    fi
    sleep 1
  done
  log "llama.cpp server ready: $(base_url)/v1"
}

stop_port_process() {
  local pids
  pids="$(lsof -tiTCP:"$GEMMA_LLAMA_PORT" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    log "stopping listeners on port $GEMMA_LLAMA_PORT: $pids"
    kill $pids >/dev/null 2>&1 || true
  fi
}

start_server() {
  check_model_file
  local server_bin
  if ! server_bin="$(llama_server_bin)"; then
    log "llama.cpp server binary not found"
    log "install it with: brew install llama.cpp"
    log "or set LLAMA_SERVER_BIN=/path/to/llama-server"
    exit 1
  fi

  if http_ready; then
    log "llama.cpp server already running: $(base_url)/v1"
    log "model name: $GEMMA_LLAMA_MODEL"
    return
  fi

  if port_has_openai_api; then
    log "port $GEMMA_LLAMA_PORT already exposes an OpenAI-compatible API, but model '$GEMMA_LLAMA_MODEL' is not available"
    log "stop the existing service first, for example: ./scripts/gemma-ollama.sh stop"
    log "or use another port: GEMMA_LLAMA_PORT=11436 $0 start"
    exit 1
  fi

  if is_pid_running; then
    log "stale process exists but HTTP is not ready, pid $(cat "$PID_FILE")"
    stop_server
  fi

  log "starting llama.cpp Gemma server on $(base_url)"
  (
    cd "$ROOT_DIR"
    nohup "$server_bin" \
      -m "$GEMMA_MODEL_PATH" \
      --host "$GEMMA_LLAMA_HOST" \
      --port "$GEMMA_LLAMA_PORT" \
      --ctx-size "$GEMMA_LLAMA_CTX_SIZE" \
      --alias "$GEMMA_LLAMA_MODEL" \
      > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
  )
  log "pid $(cat "$PID_FILE"), log $LOG_FILE"
  wait_http
  log "OpenAI-compatible base URL: $(base_url)/v1"
  log "model name: $GEMMA_LLAMA_MODEL"
}

stop_server() {
  if is_pid_running; then
    log "stopping llama.cpp Gemma, pid $(cat "$PID_FILE")"
    kill "$(cat "$PID_FILE")" >/dev/null 2>&1 || true
  fi
  rm -f "$PID_FILE"
  stop_port_process
}

status_server() {
  if http_ready; then
    log "running, OpenAI-compatible API ready: $(base_url)/v1"
    models_json | python3 -m json.tool || true
  elif port_has_openai_api; then
    log "OpenAI-compatible API is listening on $(base_url)/v1, but model '$GEMMA_LLAMA_MODEL' is not available"
    models_json | python3 -m json.tool || true
  else
    log "stopped"
  fi
}

smoke_chat() {
  start_server
  curl -fsS "$(base_url)/v1/chat/completions" \
    -H 'Content-Type: application/json' \
    -d "{\"model\":\"$GEMMA_LLAMA_MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"请用一句话回答：你已经启动了吗？\"}],\"max_tokens\":64,\"temperature\":0.2}" \
    | python3 -m json.tool
}

usage() {
  cat <<EOF_USAGE
Usage: $0 {start|stop|restart|status|smoke}

Environment variables:
  GEMMA_MODEL_PATH=$GEMMA_MODEL_PATH
  GEMMA_LLAMA_HOST=$GEMMA_LLAMA_HOST
  GEMMA_LLAMA_PORT=$GEMMA_LLAMA_PORT
  GEMMA_LLAMA_MODEL=$GEMMA_LLAMA_MODEL
  GEMMA_LLAMA_CTX_SIZE=$GEMMA_LLAMA_CTX_SIZE
  GEMMA_MIN_BYTES=$GEMMA_MIN_BYTES
  LLAMA_SERVER_BIN=${LLAMA_SERVER_BIN:-}

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
  smoke)
    smoke_chat
    ;;
  *)
    usage
    exit 1
    ;;
esac
