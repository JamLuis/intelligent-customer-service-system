CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS diagnostic_case (
  case_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id uuid NOT NULL,
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  issue_category varchar(64),
  status varchar(32) NOT NULL DEFAULT 'pending',
  root_cause text,
  confidence_score numeric(5,2) NOT NULL DEFAULT 0,
  evidence_summary jsonb NOT NULL DEFAULT '[]'::jsonb,
  recommended_actions jsonb NOT NULL DEFAULT '[]'::jsonb,
  risk_level varchar(8) NOT NULL DEFAULT 'L0',
  concluded_at timestamptz,
  archived_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL,
  updated_by varchar(64),
  deleted_at timestamptz,
  CONSTRAINT ck_diagnostic_case_status CHECK (status IN ('pending','diagnosing','evidence_ready','concluded','archived')),
  CONSTRAINT ck_diagnostic_case_confidence CHECK (confidence_score >= 0 AND confidence_score <= 100),
  CONSTRAINT ck_diagnostic_case_risk CHECK (risk_level IN ('L0','L1','L2','L3','L4','L5'))
);

CREATE INDEX IF NOT EXISTS idx_case_session ON diagnostic_case (session_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_case_project_status ON diagnostic_case (tenant_id, project_id, status, updated_at DESC) WHERE deleted_at IS NULL;

COMMENT ON TABLE diagnostic_case IS '诊断案例表：记录一次诊断的状态、结论、证据摘要和建议动作';
