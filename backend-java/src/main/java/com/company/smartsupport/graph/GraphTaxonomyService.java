package com.company.smartsupport.graph;

import org.springframework.stereotype.Service;

import com.company.smartsupport.graph.dto.GraphTaxonomyResponse;

@Service
public class GraphTaxonomyService {

    private final PgGraphMetadataRepository pgGraphMetadataRepository;

    public GraphTaxonomyService(PgGraphMetadataRepository pgGraphMetadataRepository) {
        this.pgGraphMetadataRepository = pgGraphMetadataRepository;
    }

    public GraphTaxonomyResponse getTaxonomy(String projectId) {
        return new GraphTaxonomyResponse(
                pgGraphMetadataRepository.listCategories(projectId),
                pgGraphMetadataRepository.listEntityTypes(),
                pgGraphMetadataRepository.listRelationTypes());
    }
}
