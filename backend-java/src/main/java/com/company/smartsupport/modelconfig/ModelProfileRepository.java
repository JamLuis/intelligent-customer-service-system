package com.company.smartsupport.modelconfig;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.company.smartsupport.common.SmartSupportException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class ModelProfileRepository {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public ModelProfileRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public List<ModelProfileRecord> list(String projectId, String purpose) {
        List<ModelProfileRecord> rows = jdbcClient.sql(baseSelect() + """
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND purpose = :purpose
                ORDER BY purpose, provider_mode, active DESC, profile_key
                """)
                .param("projectId", projectId)
                .param("purpose", purpose)
                .query(this::mapRow)
                .list();
        if (!rows.isEmpty()) {
            return rows;
        }
        return defaultProfiles(purpose);
    }

    public Optional<ModelProfileRecord> find(String projectId, String purpose, String providerMode, String profileKey) {
        return jdbcClient.sql(baseSelect() + """
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND purpose = :purpose
                  AND provider_mode = :providerMode
                  AND profile_key = :profileKey
                """)
                .param("projectId", projectId)
                .param("purpose", purpose)
                .param("providerMode", providerMode)
                .param("profileKey", profileKey)
                .query(this::mapRow)
                .optional();
    }

    public ModelProfileRecord active(String projectId, String purpose) {
        return jdbcClient.sql(baseSelect() + """
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND purpose = :purpose
                  AND active = TRUE
                """)
                .param("projectId", projectId)
                .param("purpose", purpose)
                .query(this::mapRow)
                .optional()
                .orElseGet(() -> defaultProfile(purpose, "local"));
    }

    public ModelProfileRecord save(String projectId, ModelProfileRecord profile, String actorId) {
        return jdbcClient.sql("""
                INSERT INTO ai_model_profile (
                    project_id,
                    purpose,
                    provider_mode,
                    profile_key,
                    display_name,
                    provider,
                    api_base_url,
                    api_key,
                    workspace_id,
                    model_name,
                    embedding_model,
                    embedding_dim,
                    local_runtime,
                    model_file_path,
                    context_window,
                    temperature,
                    max_tokens,
                    force_graph_grounding,
                    enabled,
                    active,
                    status,
                    last_check_result,
                    updated_by,
                    updated_at
                ) VALUES (
                    :projectId,
                    :purpose,
                    :providerMode,
                    :profileKey,
                    :displayName,
                    :provider,
                    :apiBaseUrl,
                    :apiKey,
                    :workspaceId,
                    :modelName,
                    :embeddingModel,
                    :embeddingDim,
                    :localRuntime,
                    :modelFilePath,
                    :contextWindow,
                    :temperature,
                    :maxTokens,
                    :forceGraphGrounding,
                    :enabled,
                    :active,
                    :status,
                    CAST(:lastCheckResult AS jsonb),
                    :actorId,
                    now()
                )
                ON CONFLICT (tenant_id, project_id, purpose, provider_mode, profile_key) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    provider = EXCLUDED.provider,
                    api_base_url = EXCLUDED.api_base_url,
                    api_key = EXCLUDED.api_key,
                    workspace_id = EXCLUDED.workspace_id,
                    model_name = EXCLUDED.model_name,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding_dim = EXCLUDED.embedding_dim,
                    local_runtime = EXCLUDED.local_runtime,
                    model_file_path = EXCLUDED.model_file_path,
                    context_window = EXCLUDED.context_window,
                    temperature = EXCLUDED.temperature,
                    max_tokens = EXCLUDED.max_tokens,
                    force_graph_grounding = EXCLUDED.force_graph_grounding,
                    enabled = EXCLUDED.enabled,
                    status = EXCLUDED.status,
                    last_check_result = EXCLUDED.last_check_result,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = now()
                RETURNING tenant_id, project_id, purpose, provider_mode, profile_key, display_name,
                          provider, api_base_url, api_key, workspace_id, model_name, embedding_model,
                          embedding_dim, local_runtime, model_file_path, context_window, temperature,
                          max_tokens, force_graph_grounding, enabled, active, status, last_check_result::text, updated_at
                """)
                .param("projectId", projectId)
                .param("purpose", profile.purpose())
                .param("providerMode", profile.providerMode())
                .param("profileKey", profile.profileKey())
                .param("displayName", profile.displayName())
                .param("provider", profile.provider())
                .param("apiBaseUrl", profile.apiBaseUrl())
                .param("apiKey", profile.apiKey())
                .param("workspaceId", profile.workspaceId())
                .param("modelName", profile.model())
                .param("embeddingModel", profile.embeddingModel())
                .param("embeddingDim", profile.embeddingDim())
                .param("localRuntime", profile.localRuntime())
                .param("modelFilePath", profile.modelFilePath())
                .param("contextWindow", profile.contextWindow())
                .param("temperature", profile.temperature())
                .param("maxTokens", profile.maxTokens())
                .param("forceGraphGrounding", profile.forceGraphGrounding())
                .param("enabled", profile.enabled())
                .param("active", profile.active())
                .param("status", profile.status())
                .param("lastCheckResult", writeJson(profile.lastCheckResult()))
                .param("actorId", actorId)
                .query(this::mapRow)
                .single();
    }

    public ModelProfileRecord activate(String projectId, String purpose, String providerMode, String profileKey, String actorId) {
        jdbcClient.sql("""
                UPDATE ai_model_profile
                SET active = FALSE,
                    updated_by = :actorId,
                    updated_at = now()
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND purpose = :purpose
                """)
                .param("projectId", projectId)
                .param("purpose", purpose)
                .param("actorId", actorId)
                .update();
        jdbcClient.sql("""
                UPDATE ai_model_profile
                SET active = TRUE,
                    enabled = TRUE,
                    updated_by = :actorId,
                    updated_at = now()
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND purpose = :purpose
                  AND provider_mode = :providerMode
                  AND profile_key = :profileKey
                """)
                .param("projectId", projectId)
                .param("purpose", purpose)
                .param("providerMode", providerMode)
                .param("profileKey", profileKey)
                .param("actorId", actorId)
                .update();
        return find(projectId, purpose, providerMode, profileKey).orElseThrow(() -> new SmartSupportException("ICSS-KG-404-MODEL_PROFILE_NOT_FOUND", "模型 Profile 不存在"));
    }

    public List<ModelProfileRecord> defaultProfiles(String purpose) {
        List<ModelProfileRecord> profiles = new ArrayList<>();
        profiles.add(defaultProfile(purpose, "cloud"));
        profiles.add(defaultProfile(purpose, "local"));
        return profiles;
    }

    public ModelProfileRecord defaultProfile(String purpose, String providerMode) {
        if ("local".equals(providerMode)) {
            return new ModelProfileRecord(
                    purpose,
                    "local",
                    "default-local",
                    purposeLabel(purpose) + " - 本地模型",
                    "mlx-openai-compatible",
                    "http://127.0.0.1:18090/v1",
                    "",
                    "",
                    "mlx-community/Qwen3.5-2B-4bit",
                    "mlx-community/bge-m3-mlx-4bit",
                    1024,
                    "mlx",
                    "",
                    8192,
                    BigDecimal.valueOf("chat_answer".equals(purpose) ? 0.20 : 0.00),
                    "chat_answer".equals(purpose) ? 1024 : 900,
                    true,
                    true,
                    "chat_answer".equals(purpose),
                    "unchecked",
                    new LinkedHashMap<>(),
                    null);
        }
        return new ModelProfileRecord(
                purpose,
                "cloud",
                "default-cloud",
                purposeLabel(purpose) + " - 云端模型",
                "bailian",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "",
                "",
                "qwen3.6-flash",
                "text-embedding-v4",
                1536,
                "",
                "",
                8192,
                BigDecimal.valueOf("chat_answer".equals(purpose) ? 0.20 : 0.00),
                "chat_answer".equals(purpose) ? 1024 : 900,
                true,
                true,
                false,
                "unchecked",
                new LinkedHashMap<>(),
                null);
    }

    private String baseSelect() {
        return """
                SELECT tenant_id,
                       project_id,
                       purpose,
                       provider_mode,
                       profile_key,
                       display_name,
                       provider,
                       api_base_url,
                       api_key,
                       workspace_id,
                       model_name,
                       embedding_model,
                       embedding_dim,
                       local_runtime,
                       model_file_path,
                       context_window,
                       temperature,
                       max_tokens,
                       force_graph_grounding,
                       enabled,
                       active,
                       status,
                       last_check_result::text,
                       updated_at
                FROM ai_model_profile
                """;
    }

    private ModelProfileRecord mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ModelProfileRecord(
                rs.getString("purpose"),
                rs.getString("provider_mode"),
                rs.getString("profile_key"),
                rs.getString("display_name"),
                rs.getString("provider"),
                rs.getString("api_base_url"),
                rs.getString("api_key"),
                rs.getString("workspace_id"),
                rs.getString("model_name"),
                rs.getString("embedding_model"),
                rs.getInt("embedding_dim"),
                rs.getString("local_runtime"),
                rs.getString("model_file_path"),
                rs.getInt("context_window"),
                rs.getBigDecimal("temperature"),
                rs.getInt("max_tokens"),
                rs.getBoolean("force_graph_grounding"),
                rs.getBoolean("enabled"),
                rs.getBoolean("active"),
                rs.getString("status"),
                readJson(rs.getString("last_check_result")),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private String purposeLabel(String purpose) {
        return "chat_answer".equals(purpose) ? "对话问答" : "知识抽取";
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
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "模型 Profile JSON 序列化失败");
        }
    }

    public record ModelProfileRecord(
            String purpose,
            String providerMode,
            String profileKey,
            String displayName,
            String provider,
            String apiBaseUrl,
            String apiKey,
            String workspaceId,
            String model,
            String embeddingModel,
            Integer embeddingDim,
            String localRuntime,
            String modelFilePath,
            Integer contextWindow,
            BigDecimal temperature,
            Integer maxTokens,
            boolean forceGraphGrounding,
            boolean enabled,
            boolean active,
            String status,
            Map<String, Object> lastCheckResult,
            OffsetDateTime updatedAt) {
    }
}