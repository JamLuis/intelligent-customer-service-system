CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS mcp_capability_status_log (
  status_log_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  capability_id uuid NOT NULL,
  from_status varchar(32),
  to_status varchar(32) NOT NULL,
  reason text NOT NULL,
  impact_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  changed_by varchar(64) NOT NULL,
  changed_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_mcp_status_log_from CHECK (from_status IS NULL OR from_status IN ('draft','enabled','disabled','unhealthy','retired')),
  CONSTRAINT ck_mcp_status_log_to CHECK (to_status IN ('draft','enabled','disabled','unhealthy','retired'))
);

CREATE INDEX IF NOT EXISTS idx_mcp_status_log_capability ON mcp_capability_status_log (capability_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_mcp_status_log_changed_by ON mcp_capability_status_log (changed_by, changed_at DESC);

COMMENT ON TABLE mcp_capability_status_log IS 'MCP 能力状态日志表：记录启用、停用、异常、恢复、退役及影响范围';
