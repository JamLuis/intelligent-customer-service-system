package com.company.smartsupport.graph.dto;

import java.time.OffsetDateTime;

public record GraphObjectActionResponse(
        String objectType,
        String objectId,
        String action,
        String previousStatus,
        String currentStatus,
        String reason,
        OffsetDateTime operatedAt) {
}