package com.company.smartsupport.graph.dto;

import java.util.List;
import java.util.Map;

public record GraphAssetDetailDto(
        String graphId,
        String graphName,
        String graphCategoryId,
        String graphCategoryName,
        String status,
        int nodeCount,
        int edgeCount,
        double confidence,
        String activeRevisionId,
        String neo4jGraphRef,
        List<String> sourceRefs,
        List<String> entityTypes,
        List<String> relationTypes,
        List<String> classificationPath,
        List<Map<String, Object>> nodes,
        List<Map<String, Object>> edges,
        List<Map<String, Object>> revisions) {
}
