CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS execution_trace (
  trace_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  case_id uuid NOT NULL,
  route_id uuid,
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  issue_category varchar(64),
  status varchar(32) NOT NULL DEFAULT 'recording',
  graph_paths jsonb NOT NULL DEFAULT '[]'::jsonb,
  reasoning_summary jsonb NOT NULL DEFAULT '[]'::jsonb,
  final_answer text,
  confidence_score numeric(5,2) NOT NULL DEFAULT 0,
  degraded boolean NOT NULL DEFAULT false,
  failure_reason text,
  started_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  duration_ms bigint,
  archive_status varchar(16) NOT NULL DEFAULT 'online',
  archived_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL DEFAULT 'system',
  updated_by varchar(64),
  deleted_at timestamptz,
  CONSTRAINT ck_execution_trace_status CHECK (status IN ('recording','completed','partial','failed')),
  CONSTRAINT ck_execution_trace_confidence CHECK (confidence_score >= 0 AND confidence_score <= 100),
  CONSTRAINT ck_execution_trace_archive CHECK (archive_status IN ('online','archived'))
);

CREATE INDEX IF NOT EXISTS idx_trace_case ON execution_trace (case_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_trace_project_status ON execution_trace (tenant_id, project_id, status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_trace_archive ON execution_trace (archive_status, completed_at) WHERE deleted_at IS NULL;

COMMENT ON TABLE execution_trace IS '执行轨迹表：记录一次诊断的图谱路径、推理摘要、降级与归档状态';
