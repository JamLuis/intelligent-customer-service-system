package com.company.smartsupport.graph.dto;

import java.util.List;

public record GraphCategoryDto(
        String categoryId,
        String categoryName,
        String domain,
        String description,
        List<String> entityTypeScope,
        List<String> relationTypeScope,
        String status,
        int sortOrder) {
}
