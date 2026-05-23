package com.company.smartsupport.graph.dto;

import java.util.List;

public record GraphAssetSummaryDto(
        String graphId,
        String graphName,
        String graphCategoryId,
        String graphCategoryName,
        String status,
        int nodeCount,
        int edgeCount,
        double confidence,
        String activeRevisionId,
        List<String> sourceRefs,
        List<String> entityTypes,
        List<String> relationTypes) {
}
