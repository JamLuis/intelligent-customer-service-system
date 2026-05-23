package com.company.smartsupport.knowledge.dto;

import java.util.List;
import java.util.Map;

public record CandidateEntityDto(
        String candidateId,
        String blockId,
        String graphCategoryId,
        String entityType,
        String rawName,
        String canonicalName,
        Map<String, Object> uniqueKey,
        Map<String, Object> properties,
        List<String> evidenceBlockIds,
        double confidence,
        String extractor,
        String status,
        String reviewReason) {
}
