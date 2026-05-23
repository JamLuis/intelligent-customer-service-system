package com.company.smartsupport.graph.dto;

import java.util.List;
import java.util.Map;

public record GraphEntityTypeDto(
        String entityType,
        String label,
        String description,
        List<String> uniqueKeySchema,
        Map<String, Object> propertySchema,
        Map<String, Object> extractorRules,
        String status,
        int sortOrder) {
}
