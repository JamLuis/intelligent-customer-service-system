CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS audit_log (
  audit_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64),
  actor_id varchar(64) NOT NULL,
  actor_role varchar(32),
  action_type varchar(64) NOT NULL,
  object_type varchar(64) NOT NULL,
  object_id uuid,
  summary text NOT NULL,
  detail_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  ip_address varchar(64),
  user_agent text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_audit_actor_role CHECK (actor_role IS NULL OR actor_role IN ('R-CS','R-OPS','R-RD','R-ADMIN','R-AUDIT'))
);

CREATE INDEX IF NOT EXISTS idx_audit_object ON audit_log (object_type, object_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_log (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_project_time ON audit_log (tenant_id, project_id, created_at DESC);

COMMENT ON TABLE audit_log IS '审计日志表：记录敏感操作、导出、图谱发布、MCP 启停和路径固化等事件';
