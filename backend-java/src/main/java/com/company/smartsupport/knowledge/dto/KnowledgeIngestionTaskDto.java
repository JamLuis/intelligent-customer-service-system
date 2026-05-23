package com.company.smartsupport.knowledge.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record KnowledgeIngestionTaskDto(
        String taskId,
        String taskType,
        String status,
        BigDecimal progress,
        Map<String, Object> resultPayload,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt) {
}
