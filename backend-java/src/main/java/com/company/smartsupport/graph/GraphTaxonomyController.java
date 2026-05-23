package com.company.smartsupport.graph;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.RequestContext;
import com.company.smartsupport.graph.dto.GraphTaxonomyResponse;

@RestController
@RequestMapping("/api/v1/graphs")
public class GraphTaxonomyController {

    private final GraphTaxonomyService graphTaxonomyService;
    private final RequestContext requestContext;

    public GraphTaxonomyController(GraphTaxonomyService graphTaxonomyService, RequestContext requestContext) {
        this.graphTaxonomyService = graphTaxonomyService;
        this.requestContext = requestContext;
    }

    @GetMapping("/taxonomy")
    public ApiResponse<GraphTaxonomyResponse> taxonomy() {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), graphTaxonomyService.getTaxonomy(projectId));
    }
}
