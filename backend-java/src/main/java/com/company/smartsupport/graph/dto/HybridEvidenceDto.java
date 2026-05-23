package com.company.smartsupport.graph.dto;

import java.util.Map;

public record HybridEvidenceDto(
        String blockId,
        String sourceId,
        String rawTextSummary,
        double vectorScore,
        double bm25Score,
        double graphBoostScore,
        double recencyScore,
        double hybridScore,
        String embeddingModel,
        String embeddingVersion,
        Map<String, Object> metadata) {
}