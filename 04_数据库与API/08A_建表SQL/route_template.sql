CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS route_template (
  route_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  issue_category varchar(64) NOT NULL,
  route_name varchar(200) NOT NULL,
  question_pattern text NOT NULL,
  graph_strategy jsonb NOT NULL DEFAULT '{}'::jsonb,
  mcp_plan jsonb NOT NULL DEFAULT '[]'::jsonb,
  success_rate numeric(5,2) NOT NULL DEFAULT 0,
  status varchar(32) NOT NULL DEFAULT 'candidate',
  version_no int NOT NULL DEFAULT 1,
  failure_count int NOT NULL DEFAULT 0,
  last_used_at timestamptz,
  activated_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL DEFAULT 'system',
  updated_by varchar(64),
  deleted_at timestamptz,
  CONSTRAINT ck_route_template_status CHECK (status IN ('candidate','active','reviewing','disabled','archived')),
  CONSTRAINT ck_route_template_success CHECK (success_rate >= 0 AND success_rate <= 100),
  CONSTRAINT ck_route_template_version CHECK (version_no > 0),
  CONSTRAINT ck_route_template_failure CHECK (failure_count >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_route_scope_version ON route_template (tenant_id, project_id, issue_category, route_name, version_no) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_route_scope_status ON route_template (tenant_id, project_id, issue_category, status, updated_at DESC) WHERE deleted_at IS NULL;

COMMENT ON TABLE route_template IS '诊断路径模板表：按项目和问题类型固化可复用诊断路径';
