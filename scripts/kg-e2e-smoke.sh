#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="$ROOT_DIR/infra"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-$ROOT_DIR/.env}"
POSTGRES_DB="${POSTGRES_DB:-smart_support}"
POSTGRES_USER="${POSTGRES_USER:-smart_support}"
NEO4J_USER="${NEO4J_USER:-neo4j}"
NEO4J_PASSWORD="${NEO4J_PASSWORD:-smart_support}"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8088/api/v1}"
PROJECT_ID="${PROJECT_ID:-P001}"
RUN_ID="$(date +%Y%m%d%H%M%S)"
CATEGORY_ID="kg-smoke"

auth_header=(-H "Authorization: Bearer smoke-token" -H "X-Project-Id: $PROJECT_ID")

compose() {
  if [[ -f "$COMPOSE_ENV_FILE" ]]; then
    docker compose --env-file "$COMPOSE_ENV_FILE" -f "$INFRA_DIR/docker-compose.yml" "$@"
  else
    docker compose -f "$INFRA_DIR/docker-compose.yml" "$@"
  fi
}

json_get() {
  python3 - "$1" "$2" <<'PY'
import json
import sys
obj = json.loads(sys.argv[1])
for part in sys.argv[2].split('.'):
    if not part:
        continue
    if part.isdigit():
        obj = obj[int(part)]
    else:
        obj = obj[part]
print(obj)
PY
}

log() {
  printf '[kg-e2e] %s\n' "$1"
}

log "registering abstract taxonomy for $PROJECT_ID"
compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/init.sql >/dev/null
compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 >/dev/null <<SQL
INSERT INTO graph_entity_type (entity_type, label, description, unique_key_schema, property_schema, extractor_rules, status, sort_order)
VALUES
  ('EntityTypeA', 'Entity Type A', 'Smoke entity A', '["name"]'::jsonb, '{}'::jsonb, '{}'::jsonb, 'enabled', 100),
  ('EntityTypeB', 'Entity Type B', 'Smoke entity B', '["name"]'::jsonb, '{}'::jsonb, '{}'::jsonb, 'enabled', 101)
ON CONFLICT (entity_type) DO UPDATE
SET status = 'enabled', updated_at = now();

INSERT INTO graph_relation_type (relation_type, label, description, from_entity_types, to_entity_types, property_schema, status, sort_order)
VALUES ('RELATION_A', 'Relation A', 'Smoke relation', '["EntityTypeA"]'::jsonb, '["EntityTypeB"]'::jsonb, '{}'::jsonb, 'enabled', 100)
ON CONFLICT (relation_type) DO UPDATE
SET status = 'enabled', updated_at = now();

INSERT INTO graph_category (tenant_id, project_id, category_id, category_name, domain, description, entity_type_scope, relation_type_scope, status, sort_order)
VALUES ('default', '$PROJECT_ID', '$CATEGORY_ID', 'KG Smoke Category', 'smoke', 'Abstract taxonomy smoke test', '["EntityTypeA","EntityTypeB"]'::jsonb, '["RELATION_A"]'::jsonb, 'enabled', 100)
ON CONFLICT (tenant_id, project_id, category_id) DO UPDATE
SET status = 'enabled', updated_at = now();
SQL

log "submitting structured source"
SOURCE_TEXT="EntityTypeA: Alpha-$RUN_ID
EntityTypeB: Beta-$RUN_ID
RELATION_A: Alpha-$RUN_ID -> Beta-$RUN_ID"
REQUEST_BODY="$(python3 - <<PY
import json
print(json.dumps({
  'sourceType': 'text',
  'contentMode': 'structured_text',
  'structureFormat': 'rule_text',
  'graphCategoryId': '$CATEGORY_ID',
  'fileName': 'kg-smoke-$RUN_ID.txt',
  'rawText': '''$SOURCE_TEXT''',
  'sensitivityLevel': 'internal'
}, ensure_ascii=False))
PY
)"
CREATE_RESPONSE="$(curl -fsS -X POST "$BACKEND_URL/knowledge/sources" "${auth_header[@]}" -H "X-Idempotency-Key: kg-smoke-$RUN_ID" -H 'Content-Type: application/json' --data "$REQUEST_BODY")"
SOURCE_ID="$(json_get "$CREATE_RESPONSE" data.sourceId)"
log "sourceId=$SOURCE_ID"

TASKS_RESPONSE="$(curl -fsS "$BACKEND_URL/knowledge/sources/$SOURCE_ID/tasks" "${auth_header[@]}")"
BLOCKS_RESPONSE="$(curl -fsS "$BACKEND_URL/knowledge/sources/$SOURCE_ID/blocks?pageSize=50" "${auth_header[@]}")"
CANDIDATES_RESPONSE="$(curl -fsS "$BACKEND_URL/knowledge/sources/$SOURCE_ID/candidates" "${auth_header[@]}")"

BLOCK_COUNT="$(json_get "$BLOCKS_RESPONSE" data.total)"
ENTITY_COUNT="$(json_get "$CANDIDATES_RESPONSE" data.summary.entityCount)"
RELATION_COUNT="$(json_get "$CANDIDATES_RESPONSE" data.summary.relationCount)"
[[ "$BLOCK_COUNT" -ge 3 ]] || { echo "expected blocks >= 3, got $BLOCK_COUNT"; exit 1; }
[[ "$ENTITY_COUNT" -ge 2 ]] || { echo "expected entities >= 2, got $ENTITY_COUNT"; exit 1; }
[[ "$RELATION_COUNT" -ge 1 ]] || { echo "expected relations >= 1, got $RELATION_COUNT"; exit 1; }

GRAPHS_RESPONSE="$(curl -fsS "$BACKEND_URL/graphs/assets?graphCategoryId=$CATEGORY_ID&pageSize=5" "${auth_header[@]}")"
GRAPH_ID="$(json_get "$GRAPHS_RESPONSE" data.items.0.graphId)"
GRAPH_DETAIL="$(curl -fsS "$BACKEND_URL/graphs/assets/$GRAPH_ID" "${auth_header[@]}")"
NODE_COUNT="$(json_get "$GRAPH_DETAIL" data.nodeCount)"
EDGE_COUNT="$(json_get "$GRAPH_DETAIL" data.edgeCount)"
DETAIL_NODE_ID="$(json_get "$GRAPH_DETAIL" data.nodes.0.entityId)"
[[ "$NODE_COUNT" -ge 2 ]] || { echo "expected graph nodes >= 2, got $NODE_COUNT"; exit 1; }
[[ "$EDGE_COUNT" -ge 1 ]] || { echo "expected graph edges >= 1, got $EDGE_COUNT"; exit 1; }
[[ -n "$DETAIL_NODE_ID" ]] || { echo "graph detail nodes are empty"; exit 1; }

NEO4J_NODE_COUNT="$(compose exec -T neo4j cypher-shell -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" --format plain "MATCH (n:KnowledgeEntity {projectId:'$PROJECT_ID'}) WHERE n.entityName IN ['Alpha-$RUN_ID','Beta-$RUN_ID'] RETURN count(n) AS count;" | tail -1 | tr -d '\r')"
[[ "$NEO4J_NODE_COUNT" -ge 2 ]] || { echo "expected Neo4j nodes >= 2, got $NEO4J_NODE_COUNT"; exit 1; }

log "passed: blocks=$BLOCK_COUNT entities=$ENTITY_COUNT relations=$RELATION_COUNT graph=$GRAPH_ID nodes=$NODE_COUNT edges=$EDGE_COUNT"
