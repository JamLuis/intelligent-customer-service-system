CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS graph_revision (
  revision_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  graph_id uuid NOT NULL,
  revision_no int NOT NULL,
  status varchar(32) NOT NULL DEFAULT 'draft',
  change_summary text,
  diff_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  source_refs jsonb NOT NULL DEFAULT '[]'::jsonb,
  published_at timestamptz,
  published_by varchar(64),
  rolled_back_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL DEFAULT 'system',
  CONSTRAINT ck_graph_revision_status CHECK (status IN ('draft','reviewing','published','deprecated','rolled_back')),
  CONSTRAINT ck_graph_revision_no CHECK (revision_no > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_graph_revision_no ON graph_revision (graph_id, revision_no);
CREATE INDEX IF NOT EXISTS idx_graph_revision_status ON graph_revision (graph_id, status, created_at DESC);

COMMENT ON TABLE graph_revision IS '图谱版本表：记录图谱资产发布、废弃、回滚和差异摘要';
