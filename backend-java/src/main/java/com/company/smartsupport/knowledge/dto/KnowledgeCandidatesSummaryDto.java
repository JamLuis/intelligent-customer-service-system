package com.company.smartsupport.knowledge.dto;

public record KnowledgeCandidatesSummaryDto(
        int entityCount,
        int relationCount,
        int reviewingCount,
        int conflictCount) {
}
