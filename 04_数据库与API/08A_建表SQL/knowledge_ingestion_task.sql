CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS knowledge_ingestion_task (
  task_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_id uuid NOT NULL,
  task_type varchar(32) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'pending',
  progress numeric(5,2) NOT NULL DEFAULT 0,
  result_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  error_message text,
  started_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_ingestion_task_type CHECK (task_type IN ('parse','ocr','extract','graph_build','retry')),
  CONSTRAINT ck_ingestion_task_status CHECK (status IN ('pending','running','success','failed','canceled')),
  CONSTRAINT ck_ingestion_task_progress CHECK (progress >= 0 AND progress <= 100)
);

CREATE INDEX IF NOT EXISTS idx_ingestion_source ON knowledge_ingestion_task (source_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ingestion_status ON knowledge_ingestion_task (status, created_at DESC);

COMMENT ON TABLE knowledge_ingestion_task IS '知识入图任务表：记录解析、OCR、抽取、图谱构建和重跑任务';
