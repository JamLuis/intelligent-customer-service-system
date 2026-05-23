CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS graph_asset (
  graph_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id uuid,
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  graph_type varchar(32) NOT NULL DEFAULT 'knowledge',
  graph_category_id varchar(64),
  graph_category_name varchar(128),
  graph_name varchar(200) NOT NULL,
  neo4j_graph_ref varchar(256),
  source_refs jsonb NOT NULL DEFAULT '[]'::jsonb,
  entity_types jsonb NOT NULL DEFAULT '[]'::jsonb,
  relation_types jsonb NOT NULL DEFAULT '[]'::jsonb,
  classification_path jsonb NOT NULL DEFAULT '[]'::jsonb,
  node_count int NOT NULL DEFAULT 0,
  edge_count int NOT NULL DEFAULT 0,
  confidence numeric(4,2) NOT NULL DEFAULT 0,
  status varchar(32) NOT NULL DEFAULT 'draft',
  active_revision_id uuid,
  published_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL DEFAULT 'system',
  updated_by varchar(64),
  deleted_at timestamptz,
  CONSTRAINT ck_graph_asset_type CHECK (graph_type IN ('knowledge','code','api','db','log','case','mixed')),
  CONSTRAINT ck_graph_asset_status CHECK (status IN ('draft','reviewing','published','deprecated','rolled_back')),
  CONSTRAINT ck_graph_asset_confidence CHECK (confidence >= 0 AND confidence <= 1),
  CONSTRAINT ck_graph_asset_counts CHECK (node_count >= 0 AND edge_count >= 0)
);

ALTER TABLE graph_asset ADD COLUMN IF NOT EXISTS graph_category_id varchar(64);
ALTER TABLE graph_asset ADD COLUMN IF NOT EXISTS graph_category_name varchar(128);
ALTER TABLE graph_asset ADD COLUMN IF NOT EXISTS entity_types jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE graph_asset ADD COLUMN IF NOT EXISTS relation_types jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE graph_asset ADD COLUMN IF NOT EXISTS classification_path jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE graph_asset ADD COLUMN IF NOT EXISTS active_revision_id uuid;

CREATE INDEX IF NOT EXISTS idx_graph_project_status ON graph_asset (tenant_id, project_id, status, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_graph_category_status ON graph_asset (tenant_id, project_id, graph_category_id, status, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_graph_batch ON graph_asset (batch_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE graph_asset IS '图谱资产表：记录 Neo4j 子图元数据、来源、版本状态和统计信息';
