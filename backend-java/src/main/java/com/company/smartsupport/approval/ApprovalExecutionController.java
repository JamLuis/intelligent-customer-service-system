package com.company.smartsupport.approval;

import java.util.Map;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.RequestContext;
import com.company.smartsupport.mock.MockSupportService;

@RestController
public class ApprovalExecutionController {

    private final MockSupportService mockSupportService;
    private final RequestContext requestContext;

    public ApprovalExecutionController(MockSupportService mockSupportService, RequestContext requestContext) {
        this.mockSupportService = mockSupportService;
        this.requestContext = requestContext;
    }

    @PostMapping("/api/v1/approvals")
    public ApiResponse<Map<String, Object>> createApprovalOrder(@RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.placeholder("deferred", "审批系统归属未定，V0.1 仅返回占位响应"));
    }

    @PatchMapping("/api/v1/approvals/{approvalId}/review")
    public ApiResponse<Map<String, Object>> reviewApprovalOrder(@PathVariable String approvalId, @RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), Map.of("approvalId", approvalId, "approvalStatus", "mock_reviewed", "mock", true));
    }

    @PostMapping("/api/v1/executions")
    public ApiResponse<Map<String, Object>> executeApprovedAction(@RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.placeholder("blocked", "V0.1 禁止真实生产写操作，执行能力后置 V0.3"));
    }
}
