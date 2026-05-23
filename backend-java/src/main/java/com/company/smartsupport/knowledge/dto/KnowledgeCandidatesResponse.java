package com.company.smartsupport.knowledge.dto;

import java.util.List;

public record KnowledgeCandidatesResponse(
        String sourceId,
        List<CandidateEntityDto> entities,
        List<CandidateRelationDto> relations,
        KnowledgeCandidatesSummaryDto summary) {
}
