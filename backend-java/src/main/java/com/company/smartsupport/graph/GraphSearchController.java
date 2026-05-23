package com.company.smartsupport.graph;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.RequestContext;

@RestController
@RequestMapping("/api/v1/graphs/search")
public class GraphSearchController {

    private final RequestContext requestContext;
    private final GraphSearchService graphSearchService;

    public GraphSearchController(RequestContext requestContext, GraphSearchService graphSearchService) {
        this.requestContext = requestContext;
        this.graphSearchService = graphSearchService;
    }

    @PostMapping("/diagnosis")
    public ApiResponse<Map<String, Object>> searchDiagnosis(@RequestBody Map<String, Object> body) {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), graphSearchService.searchDiagnosis(projectId, body));
    }
}