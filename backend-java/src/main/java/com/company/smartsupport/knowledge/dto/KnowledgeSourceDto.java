package com.company.smartsupport.knowledge.dto;

import java.time.OffsetDateTime;

public record KnowledgeSourceDto(
        String sourceId,
        String sourceType,
        String graphCategoryId,
        String graphCategoryName,
        String fileName,
        String objectKey,
        long fileSizeBytes,
        String sensitivityLevel,
        String status,
        String parserStatus,
        String extractStatus,
        String graphBuildStatus,
        String failureReason,
        OffsetDateTime createdAt) {
}
