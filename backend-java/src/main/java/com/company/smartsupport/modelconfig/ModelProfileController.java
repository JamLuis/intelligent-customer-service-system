package com.company.smartsupport.modelconfig;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.RequestContext;

@RestController
@RequestMapping("/api/v1/admin/model-profiles")
public class ModelProfileController {

    private final ModelProfileService service;
    private final LocalModelDiscoveryService discoveryService;
    private final LocalModelRuntimeService runtimeService;
    private final RequestContext requestContext;

    public ModelProfileController(ModelProfileService service, LocalModelDiscoveryService discoveryService,
            LocalModelRuntimeService runtimeService, RequestContext requestContext) {
        this.service = service;
        this.discoveryService = discoveryService;
        this.runtimeService = runtimeService;
        this.requestContext = requestContext;
    }

    @GetMapping
    public ApiResponse<List<ModelProfileDto>> list(@RequestParam String purpose) {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), service.list(projectId, purpose));
    }

    @GetMapping("/local-discovery")
    public ApiResponse<Map<String, Object>> discoverLocal(@RequestParam(defaultValue = "chat_answer") String purpose) {
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), discoveryService.discover(purpose));
    }

    @PostMapping("/local-runtime/{runtime}/start")
    public ApiResponse<Map<String, Object>> startLocalRuntime(@PathVariable String runtime) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), runtimeService.start(runtime));
    }

    @PutMapping("/{purpose}/{providerMode}/{profileKey}")
    public ApiResponse<ModelProfileDto> save(
            @PathVariable String purpose,
            @PathVariable String providerMode,
            @PathVariable String profileKey,
            @RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), service.save(projectId, purpose, providerMode, profileKey, body, requestContext.actorId()));
    }

    @PostMapping("/{purpose}/{providerMode}/{profileKey}/check")
    public ApiResponse<Map<String, Object>> check(
            @PathVariable String purpose,
            @PathVariable String providerMode,
            @PathVariable String profileKey) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), service.check(projectId, purpose, providerMode, profileKey, requestContext.actorId()));
    }

    @PostMapping("/{purpose}/{providerMode}/{profileKey}/activate")
    public ApiResponse<ModelProfileDto> activate(
            @PathVariable String purpose,
            @PathVariable String providerMode,
            @PathVariable String profileKey) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), service.activate(projectId, purpose, providerMode, profileKey, requestContext.actorId()));
    }

    @PostMapping("/preload/{purpose}")
    public ApiResponse<Map<String, Object>> preload(@PathVariable String purpose) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), service.preloadForPurpose(projectId, purpose));
    }
}