package com.company.smartsupport.trace;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.PageResult;
import com.company.smartsupport.common.RequestContext;
import com.company.smartsupport.mock.MockSupportService;

@RestController
@RequestMapping("/api/v1/traces")
public class TraceController {

    private final MockSupportService mockSupportService;
    private final RequestContext requestContext;

    public TraceController(MockSupportService mockSupportService, RequestContext requestContext) {
        this.mockSupportService = mockSupportService;
        this.requestContext = requestContext;
    }

    @GetMapping("/{traceId}")
    public ApiResponse<Map<String, Object>> getExecutionTrace(@PathVariable String traceId,
            @RequestParam(defaultValue = "true") boolean includeSteps,
            @RequestParam(defaultValue = "true") boolean includeMcpCalls) {
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.getTrace(traceId, includeSteps, includeMcpCalls));
    }

    @GetMapping("/{traceId}/steps")
    public ApiResponse<PageResult<Map<String, Object>>> steps(@PathVariable String traceId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.traceSteps(traceId, pageNo, pageSize));
    }

    @GetMapping("/{traceId}/mcp-calls")
    public ApiResponse<PageResult<Map<String, Object>>> mcpCalls(@PathVariable String traceId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.traceMcpCalls(traceId, pageNo, pageSize));
    }
}
