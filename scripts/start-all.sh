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
MLX_ENABLED="${MLX_ENABLED:-1}"
MLX_HOST="${MLX_HOST:-127.0.0.1}"
MLX_PORT="${MLX_PORT:-18090}"
MLX_MODEL="${MLX_MODEL:-mlx-community/Qwen3.5-2B-4bit}"
MLX_MAX_TOKENS="${MLX_MAX_TOKENS:-512}"
MLX_PYTHON="${MLX_PYTHON:-}"
OLLAMA_ENABLED="${OLLAMA_ENABLED:-0}"
OLLAMA_PORT="${OLLAMA_PORT:-11434}"
OLLAMA_MODEL="${OLLAMA_MODEL:-qwen2.5:1.5b}"
NODE_MIN_MAJOR="${NODE_MIN_MAJOR:-20}"
STARTUP_TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-90}"
MAVEN_SETTINGS="${MAVEN_SETTINGS:-}"
MAVEN_SETTINGS_ALLOW_FALLBACK="${MAVEN_SETTINGS_ALLOW_FALLBACK:-1}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-$ROOT_DIR/.env}"
APPLY_NEO4J_SCHEMA="${APPLY_NEO4J_SCHEMA:-0}"
BACKEND_JAR="$ROOT_DIR/backend-java/target/backend-java-0.1.0-SNAPSHOT.jar"

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

node_bin_dir() {
  local candidate

  if [[ -n "${NODE_BIN_DIR:-}" && -x "$NODE_BIN_DIR/node" ]]; then
    printf '%s' "$NODE_BIN_DIR"
    return
  fi

  for candidate in \
    "$HOME/.nvm/versions/node/v22.21.1/bin/node" \
    "$HOME/.nvm/versions/node/v20.20.1/bin/node" \
    "$HOME"/.nvm/versions/node/v*/bin/node \
    /opt/homebrew/bin/node \
    /usr/local/bin/node \
    "$(command -v node 2>/dev/null || true)"; do
    [[ -x "$candidate" ]] || continue
    if "$candidate" -e "const major = Number(process.versions.node.split('.')[0]); process.exit(major >= Number(process.env.NODE_MIN_MAJOR || '$NODE_MIN_MAJOR') ? 0 : 1);" >/dev/null 2>&1; then
      dirname "$candidate"
      return
    fi
  done

  log "Node.js $NODE_MIN_MAJOR+ not found; install Node 20.19+ or 22.12+"
  exit 1
}

with_node_path() {
  local node_dir="$1"
  shift
  printf 'export PATH=%q:"$PATH"; ' "$node_dir"
  printf '%s' "$*"
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

stop_port_process() {
  local name="$1"
  local port="$2"
  local pids
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    log "stopping $name listeners on port $port: $pids"
    kill $pids >/dev/null 2>&1 || true
  fi
}

http_ready() {
  local url="$1"
  curl -fsS --max-time 2 "$url" >/dev/null 2>&1
}

wait_http() {
  local name="$1"
  local url="$2"
  local deadline
  deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))

  log "waiting for $name: $url"
  until http_ready "$url"; do
    if (( SECONDS >= deadline )); then
      log "$name did not become ready within ${STARTUP_TIMEOUT_SECONDS}s"
      log "check log: $LOG_DIR/$name.log"
      return 1
    fi
    sleep 1
  done
  log "$name ready: $url"
}

ollama_base_url() {
  printf 'http://127.0.0.1:%s' "$OLLAMA_PORT"
}

ollama_ready() {
  http_ready "$(ollama_base_url)/api/tags"
}

ollama_has_model() {
  curl -fsS --max-time 5 "$(ollama_base_url)/api/tags" 2>/dev/null | grep -q "\"name\":\"$OLLAMA_MODEL\""
}

start_ollama() {
  if [[ "$OLLAMA_ENABLED" != "1" ]]; then
    log "ollama disabled by OLLAMA_ENABLED=$OLLAMA_ENABLED"
    return
  fi
  require_command ollama

  if ollama_ready; then
    log "ollama already running: $(ollama_base_url)"
  else
    start_process "ollama" "OLLAMA_HOST=127.0.0.1:$OLLAMA_PORT exec ollama serve"
    wait_http "ollama" "$(ollama_base_url)/api/tags"
  fi

  if ollama_has_model; then
    log "ollama model ready: $OLLAMA_MODEL"
  else
    log "pulling ollama model: $OLLAMA_MODEL"
    OLLAMA_HOST="$(ollama_base_url)" ollama pull "$OLLAMA_MODEL"
  fi
}

ollama_status() {
  if [[ "$OLLAMA_ENABLED" != "1" ]]; then
    log "ollama disabled"
    return
  fi
  if ollama_ready; then
    if ollama_has_model; then
      log "ollama running, http ready, model $OLLAMA_MODEL ready"
    else
      log "ollama running, http ready, model $OLLAMA_MODEL missing"
    fi
  else
    log "ollama stopped"
  fi
}

mlx_python() {
  local candidate

  if [[ -n "$MLX_PYTHON" && -x "$MLX_PYTHON" ]]; then
    printf '%s' "$MLX_PYTHON"
    return
  fi

  for candidate in \
    "$ROOT_DIR/../.venv/bin/python" \
    "$ROOT_DIR/.venv/bin/python" \
    "$ROOT_DIR/ai-service-python/.venv/bin/python" \
    "$(command -v python3 2>/dev/null || true)"; do
    [[ -x "$candidate" ]] || continue
    printf '%s' "$candidate"
    return
  done

  log "Python not found for MLX; set MLX_PYTHON"
  exit 1
}

mlx_base_url() {
  printf 'http://%s:%s/v1' "$MLX_HOST" "$MLX_PORT"
}

mlx_ready() {
  http_ready "$(mlx_base_url)/models"
}

ensure_mlx_dependencies() {
  if [[ "$MLX_ENABLED" != "1" ]]; then
    return
  fi

  local py
  py="$(mlx_python)"
  if ! "$py" -c 'import mlx_lm, mlx_embeddings' >/dev/null 2>&1; then
    log "installing MLX dependencies into $py"
    "$py" -m pip install -q -U mlx-lm mlx-embeddings
  fi
}

start_mlx() {
  if [[ "$MLX_ENABLED" != "1" ]]; then
    log "mlx disabled by MLX_ENABLED=$MLX_ENABLED"
    return
  fi

  ensure_mlx_dependencies

  if mlx_ready; then
    log "mlx already running: $(mlx_base_url)"
  else
    local py
    py="$(mlx_python)"
    start_process "mlx" "exec '$py' -m mlx_lm server --model '$MLX_MODEL' --host '$MLX_HOST' --port '$MLX_PORT' --max-tokens '$MLX_MAX_TOKENS' --temp 0 --chat-template-args '{\"enable_thinking\":false}' --log-level INFO"
    wait_http "mlx" "$(mlx_base_url)/models"
  fi

  log "mlx model ready: $MLX_MODEL"
}

mlx_status() {
  if [[ "$MLX_ENABLED" != "1" ]]; then
    log "mlx disabled"
    return
  fi
  if mlx_ready; then
    log "mlx running, http ready, model $MLX_MODEL"
  else
    log "mlx stopped"
  fi
}

service_status() {
  local name="$1"
  local url="$2"
  local file
  file="$(pid_file "$name")"

  if is_running "$file"; then
    if http_ready "$url"; then
      log "$name running, pid $(cat "$file"), http ready"
    else
      log "$name running, pid $(cat "$file"), http not ready"
    fi
  else
    log "$name stopped"
  fi
}

install_node_dependencies() {
  require_command npm
  local node_dir
  node_dir="$(node_bin_dir)"
  export PATH="$node_dir:$PATH"
  log "using Node $(node -v) from $node_dir"

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

  if ! "$venv_dir/bin/python" -c 'import fastapi, uvicorn, httpx, pydantic_settings, mlx_embeddings' >/dev/null 2>&1; then
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

compose_exec() {
  if [[ -f "$COMPOSE_ENV_FILE" ]]; then
    (cd "$ROOT_DIR/infra" && docker compose --env-file "$COMPOSE_ENV_FILE" exec -T "$@")
  else
    (cd "$ROOT_DIR/infra" && docker compose exec -T "$@")
  fi
}

apply_postgres_schema() {
  local postgres_user="${POSTGRES_USER:-smart_support}"
  local postgres_db="${POSTGRES_DB:-smart_support}"
  log "applying PostgreSQL schema and protected taxonomy"
  compose_exec postgres sh -lc "until pg_isready -U '$postgres_user' -d '$postgres_db' >/dev/null 2>&1; do sleep 1; done; psql -U '$postgres_user' -d '$postgres_db' -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/init.sql" > "$LOG_DIR/postgres-schema.log" 2>&1
}

apply_neo4j_schema() {
  local neo4j_user="${NEO4J_USER:-neo4j}"
  local neo4j_password="${NEO4J_PASSWORD:-smart_support}"
  log "applying Neo4j indexes and constraints"
  compose_exec neo4j sh -lc "until cypher-shell -u '$neo4j_user' -p '$neo4j_password' 'RETURN 1' >/dev/null 2>&1; do sleep 1; done; cypher-shell -u '$neo4j_user' -p '$neo4j_password' --format plain -f /imports/init.cypher" > "$LOG_DIR/neo4j-schema.log" 2>&1
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
  require_command curl
  install_node_dependencies
  install_python_dependencies
  ensure_mlx_dependencies
  start_infra
  apply_postgres_schema
  if [[ "$APPLY_NEO4J_SCHEMA" == "1" ]]; then
    apply_neo4j_schema
  fi
  start_mlx
  start_ollama

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

  local node_dir
  node_dir="$(node_bin_dir)"

  log "packaging Java backend"
  if ! (cd "$ROOT_DIR" && export JAVA_HOME="$jdk_home" && export PATH="$JAVA_HOME/bin:$PATH" && bash -lc "mvn $maven_settings_arg -q -f backend-java/pom.xml -DskipTests package"); then
    if [[ -n "$maven_settings_arg" && "$MAVEN_SETTINGS_ALLOW_FALLBACK" == "1" ]]; then
      log "Maven settings failed, retrying with default Maven repositories"
      (cd "$ROOT_DIR" && export JAVA_HOME="$jdk_home" && export PATH="$JAVA_HOME/bin:$PATH" && mvn -q -f backend-java/pom.xml -DskipTests package)
    else
      return 1
    fi
  fi

  start_process "mcp-server-node" "$(with_node_path "$node_dir" "PORT=$MCP_PORT exec npm run dev:mcp-node")"
  start_process "ai-service-python" "exec ai-service-python/.venv/bin/python -m uvicorn app.main:app --app-dir ai-service-python --host 0.0.0.0 --port $AI_PORT"
  start_process "backend-java" "export JAVA_HOME='$jdk_home'; export PATH="\$JAVA_HOME/bin:\$PATH"; BACKEND_PORT=$BACKEND_PORT AI_SERVICE_BASE_URL=http://localhost:$AI_PORT MCP_SERVER_BASE_URL=http://localhost:$MCP_PORT exec java -jar '$BACKEND_JAR'"

  if [[ -f "$ROOT_DIR/frontend/package.json" ]]; then
    start_process "frontend" "$(with_node_path "$node_dir" "exec npm --prefix frontend run dev -- --host 0.0.0.0 --port $FRONTEND_PORT")"
  else
    log "frontend/package.json not found, skip frontend"
  fi

  wait_http "mcp-server-node" "http://127.0.0.1:$MCP_PORT/health"
  wait_http "ai-service-python" "http://127.0.0.1:$AI_PORT/health"
  wait_http "backend-java" "http://127.0.0.1:$BACKEND_PORT/api/health"
  if [[ -f "$ROOT_DIR/frontend/package.json" ]]; then
    wait_http "frontend" "http://127.0.0.1:$FRONTEND_PORT/"
  fi

  log "started services"
  log "frontend: http://localhost:$FRONTEND_PORT"
  log "backend: http://localhost:$BACKEND_PORT/api/health"
  log "ai-service: http://localhost:$AI_PORT/health"
  log "mcp-server: http://localhost:$MCP_PORT/health"
  if [[ "$OLLAMA_ENABLED" == "1" ]]; then
    log "ollama: $(ollama_base_url) ($OLLAMA_MODEL)"
  fi
  if [[ "$MLX_ENABLED" == "1" ]]; then
    log "mlx: $(mlx_base_url) ($MLX_MODEL)"
  fi
}

stop_all() {
  stop_process "frontend"
  stop_process "backend-java"
  stop_process "ai-service-python"
  stop_process "mcp-server-node"
  stop_process "mlx"
  stop_process "ollama"
  stop_port_process "frontend" "$FRONTEND_PORT"
  stop_port_process "backend-java" "$BACKEND_PORT"
  stop_port_process "ai-service-python" "$AI_PORT"
  stop_port_process "mcp-server-node" "$MCP_PORT"
  stop_port_process "mlx" "$MLX_PORT"
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

  service_status "frontend" "http://127.0.0.1:$FRONTEND_PORT/"
  service_status "backend-java" "http://127.0.0.1:$BACKEND_PORT/api/health"
  service_status "ai-service-python" "http://127.0.0.1:$AI_PORT/health"
  service_status "mcp-server-node" "http://127.0.0.1:$MCP_PORT/health"
  mlx_status
  ollama_status
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