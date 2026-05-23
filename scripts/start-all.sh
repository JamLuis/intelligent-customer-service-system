#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/.runtime"
LOG_DIR="$RUNTIME_DIR/logs"
PID_DIR="$RUNTIME_DIR/pids"
JAVA_VERSION="${JAVA_VERSION:-25}"
AI_PORT="${AI_PORT:-8100}"
MCP_PORT="${MCP_PORT:-3202}"
BACKEND_PORT="${BACKEND_PORT:-8088}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
MAVEN_SETTINGS="${MAVEN_SETTINGS:-$ROOT_DIR/../app-ship-alarm/settings.xml}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-$ROOT_DIR/.env}"

mkdir -p "$LOG_DIR" "$PID_DIR"

log() {
  printf '[smart-support] %s\n' "$1"
}

pid_file() {
  printf '%s/%s.pid' "$PID_DIR" "$1"
}

is_running() {
  local file="$1"
  [[ -f "$file" ]] && kill -0 "$(cat "$file")" >/dev/null 2>&1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "missing command: $1"
    exit 1
  fi
}

java_home() {
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    /usr/libexec/java_home -v "$JAVA_VERSION"
  else
    printf '%s' "${JAVA_HOME:-}"
  fi
}

start_process() {
  local name="$1"
  local command="$2"
  local file
  file="$(pid_file "$name")"

  if is_running "$file"; then
    log "$name already running, pid $(cat "$file")"
    return
  fi

  log "starting $name"
  (
    cd "$ROOT_DIR"
    nohup bash -lc "$command" > "$LOG_DIR/$name.log" 2>&1 &
    echo $! > "$file"
  )
  log "$name pid $(cat "$file"), log $LOG_DIR/$name.log"
}

stop_process() {
  local name="$1"
  local file
  file="$(pid_file "$name")"

  if is_running "$file"; then
    log "stopping $name, pid $(cat "$file")"
    kill "$(cat "$file")" >/dev/null 2>&1 || true
  fi

  rm -f "$file"
}

install_node_dependencies() {
  require_command npm
  if [[ ! -d "$ROOT_DIR/node_modules" ]]; then
    log "installing Node dependencies"
    (cd "$ROOT_DIR" && npm install)
  fi

  if [[ -f "$ROOT_DIR/frontend/package.json" && ! -d "$ROOT_DIR/frontend/node_modules" ]]; then
    log "installing frontend dependencies"
    (cd "$ROOT_DIR/frontend" && npm install)
  fi
}

install_python_dependencies() {
  require_command python3
  local venv_dir="$ROOT_DIR/ai-service-python/.venv"
  if [[ ! -x "$venv_dir/bin/python" ]]; then
    log "creating Python virtualenv"
    python3 -m venv "$venv_dir"
  fi

  if ! "$venv_dir/bin/python" -c 'import fastapi, uvicorn' >/dev/null 2>&1; then
    log "installing Python AI Service dependencies"
    "$venv_dir/bin/python" -m pip install -q --upgrade pip
    "$venv_dir/bin/python" -m pip install -q -e "$ROOT_DIR/ai-service-python"
  fi
}

start_infra() {
  require_command docker

  log "starting containerized infra: PostgreSQL + pgvector, Neo4j, Redis"
  if [[ -f "$COMPOSE_ENV_FILE" ]]; then
    (cd "$ROOT_DIR/infra" && docker compose --env-file "$COMPOSE_ENV_FILE" up -d)
  else
    (cd "$ROOT_DIR/infra" && docker compose up -d)
  fi
}

stop_infra() {
  if command -v docker >/dev/null 2>&1; then
    log "stopping infra"
    if [[ -f "$COMPOSE_ENV_FILE" ]]; then
      (cd "$ROOT_DIR/infra" && docker compose --env-file "$COMPOSE_ENV_FILE" down)
    else
      (cd "$ROOT_DIR/infra" && docker compose down)
    fi
  fi
}

start_all() {
  require_command mvn
  install_node_dependencies
  install_python_dependencies
  start_infra

  local jdk_home
  jdk_home="$(java_home)"
  if [[ -z "$jdk_home" ]]; then
    log "JDK $JAVA_VERSION not found; set JAVA_HOME or install JDK $JAVA_VERSION"
    exit 1
  fi

  local maven_settings_arg=""
  if [[ -f "$MAVEN_SETTINGS" ]]; then
    maven_settings_arg="-s '$MAVEN_SETTINGS'"
  fi

  start_process "mcp-server-node" "PORT=$MCP_PORT npm run dev:mcp-node"
  start_process "ai-service-python" "cd ai-service-python && .venv/bin/python -m uvicorn app.main:app --host 0.0.0.0 --port $AI_PORT"
  start_process "backend-java" "export JAVA_HOME='$jdk_home'; export PATH=\"\$JAVA_HOME/bin:\$PATH\"; BACKEND_PORT=$BACKEND_PORT AI_SERVICE_BASE_URL=http://localhost:$AI_PORT MCP_SERVER_BASE_URL=http://localhost:$MCP_PORT mvn $maven_settings_arg -f backend-java/pom.xml spring-boot:run"

  if [[ -f "$ROOT_DIR/frontend/package.json" ]]; then
    start_process "frontend" "cd frontend && npm run dev -- --host 0.0.0.0 --port $FRONTEND_PORT"
  else
    log "frontend/package.json not found, skip frontend"
  fi

  log "started services"
  log "frontend: http://localhost:$FRONTEND_PORT"
  log "backend: http://localhost:$BACKEND_PORT/api/health"
  log "ai-service: http://localhost:$AI_PORT/health"
  log "mcp-server: http://localhost:$MCP_PORT/health"
}

stop_all() {
  stop_process "frontend"
  stop_process "backend-java"
  stop_process "ai-service-python"
  stop_process "mcp-server-node"
  stop_infra
}

status_all() {
  if command -v docker >/dev/null 2>&1; then
    log "infra containers"
    if [[ -f "$COMPOSE_ENV_FILE" ]]; then
      (cd "$ROOT_DIR/infra" && docker compose --env-file "$COMPOSE_ENV_FILE" ps)
    else
      (cd "$ROOT_DIR/infra" && docker compose ps)
    fi
  fi

  for name in frontend backend-java ai-service-python mcp-server-node; do
    local file
    file="$(pid_file "$name")"
    if is_running "$file"; then
      log "$name running, pid $(cat "$file")"
    else
      log "$name stopped"
    fi
  done
}

case "${1:-start}" in
  start)
    start_all
    ;;
  stop)
    stop_all
    ;;
  restart)
    stop_all
    start_all
    ;;
  status)
    status_all
    ;;
  *)
    log "usage: scripts/start-all.sh [start|stop|restart|status]"
    exit 1
    ;;
esac