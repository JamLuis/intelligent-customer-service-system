package com.company.smartsupport.modelconfig;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.RequestContext;

@RestController
@RequestMapping("/api/v1/admin/knowledge/model-config")
public class KnowledgeModelConfigController {

    private final KnowledgeModelConfigService service;
    private final RequestContext requestContext;

    public KnowledgeModelConfigController(KnowledgeModelConfigService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    @GetMapping
    public ApiResponse<KnowledgeModelConfigDto> getConfig() {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), service.getConfig(projectId));
    }

    @PutMapping
    public ApiResponse<KnowledgeModelConfigDto> saveConfig(@RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), service.saveConfig(projectId, body, requestContext.actorId()));
    }

    @PostMapping("/check")
    public ApiResponse<Map<String, Object>> checkConfig() {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), service.checkConfig(projectId, requestContext.actorId()));
    }
}
