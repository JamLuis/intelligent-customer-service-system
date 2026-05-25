package com.company.smartsupport.modelconfig;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.integration.AiServiceClient;
import com.company.smartsupport.modelconfig.KnowledgeModelConfigRepository.ModelConfigRecord;

@Service
public class KnowledgeModelConfigService {

    private final KnowledgeModelConfigRepository repository;
    private final AiServiceClient aiServiceClient;
    private final ModelProfileService modelProfileService;

    public KnowledgeModelConfigService(KnowledgeModelConfigRepository repository, AiServiceClient aiServiceClient, ModelProfileService modelProfileService) {
        this.repository = repository;
        this.aiServiceClient = aiServiceClient;
        this.modelProfileService = modelProfileService;
    }

    public KnowledgeModelConfigDto getConfig(String projectId) {
        return toDto(current(projectId));
    }

    public KnowledgeModelConfigDto saveConfig(String projectId, Map<String, Object> body, String actorId) {
        ModelConfigRecord existing = current(projectId);
        String providerMode = text(body.get("providerMode"), existing.providerMode());
        if (!providerMode.equals("cloud") && !providerMode.equals("local")) {
            throw new SmartSupportException("ICSS-KG-400-MODEL_CONFIG_INVALID", "模型方式只能是 cloud 或 local");
        }
        String apiKey = existing.apiKey();
        if (Boolean.TRUE.equals(body.get("clearApiKey"))) {
            apiKey = "";
        } else if (body.containsKey("apiKey") && body.get("apiKey") != null && !String.valueOf(body.get("apiKey")).isBlank()) {
            apiKey = String.valueOf(body.get("apiKey"));
        }
        ModelConfigRecord config = new ModelConfigRecord(
                providerMode,
                text(body.get("provider"), providerMode.equals("local") ? "local-openai-compatible" : existing.provider()),
                requiredText(body.get("apiBaseUrl"), "模型服务地址不能为空"),
                apiKey,
                text(body.get("workspaceId"), ""),
                requiredText(body.get("model"), "模型名称不能为空"),
                text(body.get("embeddingModel"), existing.embeddingModel()),
                integer(body.get("embeddingDim"), existing.embeddingDim()),
                booleanValue(body.get("extractEnabled"), existing.extractEnabled()),
                "unchecked",
                Map.of(),
                existing.updatedAt());
        return toDto(repository.save(projectId, config, actorId));
    }

    public Map<String, Object> checkConfig(String projectId, String actorId) {
        ModelConfigRecord config = current(projectId);
        Map<String, Object> payload = toAiPayload(config);
        payload.put("checkEmbedding", true);
        Map<String, Object> result = aiServiceClient.checkLlm(payload)
                .orElseGet(() -> Map.of("ok", false, "message", "AI Service 不可用"));
        String status = Boolean.TRUE.equals(result.get("ok")) ? "healthy" : "failed";
        repository.save(projectId, new ModelConfigRecord(
                config.providerMode(),
                config.provider(),
                config.apiBaseUrl(),
                config.apiKey(),
                config.workspaceId(),
                config.model(),
                config.embeddingModel(),
                config.embeddingDim(),
                config.extractEnabled(),
                status,
                result,
                config.updatedAt()), actorId);
        return result;
    }

    public Map<String, Object> activeAiConfig(String projectId) {
        return modelProfileService.activeAiConfig(projectId, ModelProfileService.PURPOSE_KNOWLEDGE_EXTRACT);
    }

    private ModelConfigRecord current(String projectId) {
        return repository.find(projectId).orElseGet(repository::defaultConfig);
    }

    private Map<String, Object> toAiPayload(ModelConfigRecord config) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerMode", config.providerMode());
        payload.put("provider", config.provider());
        payload.put("apiBaseUrl", config.apiBaseUrl());
        payload.put("apiKey", config.apiKey() == null ? "" : config.apiKey());
        payload.put("workspaceId", config.workspaceId() == null ? "" : config.workspaceId());
        payload.put("model", config.model());
        payload.put("embeddingModel", config.embeddingModel());
        payload.put("embeddingDim", config.embeddingDim());
        return payload;
    }

    private KnowledgeModelConfigDto toDto(ModelConfigRecord config) {
        String apiKey = config.apiKey() == null ? "" : config.apiKey();
        return new KnowledgeModelConfigDto(
                config.providerMode(),
                config.provider(),
                config.apiBaseUrl(),
                StringUtils.hasText(apiKey),
                mask(apiKey),
                config.workspaceId(),
                config.model(),
                config.embeddingModel(),
                config.embeddingDim(),
                config.extractEnabled(),
                config.status(),
                config.lastCheckResult(),
                config.updatedAt());
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

    private String requiredText(Object value, String message) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank()) {
            throw new SmartSupportException("ICSS-KG-400-MODEL_CONFIG_INVALID", message);
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

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
