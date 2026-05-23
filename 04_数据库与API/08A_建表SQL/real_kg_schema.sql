CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

-- Real KG schema extension. This file is intentionally additive and idempotent.
-- It upgrades the V0.1 mock graph metadata model into a real source -> block -> candidate -> review -> query foundation.

ALTER TABLE knowledge_source DROP CONSTRAINT IF EXISTS ck_knowledge_source_type;
ALTER TABLE knowledge_source
  ADD CONSTRAINT ck_knowledge_source_type CHECK (source_type IN ('doc','docx','xls','xlsx','pdf','jpg','jpeg','png','text','md','log','ini','json','csv'));

ALTER TABLE graph_build_batch DROP CONSTRAINT IF EXISTS ck_graph_build_batch_type;
ALTER TABLE graph_build_batch
  ADD CONSTRAINT ck_graph_build_batch_type CHECK (batch_type IN ('knowledge','code','api','db','log','case','mixed'));

CREATE TABLE IF NOT EXISTS graph_category (
  category_uid uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  category_id varchar(64) NOT NULL,
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL DEFAULT '*',
  category_name varchar(128) NOT NULL,
  domain varchar(64) NOT NULL,
  description text,
  entity_type_scope jsonb NOT NULL DEFAULT '[]'::jsonb,
  relation_type_scope jsonb NOT NULL DEFAULT '[]'::jsonb,
  status varchar(32) NOT NULL DEFAULT 'enabled',
  sort_order int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL DEFAULT 'system',
  updated_by varchar(64),
  CONSTRAINT uk_graph_category_scope UNIQUE (tenant_id, project_id, category_id),
  CONSTRAINT ck_graph_category_domain CHECK (domain IN ('asset','people','event','integration','code','db','ops','case','geo','mixed')),
  CONSTRAINT ck_graph_category_status CHECK (status IN ('enabled','disabled')),
  CONSTRAINT ck_graph_category_scope_json CHECK (jsonb_typeof(entity_type_scope) = 'array' AND jsonb_typeof(relation_type_scope) = 'array')
);

CREATE INDEX IF NOT EXISTS idx_graph_category_project_status ON graph_category (tenant_id, project_id, status, sort_order, category_id);
CREATE INDEX IF NOT EXISTS idx_graph_category_domain ON graph_category (tenant_id, project_id, domain, status, sort_order);

COMMENT ON TABLE graph_category IS '真实知识图谱分类表：定义地区-船舶、船舶-设备、设备-告警等动态图谱分类';
COMMENT ON COLUMN graph_category.project_id IS '项目级分类；全局默认分类使用 *';

CREATE TABLE IF NOT EXISTS graph_entity_type (
  entity_type varchar(64) PRIMARY KEY,
  label varchar(128) NOT NULL,
  description text,
  unique_key_schema jsonb NOT NULL DEFAULT '[]'::jsonb,
  property_schema jsonb NOT NULL DEFAULT '{}'::jsonb,
  extractor_rules jsonb NOT NULL DEFAULT '{}'::jsonb,
  status varchar(32) NOT NULL DEFAULT 'enabled',
  sort_order int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_graph_entity_type_status CHECK (status IN ('enabled','disabled')),
  CONSTRAINT ck_graph_entity_type_unique_schema CHECK (jsonb_typeof(unique_key_schema) = 'array'),
  CONSTRAINT ck_graph_entity_type_property_schema CHECK (jsonb_typeof(property_schema) = 'object'),
  CONSTRAINT ck_graph_entity_type_extractor_rules CHECK (jsonb_typeof(extractor_rules) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_graph_entity_type_status ON graph_entity_type (status, sort_order, entity_type);

COMMENT ON TABLE graph_entity_type IS '真实知识图谱实体类型表：定义 Device、Vessel、Protocol 等本体节点类型';

CREATE TABLE IF NOT EXISTS graph_relation_type (
  relation_type varchar(64) PRIMARY KEY,
  label varchar(128) NOT NULL,
  description text,
  from_entity_types jsonb NOT NULL DEFAULT '[]'::jsonb,
  to_entity_types jsonb NOT NULL DEFAULT '[]'::jsonb,
  property_schema jsonb NOT NULL DEFAULT '{}'::jsonb,
  inverse_relation_type varchar(64),
  status varchar(32) NOT NULL DEFAULT 'enabled',
  sort_order int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_graph_relation_type_status CHECK (status IN ('enabled','disabled')),
  CONSTRAINT ck_graph_relation_type_from_json CHECK (jsonb_typeof(from_entity_types) = 'array'),
  CONSTRAINT ck_graph_relation_type_to_json CHECK (jsonb_typeof(to_entity_types) = 'array'),
  CONSTRAINT ck_graph_relation_type_property_schema CHECK (jsonb_typeof(property_schema) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_graph_relation_type_status ON graph_relation_type (status, sort_order, relation_type);
CREATE INDEX IF NOT EXISTS idx_graph_relation_type_inverse ON graph_relation_type (inverse_relation_type) WHERE inverse_relation_type IS NOT NULL;

COMMENT ON TABLE graph_relation_type IS '真实知识图谱关系类型表：定义 INSTALLED_ON、BOUND_TO、USES_PROTOCOL 等边类型';

CREATE TABLE IF NOT EXISTS knowledge_block (
  block_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_id uuid NOT NULL,
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  graph_category_id varchar(64),
  block_type varchar(32) NOT NULL,
  section_path text,
  page_no int,
  row_no int,
  col_no int,
  raw_text text NOT NULL,
  normalized_text text,
  content_hash varchar(128) NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  embedding vector(1536),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_knowledge_block_type CHECK (block_type IN ('title','paragraph','table','table_row','image_ocr','code','log','json','csv','kv','sheet')),
  CONSTRAINT ck_knowledge_block_page CHECK (page_no IS NULL OR page_no > 0),
  CONSTRAINT ck_knowledge_block_row_col CHECK ((row_no IS NULL OR row_no >= 0) AND (col_no IS NULL OR col_no >= 0)),
  CONSTRAINT ck_knowledge_block_metadata CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_block_hash ON knowledge_block (tenant_id, project_id, source_id, content_hash);
CREATE INDEX IF NOT EXISTS idx_knowledge_block_source ON knowledge_block (source_id, created_at);
CREATE INDEX IF NOT EXISTS idx_knowledge_block_project_category ON knowledge_block (tenant_id, project_id, graph_category_id, block_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_knowledge_block_metadata_gin ON knowledge_block USING gin (metadata);
CREATE INDEX IF NOT EXISTS idx_knowledge_block_embedding_hnsw ON knowledge_block USING hnsw (embedding vector_cosine_ops) WHERE embedding IS NOT NULL;

COMMENT ON TABLE knowledge_block IS '知识解析块表：保存文本块、表格块、OCR 块、日志块及向量索引';
COMMENT ON COLUMN knowledge_block.embedding IS 'pgvector 语义召回向量，当前维度 1536；只作为证据召回，不作为最终关系判断';

CREATE TABLE IF NOT EXISTS graph_candidate_entity (
  candidate_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_id uuid NOT NULL,
  block_id uuid,
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  graph_category_id varchar(64) NOT NULL,
  entity_type varchar(64) NOT NULL,
  raw_name text NOT NULL,
  canonical_name text NOT NULL,
  unique_key jsonb NOT NULL DEFAULT '{}'::jsonb,
  properties jsonb NOT NULL DEFAULT '{}'::jsonb,
  evidence_block_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
  confidence numeric(5,4) NOT NULL DEFAULT 0,
  extractor varchar(64) NOT NULL DEFAULT 'rule',
  status varchar(32) NOT NULL DEFAULT 'candidate',
  review_reason text,
  merged_to_candidate_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_candidate_entity_confidence CHECK (confidence >= 0 AND confidence <= 1),
  CONSTRAINT ck_candidate_entity_extractor CHECK (extractor IN ('rule','llm','manual','ocr','cross_link')),
  CONSTRAINT ck_candidate_entity_status CHECK (status IN ('candidate','accepted','reviewing','rejected','conflict','merged')),
  CONSTRAINT ck_candidate_entity_json CHECK (jsonb_typeof(unique_key) = 'object' AND jsonb_typeof(properties) = 'object' AND jsonb_typeof(evidence_block_ids) = 'array')
);

CREATE INDEX IF NOT EXISTS idx_candidate_entity_source ON graph_candidate_entity (source_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_candidate_entity_project_category ON graph_candidate_entity (tenant_id, project_id, graph_category_id, status, confidence DESC);
CREATE INDEX IF NOT EXISTS idx_candidate_entity_type_name ON graph_candidate_entity (tenant_id, project_id, entity_type, canonical_name);
CREATE INDEX IF NOT EXISTS idx_candidate_entity_unique_key_gin ON graph_candidate_entity USING gin (unique_key);

COMMENT ON TABLE graph_candidate_entity IS '候选实体表：保存从结构化/非结构化内容中抽取出的待复核实体';

CREATE TABLE IF NOT EXISTS graph_candidate_relation (
  candidate_relation_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_id uuid NOT NULL,
  source_candidate_id uuid NOT NULL,
  target_candidate_id uuid NOT NULL,
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  graph_category_id varchar(64) NOT NULL,
  relation_type varchar(64) NOT NULL,
  properties jsonb NOT NULL DEFAULT '{}'::jsonb,
  evidence_block_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
  confidence numeric(5,4) NOT NULL DEFAULT 0,
  extractor varchar(64) NOT NULL DEFAULT 'rule',
  status varchar(32) NOT NULL DEFAULT 'candidate',
  review_reason text,
  merged_to_relation_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_candidate_relation_confidence CHECK (confidence >= 0 AND confidence <= 1),
  CONSTRAINT ck_candidate_relation_extractor CHECK (extractor IN ('rule','llm','manual','ocr','cross_link')),
  CONSTRAINT ck_candidate_relation_status CHECK (status IN ('candidate','accepted','reviewing','rejected','conflict','merged')),
  CONSTRAINT ck_candidate_relation_json CHECK (jsonb_typeof(properties) = 'object' AND jsonb_typeof(evidence_block_ids) = 'array')
);

CREATE INDEX IF NOT EXISTS idx_candidate_relation_source ON graph_candidate_relation (source_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_candidate_relation_project_category ON graph_candidate_relation (tenant_id, project_id, graph_category_id, status, confidence DESC);
CREATE INDEX IF NOT EXISTS idx_candidate_relation_type ON graph_candidate_relation (tenant_id, project_id, relation_type, status);
CREATE INDEX IF NOT EXISTS idx_candidate_relation_candidates ON graph_candidate_relation (source_candidate_id, target_candidate_id);

COMMENT ON TABLE graph_candidate_relation IS '候选关系表：保存从内容中抽取出的待校验实体关系';

CREATE TABLE IF NOT EXISTS graph_review_task (
  review_task_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  graph_category_id varchar(64),
  object_type varchar(32) NOT NULL,
  object_id uuid NOT NULL,
  reason_code varchar(64) NOT NULL,
  priority varchar(16) NOT NULL DEFAULT 'P1',
  assignee varchar(64),
  status varchar(32) NOT NULL DEFAULT 'pending',
  decision_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  review_comment text,
  reviewed_by varchar(64),
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_review_task_object CHECK (object_type IN ('entity','relation','batch')),
  CONSTRAINT ck_review_task_reason CHECK (reason_code IN ('low_confidence','conflict','duplicate','security','schema_invalid','missing_evidence')),
  CONSTRAINT ck_review_task_priority CHECK (priority IN ('P0','P1','P2')),
  CONSTRAINT ck_review_task_status CHECK (status IN ('pending','approved','rejected','merged','canceled')),
  CONSTRAINT ck_review_task_decision_json CHECK (jsonb_typeof(decision_payload) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_review_task_project_status ON graph_review_task (tenant_id, project_id, status, priority, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_task_object ON graph_review_task (object_type, object_id);
CREATE INDEX IF NOT EXISTS idx_review_task_category ON graph_review_task (tenant_id, project_id, graph_category_id, status);

COMMENT ON TABLE graph_review_task IS '图谱复核任务表：记录低置信度、冲突、重复、安全等人工复核事项';

CREATE TABLE IF NOT EXISTS graph_query_log (
  query_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id varchar(64) NOT NULL DEFAULT 'default',
  project_id varchar(64) NOT NULL,
  query_type varchar(32) NOT NULL,
  request_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  matched_graph_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
  matched_entity_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
  duration_ms int NOT NULL DEFAULT 0,
  result_count int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_graph_query_type CHECK (query_type IN ('admin','diagnosis','vector','graph','path','evidence')),
  CONSTRAINT ck_graph_query_duration CHECK (duration_ms >= 0),
  CONSTRAINT ck_graph_query_result_count CHECK (result_count >= 0),
  CONSTRAINT ck_graph_query_json CHECK (jsonb_typeof(request_payload) = 'object' AND jsonb_typeof(matched_graph_ids) = 'array' AND jsonb_typeof(matched_entity_ids) = 'array')
);

CREATE INDEX IF NOT EXISTS idx_graph_query_project_type_time ON graph_query_log (tenant_id, project_id, query_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_graph_query_duration ON graph_query_log (query_type, duration_ms DESC, created_at DESC);

COMMENT ON TABLE graph_query_log IS '图谱查询日志表：记录管理查询、诊断检索、向量召回和路径查询，用于调优召回率和性能';
