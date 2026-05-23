CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS route_evaluation (
  evaluation_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id uuid,
  case_id uuid NOT NULL,
  trace_id uuid NOT NULL,
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  rating varchar(16) NOT NULL,
  failure_reason text,
  comment text,
  rebuild_required boolean NOT NULL DEFAULT false,
  reviewed_by varchar(64),
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL,
  CONSTRAINT ck_route_evaluation_rating CHECK (rating IN ('valid','invalid','partial'))
);

CREATE INDEX IF NOT EXISTS idx_route_eval_route ON route_evaluation (route_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_route_eval_case ON route_evaluation (case_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_route_eval_project ON route_evaluation (tenant_id, project_id, created_at DESC);

COMMENT ON TABLE route_evaluation IS '路径评估表：记录答案有效性反馈、失败原因和是否需要重建';
