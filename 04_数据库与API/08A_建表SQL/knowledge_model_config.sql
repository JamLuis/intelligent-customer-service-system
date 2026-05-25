CREATE TABLE IF NOT EXISTS knowledge_model_config (
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    project_id VARCHAR(64) NOT NULL,
    provider_mode VARCHAR(32) NOT NULL DEFAULT 'cloud' CHECK (provider_mode IN ('cloud', 'local')),
    provider VARCHAR(64) NOT NULL DEFAULT 'openai-compatible',
    api_base_url TEXT NOT NULL,
    api_key TEXT,
    workspace_id VARCHAR(128),
    model_name VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL DEFAULT 'text-embedding-v4',
    embedding_dim INTEGER NOT NULL DEFAULT 1536,
    extract_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'unchecked' CHECK (status IN ('unchecked', 'healthy', 'failed')),
    last_check_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, project_id)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_model_config_status
    ON knowledge_model_config (tenant_id, project_id, status);
