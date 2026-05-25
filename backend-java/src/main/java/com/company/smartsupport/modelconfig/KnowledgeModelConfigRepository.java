package com.company.smartsupport.modelconfig;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.company.smartsupport.common.SmartSupportException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class KnowledgeModelConfigRepository {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public KnowledgeModelConfigRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public Optional<ModelConfigRecord> find(String projectId) {
        return jdbcClient.sql("""
                SELECT provider_mode,
                       provider,
                       api_base_url,
                       api_key,
                       workspace_id,
                       model_name,
                       embedding_model,
                       embedding_dim,
                       extract_enabled,
                       status,
                       last_check_result::text,
                       updated_at
                FROM knowledge_model_config
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                """)
                .param("projectId", projectId)
                .query((rs, rowNum) -> new ModelConfigRecord(
                        rs.getString("provider_mode"),
                        rs.getString("provider"),
                        rs.getString("api_base_url"),
                        rs.getString("api_key"),
                        rs.getString("workspace_id"),
                        rs.getString("model_name"),
                        rs.getString("embedding_model"),
                        rs.getInt("embedding_dim"),
                        rs.getBoolean("extract_enabled"),
                        rs.getString("status"),
                        readJson(rs.getString("last_check_result")),
                        rs.getObject("updated_at", OffsetDateTime.class)))
                .optional();
    }

    public ModelConfigRecord save(String projectId, ModelConfigRecord config, String actorId) {
        return jdbcClient.sql("""
                INSERT INTO knowledge_model_config (
                    project_id,
                    provider_mode,
                    provider,
                    api_base_url,
                    api_key,
                    workspace_id,
                    model_name,
                    embedding_model,
                    embedding_dim,
                    extract_enabled,
                    status,
                    last_check_result,
                    updated_by,
                    updated_at
                ) VALUES (
                    :projectId,
                    :providerMode,
                    :provider,
                    :apiBaseUrl,
                    :apiKey,
                    :workspaceId,
                    :modelName,
                    :embeddingModel,
                    :embeddingDim,
                    :extractEnabled,
                    :status,
                    CAST(:lastCheckResult AS jsonb),
                    :actorId,
                    now()
                )
                ON CONFLICT (tenant_id, project_id) DO UPDATE SET
                    provider_mode = EXCLUDED.provider_mode,
                    provider = EXCLUDED.provider,
                    api_base_url = EXCLUDED.api_base_url,
                    api_key = EXCLUDED.api_key,
                    workspace_id = EXCLUDED.workspace_id,
                    model_name = EXCLUDED.model_name,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding_dim = EXCLUDED.embedding_dim,
                    extract_enabled = EXCLUDED.extract_enabled,
                    status = EXCLUDED.status,
                    last_check_result = EXCLUDED.last_check_result,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = now()
                RETURNING provider_mode,
                          provider,
                          api_base_url,
                          api_key,
                          workspace_id,
                          model_name,
                          embedding_model,
                          embedding_dim,
                          extract_enabled,
                          status,
                          last_check_result::text,
                          updated_at
                """)
                .param("projectId", projectId)
                .param("providerMode", config.providerMode())
                .param("provider", config.provider())
                .param("apiBaseUrl", config.apiBaseUrl())
                .param("apiKey", config.apiKey())
                .param("workspaceId", config.workspaceId())
                .param("modelName", config.model())
                .param("embeddingModel", config.embeddingModel())
                .param("embeddingDim", config.embeddingDim())
                .param("extractEnabled", config.extractEnabled())
                .param("status", config.status())
                .param("lastCheckResult", writeJson(config.lastCheckResult()))
                .param("actorId", actorId)
                .query((rs, rowNum) -> new ModelConfigRecord(
                        rs.getString("provider_mode"),
                        rs.getString("provider"),
                        rs.getString("api_base_url"),
                        rs.getString("api_key"),
                        rs.getString("workspace_id"),
                        rs.getString("model_name"),
                        rs.getString("embedding_model"),
                        rs.getInt("embedding_dim"),
                        rs.getBoolean("extract_enabled"),
                        rs.getString("status"),
                        readJson(rs.getString("last_check_result")),
                        rs.getObject("updated_at", OffsetDateTime.class)))
                .single();
    }

    public ModelConfigRecord defaultConfig() {
        return new ModelConfigRecord(
                "cloud",
                "bailian",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "",
                "",
                "qwen3.6-flash",
                "text-embedding-v4",
                1536,
                true,
                "unchecked",
                new LinkedHashMap<>(),
                null);
    }

    private Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, OBJECT_MAP);
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (IOException ex) {
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "模型配置 JSON 序列化失败");
        }
    }

    public record ModelConfigRecord(
            String providerMode,
            String provider,
            String apiBaseUrl,
            String apiKey,
            String workspaceId,
            String model,
            String embeddingModel,
            Integer embeddingDim,
            boolean extractEnabled,
            String status,
            Map<String, Object> lastCheckResult,
            OffsetDateTime updatedAt) {
    }
}
