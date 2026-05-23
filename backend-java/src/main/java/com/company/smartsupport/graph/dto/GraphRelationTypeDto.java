package com.company.smartsupport.graph.dto;

import java.util.List;
import java.util.Map;

public record GraphRelationTypeDto(
        String relationType,
        String label,
        String description,
        List<String> fromEntityTypes,
        List<String> toEntityTypes,
        Map<String, Object> propertySchema,
        String inverseRelationType,
        String status,
        int sortOrder) {
}
