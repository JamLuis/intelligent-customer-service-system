CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

-- Real KG schema extension. This file is intentionally additive and idempotent.
-- It upgrades the V0.1 mock graph metadata model into a real source -> block -> candidate -> review -> query foundation.

ALTER TABLE knowledge_source DROP CONSTRAINT IF EXISTS ck_knowledge_source_type;
ALTER TABLE knowledge_source
  ADD CONSTRAINT ck_knowledge_source_type CHECK (source_type IN ('doc','docx','xls','xlsx','pdf','jpg','jpeg','png','text','md','log','ini','json','csv'));

-- batch_type 不再做枚举约束；由元数据/应用层校验。
ALTER TABLE graph_build_batch DROP CONSTRAINT IF EXISTS ck_graph_build_batch_type;
ALTER TABLE graph_build_batch
  ADD CONSTRAINT ck_graph_build_batch_type CHECK (batch_type ~ '^[A-Za-z][A-Za-z0-9_\-]{0,63}$');

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
  CONSTRAINT ck_graph_category_domain CHECK (domain ~ '^[A-Za-z][A-Za-z0-9_\-]{0,63}$'),
  CONSTRAINT ck_graph_category_status CHECK (status IN ('enabled','disabled')),
  CONSTRAINT ck_graph_category_scope_json CHECK (jsonb_typeof(entity_type_scope) = 'array' AND jsonb_typeof(relation_type_scope) = 'array')
);

CREATE INDEX IF NOT EXISTS idx_graph_category_project_status ON graph_category (tenant_id, project_id, status, sort_order, category_id);
CREATE INDEX IF NOT EXISTS idx_graph_category_domain ON graph_category (tenant_id, project_id, domain, status, sort_order);

-- 旧 volume 可能仍保留 V0.1/V0.2 的领域枚举约束；这里幂等替换为通用字符校验。
ALTER TABLE graph_category DROP CONSTRAINT IF EXISTS ck_graph_category_domain;
ALTER TABLE graph_category
  ADD CONSTRAINT ck_graph_category_domain CHECK (domain ~ '^[A-Za-z][A-Za-z0-9_\-]{0,63}$');

COMMENT ON TABLE graph_category IS '知识图谱分类表（通用平台）：由租户/项目通过管理 API 自定义注册；系统不内置任何业务分类。';
COMMENT ON COLUMN graph_category.project_id IS '项目级分类；通用模板可使用 *，但默认不再下发任何通用模板。';
COMMENT ON COLUMN graph_category.domain IS '业务域标签（自由文本，仅作字符校验）；由租户自定义，例如 asset/event/integration 等，不再强制枚举。';

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

COMMENT ON TABLE graph_entity_type IS '知识图谱实体类型表（通用平台）：由租户/项目通过管理 API 自行注册；系统仅内置极少量平台级保护类型（如 SourceBlock/Document/Section），不内置任何业务领域类型。';

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

COMMENT ON TABLE graph_relation_type IS '知识图谱关系类型表（通用平台）：由租户/项目通过管理 API 自行注册；系统仅内置平台级保护关系（如 HAS_EVIDENCE），不内置任何业务领域关系。';

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
  embedding vector(1024),
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
COMMENT ON COLUMN knowledge_block.embedding IS 'pgvector 语义召回向量，当前维度 1024（BGE-M3 MLX）；只作为证据召回，不作为最终关系判断';

DROP INDEX IF EXISTS idx_knowledge_block_embedding_hnsw;
ALTER TABLE knowledge_block
  ALTER COLUMN embedding TYPE vector(1024)
  USING CASE
    WHEN embedding IS NULL THEN NULL
    WHEN vector_dims(embedding) = 1024 THEN embedding::vector(1024)
    ELSE NULL
  END;
CREATE INDEX IF NOT EXISTS idx_knowledge_block_embedding_hnsw ON knowledge_block USING hnsw (embedding vector_cosine_ops) WHERE embedding IS NOT NULL;

-- ============================================================================
-- V0.3.1 知识块加固：父链 + embedding 版本三件套 + 全文检索 tsvector
-- 对应 07F §9.4 / §13.4.2，支持 Hybrid Retrieval 与 embedding 版本严格过滤
-- ============================================================================
ALTER TABLE knowledge_block
  ADD COLUMN IF NOT EXISTS parent_block_id uuid REFERENCES knowledge_block(block_id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS embedding_model varchar(64),
  ADD COLUMN IF NOT EXISTS embedding_version varchar(32),
  ADD COLUMN IF NOT EXISTS embedding_dim int;

ALTER TABLE knowledge_block
  ADD COLUMN IF NOT EXISTS ts tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', coalesce(normalized_text, raw_text))) STORED;

CREATE INDEX IF NOT EXISTS idx_knowledge_block_ts_gin
  ON knowledge_block USING gin (ts);
CREATE INDEX IF NOT EXISTS idx_knowledge_block_parent
  ON knowledge_block (parent_block_id) WHERE parent_block_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_knowledge_block_embedding_model_ver
  ON knowledge_block (embedding_model, embedding_version) WHERE embedding IS NOT NULL;

COMMENT ON COLUMN knowledge_block.parent_block_id IS 'V0.3.1：父块引用，用于表格→单元格、章节→段落等层级关系';
COMMENT ON COLUMN knowledge_block.embedding_model IS 'V0.3.1：embedding 模型名（如 bge-large-zh-v1.5），Hybrid Retrieval 严格按此过滤';
COMMENT ON COLUMN knowledge_block.embedding_version IS 'V0.3.1：embedding 版本（如 2024Q4），与 model 联合唯一标识向量空间';
COMMENT ON COLUMN knowledge_block.embedding_dim IS 'V0.3.1：embedding 维度（如 1024），用于自适应索引校验';
COMMENT ON COLUMN knowledge_block.ts IS 'V0.3.1：生成列 tsvector(simple)，用于 BM25 全文召回（plainto_tsquery）';

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

-- ============================================================================
-- V0.3.1 候选 frozen 状态 + 加权 evidence_refs
-- 对应 07F §9.5/§9.6/§11.2/§12.1
-- ============================================================================
ALTER TABLE graph_candidate_entity DROP CONSTRAINT IF EXISTS ck_candidate_entity_status;
ALTER TABLE graph_candidate_entity ADD CONSTRAINT ck_candidate_entity_status
  CHECK (status IN ('candidate','accepted','reviewing','rejected','conflict','merged','frozen'));

ALTER TABLE graph_candidate_relation DROP CONSTRAINT IF EXISTS ck_candidate_relation_status;
ALTER TABLE graph_candidate_relation ADD CONSTRAINT ck_candidate_relation_status
  CHECK (status IN ('candidate','accepted','reviewing','rejected','conflict','merged','frozen'));

ALTER TABLE graph_candidate_relation
  ADD COLUMN IF NOT EXISTS evidence_refs jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE graph_candidate_relation DROP CONSTRAINT IF EXISTS ck_candidate_relation_evidence_refs;
ALTER TABLE graph_candidate_relation ADD CONSTRAINT ck_candidate_relation_evidence_refs
  CHECK (jsonb_typeof(evidence_refs) = 'array');

COMMENT ON COLUMN graph_candidate_relation.evidence_refs IS 'V0.3.1：加权证据数组 [{blockId,weight,sourceType}]，与 evidence_block_ids 并存；新链路写 evidence_refs，旧链路兼容读 evidence_block_ids';

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
  CONSTRAINT ck_review_task_reason CHECK (reason_code ~ '^[a-z][a-z0-9_]{0,63}$'),
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
