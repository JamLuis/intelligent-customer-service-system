package com.company.smartsupport.graph;

import org.springframework.stereotype.Service;

import com.company.smartsupport.common.PageResult;
import com.company.smartsupport.graph.dto.GraphAssetDetailDto;
import com.company.smartsupport.graph.dto.GraphAssetSummaryDto;

@Service
public class GraphAssetService {

    private final PgGraphMetadataRepository pgGraphMetadataRepository;

    public GraphAssetService(PgGraphMetadataRepository pgGraphMetadataRepository) {
        this.pgGraphMetadataRepository = pgGraphMetadataRepository;
    }

    public PageResult<GraphAssetSummaryDto> queryGraphAssets(
            String projectId,
            String graphCategoryId,
            String entityType,
            String relationType,
            int pageNo,
            int pageSize) {
        return PageResult.of(
                pgGraphMetadataRepository.listGraphAssets(projectId, graphCategoryId, entityType, relationType),
                pageNo,
                pageSize);
    }

    public GraphAssetDetailDto getGraphAsset(String projectId, String graphId) {
        return pgGraphMetadataRepository.getGraphAsset(projectId, graphId);
    }
}
