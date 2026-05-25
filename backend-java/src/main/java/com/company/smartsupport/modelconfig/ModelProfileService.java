package com.company.smartsupport.modelconfig;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.integration.AiServiceClient;
import com.company.smartsupport.modelconfig.ModelProfileRepository.ModelProfileRecord;

@Service
public class ModelProfileService {

    public static final String PURPOSE_KNOWLEDGE_EXTRACT = "knowledge_extract";
    public static final String PURPOSE_CHAT_ANSWER = "chat_answer";

    private final ModelProfileRepository repository;
    private final AiServiceClient aiServiceClient;
    private final LocalModelRuntimeService runtimeService;

    public ModelProfileService(ModelProfileRepository repository, AiServiceClient aiServiceClient, LocalModelRuntimeService runtimeService) {
        this.repository = repository;
        this.aiServiceClient = aiServiceClient;
        this.runtimeService = runtimeService;
    }

    public List<ModelProfileDto> list(String projectId, String purpose) {
        validatePurpose(purpose);
        return repository.list(projectId, purpose).stream().map(this::toDto).toList();
    }

    public ModelProfileDto save(String projectId, String purpose, String providerMode, String profileKey, Map<String, Object> body, String actorId) {
        validatePurpose(purpose);
        validateProviderMode(providerMode);
        String key = requiredText(profileKey, "Profile Key 不能为空");
        Optional<ModelProfileRecord> existingRecord = repository.find(projectId, purpose, providerMode, key);
        ModelProfileRecord existing = existingRecord.orElseGet(() -> repository.defaultProfile(purpose, providerMode));
        String apiKey = existing.apiKey();
        if (Boolean.TRUE.equals(body.get("clearApiKey"))) {
            apiKey = "";
        } else if (body.containsKey("apiKey") && body.get("apiKey") != null && !String.valueOf(body.get("apiKey")).isBlank()) {
            apiKey = String.valueOf(body.get("apiKey"));
        }
        ModelProfileRecord profile = new ModelProfileRecord(
                purpose,
                providerMode,
                key,
                text(body.get("displayName"), existing.displayName()),
                text(body.get("provider"), providerMode.equals("local") ? "local-openai-compatible" : existing.provider()),
                requiredText(body.get("apiBaseUrl"), "模型服务地址不能为空"),
                apiKey,
                text(body.get("workspaceId"), ""),
                requiredText(body.get("model"), "模型名称不能为空"),
                text(body.get("embeddingModel"), existing.embeddingModel()),
                integer(body.get("embeddingDim"), existing.embeddingDim()),
                text(body.get("localRuntime"), existing.localRuntime()),
                text(body.get("modelFilePath"), existing.modelFilePath()),
                integer(body.get("contextWindow"), existing.contextWindow()),
                decimal(body.get("temperature"), existing.temperature()),
                integer(body.get("maxTokens"), existing.maxTokens()),
                booleanValue(body.get("forceGraphGrounding"), existing.forceGraphGrounding()),
                booleanValue(body.get("enabled"), existing.enabled()),
                existingRecord.map(ModelProfileRecord::active).orElse(false),
                "unchecked",
                Map.of(),
                existing.updatedAt());
        return toDto(repository.save(projectId, profile, actorId));
    }

    public ModelProfileDto activate(String projectId, String purpose, String providerMode, String profileKey, String actorId) {
        validatePurpose(purpose);
        validateProviderMode(providerMode);
        return toDto(repository.activate(projectId, purpose, providerMode, profileKey, actorId));
    }

    public Map<String, Object> check(String projectId, String purpose, String providerMode, String profileKey, String actorId) {
        validatePurpose(purpose);
        validateProviderMode(providerMode);
        ModelProfileRecord profile = repository.find(projectId, purpose, providerMode, profileKey)
                .orElseGet(() -> repository.defaultProfile(purpose, providerMode));
        Map<String, Object> payload = toAiPayload(profile);
        payload.put("checkEmbedding", PURPOSE_KNOWLEDGE_EXTRACT.equals(purpose));
        Map<String, Object> result = aiServiceClient.checkLlm(payload)
                .orElseGet(() -> Map.of("ok", false, "message", "AI Service 不可用"));
        String status = Boolean.TRUE.equals(result.get("ok")) ? "healthy" : "failed";
        repository.save(projectId, new ModelProfileRecord(
                profile.purpose(),
                profile.providerMode(),
                profile.profileKey(),
                profile.displayName(),
                profile.provider(),
                profile.apiBaseUrl(),
                profile.apiKey(),
                profile.workspaceId(),
                profile.model(),
                profile.embeddingModel(),
                profile.embeddingDim(),
                profile.localRuntime(),
                profile.modelFilePath(),
                profile.contextWindow(),
                profile.temperature(),
                profile.maxTokens(),
                profile.forceGraphGrounding(),
                profile.enabled(),
                profile.active(),
                status,
                result,
                profile.updatedAt()), actorId);
        return result;
    }

    public Map<String, Object> activeAiConfig(String projectId, String purpose) {
        validatePurpose(purpose);
        return toAiPayload(repository.active(projectId, purpose));
    }

    public ModelProfileDto activeProfile(String projectId, String purpose) {
        validatePurpose(purpose);
        return toDto(repository.active(projectId, purpose));
    }

    /**
     * Preload the active model for a given purpose into runtime memory (lazy load).
     * Optionally unloads the active model for the other purpose to save resources.
     */
    public Map<String, Object> preloadForPurpose(String projectId, String purpose) {
        validatePurpose(purpose);
        ModelProfileRecord target = repository.active(projectId, purpose);
        if (target == null || !target.enabled()) {
            return Map.of("ok", false, "message", "没有可用的 " + purpose + " 活跃模型");
        }
        if (!"local".equals(target.providerMode())) {
            return Map.of("ok", true, "message", "云端模型无需预加载", "model", target.model());
        }
        // Unload the other purpose's model if it's a different model on the same runtime
        String otherPurpose = PURPOSE_CHAT_ANSWER.equals(purpose) ? PURPOSE_KNOWLEDGE_EXTRACT : PURPOSE_CHAT_ANSWER;
        try {
            ModelProfileRecord other = repository.active(projectId, otherPurpose);
            if (other != null && "local".equals(other.providerMode())
                    && !other.model().equals(target.model())
                    && other.localRuntime() != null && other.localRuntime().equals(target.localRuntime())) {
                runtimeService.unloadModel(other.localRuntime(), other.apiBaseUrl(), other.model());
            }
        } catch (Exception ignored) {
            // best-effort unload
        }
        Map<String, Object> result = runtimeService.preloadModel(
                target.localRuntime(), target.apiBaseUrl(), target.model());
        result.put("model", target.model());
        result.put("purpose", purpose);
        return result;
    }

    public Map<String, Object> toAiPayload(ModelProfileRecord profile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerMode", profile.providerMode());
        payload.put("provider", profile.provider());
        payload.put("apiBaseUrl", profile.apiBaseUrl());
        payload.put("apiKey", profile.apiKey() == null ? "" : profile.apiKey());
        payload.put("workspaceId", profile.workspaceId() == null ? "" : profile.workspaceId());
        payload.put("model", profile.model());
        payload.put("embeddingModel", profile.embeddingModel());
        payload.put("embeddingDim", profile.embeddingDim());
        payload.put("temperature", profile.temperature());
        payload.put("maxTokens", profile.maxTokens());
        payload.put("forceGraphGrounding", profile.forceGraphGrounding());
        payload.put("profileKey", profile.profileKey());
        payload.put("purpose", profile.purpose());
        return payload;
    }

    private ModelProfileDto toDto(ModelProfileRecord profile) {
        String apiKey = profile.apiKey() == null ? "" : profile.apiKey();
        return new ModelProfileDto(
                profile.purpose(),
                profile.providerMode(),
                profile.profileKey(),
                profile.displayName(),
                profile.provider(),
                profile.apiBaseUrl(),
                StringUtils.hasText(apiKey),
                mask(apiKey),
                profile.workspaceId(),
                profile.model(),
                profile.embeddingModel(),
                profile.embeddingDim(),
                profile.localRuntime(),
                profile.modelFilePath(),
                profile.contextWindow(),
                profile.temperature(),
                profile.maxTokens(),
                profile.forceGraphGrounding(),
                profile.enabled(),
                profile.active(),
                profile.status(),
                profile.lastCheckResult(),
                profile.updatedAt());
    }

    private void validatePurpose(String purpose) {
        if (!PURPOSE_KNOWLEDGE_EXTRACT.equals(purpose) && !PURPOSE_CHAT_ANSWER.equals(purpose)) {
            throw new SmartSupportException("ICSS-KG-400-MODEL_PROFILE_INVALID", "模型用途只能是 knowledge_extract 或 chat_answer");
        }
    }

    private void validateProviderMode(String providerMode) {
        if (!"cloud".equals(providerMode) && !"local".equals(providerMode)) {
            throw new SmartSupportException("ICSS-KG-400-MODEL_PROFILE_INVALID", "模型方式只能是 cloud 或 local");
        }
    }

    private String requiredText(Object value, String message) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank()) {
            throw new SmartSupportException("ICSS-KG-400-MODEL_PROFILE_INVALID", message);
        }
        return text;
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private Integer integer(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "********";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}