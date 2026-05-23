package com.company.smartsupport.support;

import java.util.Map;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.RequestContext;
import com.company.smartsupport.mock.MockSupportService;

@RestController
@RequestMapping("/api/v1/support/sessions")
public class SupportSessionController {

    private final MockSupportService mockSupportService;
    private final RequestContext requestContext;

    public SupportSessionController(MockSupportService mockSupportService, RequestContext requestContext) {
        this.mockSupportService = mockSupportService;
        this.requestContext = requestContext;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createSupportSession(@RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.createSession(body, projectId));
    }

    @PatchMapping("/{sessionId}/context")
    public ApiResponse<Map<String, Object>> updateSessionContext(@PathVariable String sessionId, @RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.updateSessionContext(sessionId, body));
    }
}
