CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS mcp_call_log (
  call_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trace_id uuid NOT NULL,
  capability_id uuid,
  capability_code varchar(128) NOT NULL,
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  request_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  response_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  raw_response_ref varchar(256),
  status varchar(16) NOT NULL DEFAULT 'pending',
  degraded boolean NOT NULL DEFAULT false,
  error_code varchar(64),
  error_message text,
  started_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  duration_ms bigint,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_mcp_call_status CHECK (status IN ('pending','success','failed','timeout','skipped'))
);

CREATE INDEX IF NOT EXISTS idx_mcp_call_trace ON mcp_call_log (trace_id, started_at);
CREATE INDEX IF NOT EXISTS idx_mcp_call_capability ON mcp_call_log (capability_code, status, started_at DESC);

COMMENT ON TABLE mcp_call_log IS 'MCP 调用日志表：记录工具调用摘要、原始返回引用、状态和降级原因';
