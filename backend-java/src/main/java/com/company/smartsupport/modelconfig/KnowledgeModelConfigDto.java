package com.company.smartsupport.modelconfig;

import java.time.OffsetDateTime;
import java.util.Map;

public record KnowledgeModelConfigDto(
        String providerMode,
        String provider,
        String apiBaseUrl,
        boolean apiKeyConfigured,
        String apiKeyMasked,
        String workspaceId,
        String model,
        String embeddingModel,
        Integer embeddingDim,
        boolean extractEnabled,
        String status,
        Map<String, Object> lastCheckResult,
        OffsetDateTime updatedAt) {
}
