package com.company.smartsupport.graph;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.PageResult;
import com.company.smartsupport.common.RequestContext;
import com.company.smartsupport.graph.dto.GraphAssetDetailDto;
import com.company.smartsupport.graph.dto.GraphAssetSummaryDto;
import com.company.smartsupport.graph.dto.GraphTaxonomyResponse;
import com.company.smartsupport.mock.MockSupportService;

@RestController
@RequestMapping("/api/v1/graphs/assets")
public class GraphController {

    private final MockSupportService mockSupportService;
    private final RequestContext requestContext;
    private final GraphTaxonomyService graphTaxonomyService;
    private final GraphAssetService graphAssetService;

    public GraphController(
            MockSupportService mockSupportService,
            RequestContext requestContext,
            GraphTaxonomyService graphTaxonomyService,
            GraphAssetService graphAssetService) {
        this.mockSupportService = mockSupportService;
        this.requestContext = requestContext;
        this.graphTaxonomyService = graphTaxonomyService;
        this.graphAssetService = graphAssetService;
    }

    @GetMapping
    public ApiResponse<PageResult<GraphAssetSummaryDto>> queryGraphAsset(
            @RequestParam(required = false) String graphCategoryId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String relationType,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(
                requestContext.requestId(),
                graphAssetService.queryGraphAssets(projectId, graphCategoryId, entityType, relationType, pageNo, pageSize));
    }

    @GetMapping("/categories")
    public ApiResponse<GraphTaxonomyResponse> categories() {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), graphTaxonomyService.getTaxonomy(projectId));
    }

    @GetMapping("/{graphId}")
    public ApiResponse<GraphAssetDetailDto> detail(@PathVariable String graphId) {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), graphAssetService.getGraphAsset(projectId, graphId));
    }

    @PatchMapping("/{graphId}/draft")
    public ApiResponse<Map<String, Object>> updateGraphAssetDraft(@PathVariable String graphId, @RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.updateGraphDraft(graphId, body));
    }

    @PostMapping("/{graphId}/versions/actions")
    public ApiResponse<Map<String, Object>> publishOrRollbackGraph(@PathVariable String graphId, @RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.graphAction(graphId, body));
    }
}
