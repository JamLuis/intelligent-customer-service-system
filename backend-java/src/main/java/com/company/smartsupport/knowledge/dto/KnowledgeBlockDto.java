package com.company.smartsupport.knowledge.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record KnowledgeBlockDto(
        String blockId,
        String sourceId,
        String graphCategoryId,
        String blockType,
        String sectionPath,
        Integer pageNo,
        Integer rowNo,
        Integer colNo,
        String rawText,
        String normalizedText,
        Map<String, Object> metadata,
        OffsetDateTime createdAt) {
}
