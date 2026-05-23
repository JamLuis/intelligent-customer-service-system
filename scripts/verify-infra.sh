#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="$ROOT_DIR/infra"
COMPOSE_FILE="$INFRA_DIR/docker-compose.yml"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-$ROOT_DIR/.env}"
POSTGRES_DB="${POSTGRES_DB:-smart_support}"
POSTGRES_USER="${POSTGRES_USER:-smart_support}"
NEO4J_USER="${NEO4J_USER:-neo4j}"
NEO4J_PASSWORD="${NEO4J_PASSWORD:-smart_support}"

log() {
  printf '[verify-infra] %s\n' "$1"
}

compose() {
  if [[ -f "$COMPOSE_ENV_FILE" ]]; then
    docker compose --env-file "$COMPOSE_ENV_FILE" -f "$COMPOSE_FILE" "$@"
  else
    docker compose -f "$COMPOSE_FILE" "$@"
  fi
}

cleanup() {
  log "stopping containerized infra"
  compose down >/dev/null 2>&1 || true
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "missing command: $1"
    exit 1
  fi
}

require_command docker
trap cleanup EXIT

log "validating docker compose config"
compose config >/dev/null

log "starting PostgreSQL + pgvector, Neo4j, Redis"
compose up -d --wait --wait-timeout 240

log "applying PostgreSQL schema idempotently"
compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/init.sql >/dev/null

log "checking PostgreSQL extensions and P0 tables"
compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -Atc "SELECT extname FROM pg_extension WHERE extname IN ('pgcrypto','vector') ORDER BY extname;"
compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('support_session','diagnostic_case','execution_trace','execution_trace_step','mcp_call_log','knowledge_source','knowledge_ingestion_task','graph_build_batch','graph_asset','graph_revision','mcp_capability','mcp_capability_status_log','route_template','route_evaluation','audit_log');"

log "checking real KG schema, taxonomy seed, and pgvector index"
compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('graph_category','graph_entity_type','graph_relation_type','knowledge_block','graph_candidate_entity','graph_candidate_relation','graph_review_task','graph_query_log');"
compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM graph_category WHERE status='enabled';"
compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM graph_entity_type WHERE status='enabled';"
compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM graph_relation_type WHERE status='enabled';"
compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -Atc "SELECT indexname FROM pg_indexes WHERE schemaname='public' AND tablename='knowledge_block' AND indexname='idx_knowledge_block_embedding_hnsw';"

log "checking Neo4j"
compose exec -T neo4j cypher-shell -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" "RETURN 1 AS ok;"

log "checking Redis"
compose exec -T redis redis-cli ping

log "infra verification passed"
