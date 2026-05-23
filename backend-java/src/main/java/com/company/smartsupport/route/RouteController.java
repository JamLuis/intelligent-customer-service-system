package com.company.smartsupport.route;

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
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final MockSupportService mockSupportService;
    private final RequestContext requestContext;

    public RouteController(MockSupportService mockSupportService, RequestContext requestContext) {
        this.mockSupportService = mockSupportService;
        this.requestContext = requestContext;
    }

    @PatchMapping("/{routeId}/status")
    public ApiResponse<Map<String, Object>> updateRouteTemplate(@PathVariable String routeId, @RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.updateRoute(routeId, body));
    }

    @PostMapping("/evaluations")
    public ApiResponse<Map<String, Object>> submitAnswerFeedback(@RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.submitFeedback(body));
    }
}
