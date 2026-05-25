CREATE TABLE IF NOT EXISTS ai_model_profile (
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    project_id VARCHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL CHECK (purpose IN ('knowledge_extract', 'chat_answer')),
    provider_mode VARCHAR(32) NOT NULL CHECK (provider_mode IN ('cloud', 'local')),
    profile_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    api_base_url TEXT NOT NULL,
    api_key TEXT,
    workspace_id VARCHAR(128),
    model_name VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL DEFAULT 'text-embedding-v4',
    embedding_dim INTEGER NOT NULL DEFAULT 1536,
    local_runtime VARCHAR(64),
    model_file_path TEXT,
    context_window INTEGER NOT NULL DEFAULT 8192,
    temperature NUMERIC(4, 2) NOT NULL DEFAULT 0.20,
    max_tokens INTEGER NOT NULL DEFAULT 1024,
    force_graph_grounding BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'unchecked' CHECK (status IN ('unchecked', 'healthy', 'failed')),
    last_check_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, project_id, purpose, provider_mode, profile_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_model_profile_active
    ON ai_model_profile (tenant_id, project_id, purpose)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_ai_model_profile_lookup
    ON ai_model_profile (tenant_id, project_id, purpose, provider_mode, status);