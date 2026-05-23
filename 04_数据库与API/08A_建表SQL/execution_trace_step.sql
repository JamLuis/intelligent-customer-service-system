CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS execution_trace_step (
  step_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trace_id uuid NOT NULL,
  step_order int NOT NULL,
  step_type varchar(32) NOT NULL,
  step_name varchar(128) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'running',
  input_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  output_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  error_message text,
  started_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  duration_ms bigint,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_trace_step_status CHECK (status IN ('running','success','failed','skipped')),
  CONSTRAINT ck_trace_step_type CHECK (step_type IN ('classify','extract','graph','rag','mcp','reasoning','response'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_trace_step_order ON execution_trace_step (trace_id, step_order);
CREATE INDEX IF NOT EXISTS idx_trace_step_trace ON execution_trace_step (trace_id, step_order);

COMMENT ON TABLE execution_trace_step IS '执行步骤表：记录执行轨迹中的分类、抽取、图谱、RAG、MCP、推理和响应步骤';
