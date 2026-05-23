CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS graph_build_batch (
  batch_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_id uuid,
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  batch_type varchar(32) NOT NULL DEFAULT 'knowledge',
  status varchar(16) NOT NULL DEFAULT 'running',
  confidence_threshold numeric(4,2) NOT NULL DEFAULT 0.80,
  stats_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  failure_reason text,
  started_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  rolled_back_at timestamptz,
  created_by varchar(64) NOT NULL DEFAULT 'system',
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_graph_build_batch_type CHECK (batch_type IN ('knowledge','code','api','db','log','case')),
  CONSTRAINT ck_graph_build_batch_status CHECK (status IN ('running','success','failed','rolled_back')),
  CONSTRAINT ck_graph_build_batch_threshold CHECK (confidence_threshold >= 0 AND confidence_threshold <= 1)
);

CREATE INDEX IF NOT EXISTS idx_graph_batch_source ON graph_build_batch (source_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_graph_batch_project_status ON graph_build_batch (tenant_id, project_id, status, created_at DESC);

COMMENT ON TABLE graph_build_batch IS '图谱构建批次表：记录知识、代码、接口、数据库、日志和工单入图批次';
