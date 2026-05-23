CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS knowledge_source (
  source_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  source_type varchar(16) NOT NULL,
  file_name varchar(255),
  object_key varchar(512),
  source_hash varchar(128) NOT NULL,
  file_size_bytes bigint NOT NULL DEFAULT 0,
  raw_text text,
  sensitivity_level varchar(16) NOT NULL DEFAULT 'internal',
  status varchar(32) NOT NULL DEFAULT 'uploaded',
  parser_status varchar(32) NOT NULL DEFAULT 'pending',
  extract_status varchar(32) NOT NULL DEFAULT 'pending',
  graph_build_status varchar(32) NOT NULL DEFAULT 'pending',
  failure_reason text,
  published_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL,
  updated_by varchar(64),
  deleted_at timestamptz,
  CONSTRAINT ck_knowledge_source_type CHECK (source_type IN ('doc','docx','xls','xlsx','pdf','jpg','png','text')),
  CONSTRAINT ck_knowledge_source_status CHECK (status IN ('uploaded','parsing','extracted','graph_ready','published','failed')),
  CONSTRAINT ck_knowledge_source_sensitivity CHECK (sensitivity_level IN ('public','internal','restricted')),
  CONSTRAINT ck_knowledge_source_parser CHECK (parser_status IN ('pending','running','success','failed')),
  CONSTRAINT ck_knowledge_source_extract CHECK (extract_status IN ('pending','running','success','failed')),
  CONSTRAINT ck_knowledge_source_graph_build CHECK (graph_build_status IN ('pending','running','success','failed'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_source_hash ON knowledge_source (tenant_id, project_id, source_hash) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_source_project_status ON knowledge_source (tenant_id, project_id, status, updated_at DESC) WHERE deleted_at IS NULL;

COMMENT ON TABLE knowledge_source IS '知识源表：记录文件、图片、文本来源及解析、抽取、入图状态';
