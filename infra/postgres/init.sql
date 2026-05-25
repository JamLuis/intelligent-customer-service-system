CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS source_registry (
    id BIGSERIAL PRIMARY KEY,
    source_key VARCHAR(128) NOT NULL UNIQUE,
    source_type VARCHAR(64) NOT NULL,
    source_name VARCHAR(256) NOT NULL,
    version_tag VARCHAR(128),
    access_scope VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

\i /schema/08A/support_session.sql
\i /schema/08A/diagnostic_case.sql
\i /schema/08A/execution_trace.sql
\i /schema/08A/execution_trace_step.sql
\i /schema/08A/mcp_capability.sql
\i /schema/08A/mcp_call_log.sql
\i /schema/08A/knowledge_source.sql
\i /schema/08A/knowledge_ingestion_task.sql
\i /schema/08A/knowledge_model_config.sql
\i /schema/08A/ai_model_profile.sql
\i /schema/08A/graph_build_batch.sql
\i /schema/08A/graph_asset.sql
\i /schema/08A/graph_revision.sql
\i /schema/08A/real_kg_schema.sql
\i /schema/08A/graph_protected_taxonomy.sql
-- 注意：业务领域本体（实体类型/关系类型/分类）必须由租户/项目通过 KG-ADMIN-* API 注册，
-- 不再下发任何领域 seed。如需查看一个示例，可手工执行 /schema/08A/examples/graph_taxonomy_example_marine.sql。
\i /schema/08A/mcp_capability_status_log.sql
\i /schema/08A/route_template.sql
\i /schema/08A/route_evaluation.sql
\i /schema/08A/audit_log.sql