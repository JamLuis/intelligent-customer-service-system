package com.company.smartsupport.graph.dto;

import java.util.List;

public record GraphTaxonomyResponse(
        List<GraphCategoryDto> categories,
        List<GraphEntityTypeDto> entityTypes,
        List<GraphRelationTypeDto> relationTypes) {
}
