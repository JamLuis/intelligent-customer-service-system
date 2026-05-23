CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS support_session (
  session_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  external_project_id varchar(128),
  source_system varchar(32) NOT NULL DEFAULT 'ICSS',
  question_text text NOT NULL,
  issue_category varchar(64),
  entities jsonb NOT NULL DEFAULT '[]'::jsonb,
  missing_fields jsonb NOT NULL DEFAULT '[]'::jsonb,
  context_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  status varchar(32) NOT NULL DEFAULT 'draft',
  closed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL,
  updated_by varchar(64),
  deleted_at timestamptz,
  CONSTRAINT ck_support_session_status CHECK (status IN ('draft','waiting_context','ready','analyzed','closed'))
);

CREATE INDEX IF NOT EXISTS idx_session_project_status ON support_session (tenant_id, project_id, status, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_session_created_by ON support_session (created_by, created_at DESC) WHERE deleted_at IS NULL;

COMMENT ON TABLE support_session IS '问题会话表：记录用户提问、追问上下文和会话状态';
