CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS mcp_capability (
  capability_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  capability_code varchar(128) NOT NULL,
  capability_name varchar(128) NOT NULL,
  category varchar(64) NOT NULL,
  description text,
  risk_level varchar(8) NOT NULL DEFAULT 'L0',
  status varchar(32) NOT NULL DEFAULT 'draft',
  input_schema jsonb NOT NULL DEFAULT '{}'::jsonb,
  output_schema jsonb NOT NULL DEFAULT '{}'::jsonb,
  boundary text NOT NULL,
  last_health_status varchar(16) NOT NULL DEFAULT 'unknown',
  last_health_at timestamptz,
  enabled_at timestamptz,
  disabled_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL DEFAULT 'system',
  updated_by varchar(64),
  deleted_at timestamptz,
  CONSTRAINT ck_mcp_capability_category CHECK (category IN ('device','alarm','config','log','statistics')),
  CONSTRAINT ck_mcp_capability_risk CHECK (risk_level IN ('L0','L1','L2','L3','L4','L5')),
  CONSTRAINT ck_mcp_capability_status CHECK (status IN ('draft','enabled','disabled','unhealthy','retired')),
  CONSTRAINT ck_mcp_capability_health CHECK (last_health_status IN ('unknown','healthy','unhealthy'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mcp_capability_code ON mcp_capability (capability_code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_mcp_status ON mcp_capability (status, category, updated_at DESC) WHERE deleted_at IS NULL;

COMMENT ON TABLE mcp_capability IS 'MCP 能力表：记录工具能力边界、风险等级、输入输出 schema、启停和健康状态';
