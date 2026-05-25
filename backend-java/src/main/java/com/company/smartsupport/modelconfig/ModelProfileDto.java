package com.company.smartsupport.modelconfig;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record ModelProfileDto(
        String purpose,
        String providerMode,
        String profileKey,
        String displayName,
        String provider,
        String apiBaseUrl,
        boolean apiKeyConfigured,
        String apiKeyMasked,
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