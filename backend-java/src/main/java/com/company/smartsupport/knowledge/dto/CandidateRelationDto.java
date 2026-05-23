package com.company.smartsupport.knowledge.dto;

import java.util.List;
import java.util.Map;

public record CandidateRelationDto(
        String candidateRelationId,
        String sourceCandidateId,
        String targetCandidateId,
        String graphCategoryId,
        String relationType,
        Map<String, Object> properties,
        List<String> evidenceBlockIds,
        double confidence,
        String extractor,
        String status,
        String reviewReason) {
}
