package com.company.smartsupport.graph;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.RequestContext;
import com.company.smartsupport.graph.dto.GraphObjectActionRequest;
import com.company.smartsupport.graph.dto.GraphObjectActionResponse;

@RestController
@RequestMapping("/api/v1/graphs")
public class GraphObjectActionController {

    private final RequestContext requestContext;
    private final FreezeService freezeService;

    public GraphObjectActionController(RequestContext requestContext, FreezeService freezeService) {
        this.requestContext = requestContext;
        this.freezeService = freezeService;
    }

    @PostMapping("/entities/{entityId}/actions")
    public ApiResponse<GraphObjectActionResponse> applyEntityAction(
            @PathVariable String entityId,
            @RequestBody GraphObjectActionRequest request) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), freezeService.applyEntityAction(projectId, entityId, request));
    }

    @PostMapping("/relations/{relationId}/actions")
    public ApiResponse<GraphObjectActionResponse> applyRelationAction(
            @PathVariable String relationId,
            @RequestBody GraphObjectActionRequest request) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), freezeService.applyRelationAction(projectId, relationId, request));
    }
}