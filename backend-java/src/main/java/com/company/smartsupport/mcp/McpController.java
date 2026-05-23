package com.company.smartsupport.mcp;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.PageResult;
import com.company.smartsupport.common.RequestContext;
import com.company.smartsupport.mock.MockSupportService;

@RestController
public class McpController {

    private final MockSupportService mockSupportService;
    private final RequestContext requestContext;

    public McpController(MockSupportService mockSupportService, RequestContext requestContext) {
        this.mockSupportService = mockSupportService;
        this.requestContext = requestContext;
    }

    @GetMapping("/api/v1/mcp/capabilities")
    public ApiResponse<PageResult<Map<String, Object>>> listMcpCapabilities(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.capabilities(pageNo, pageSize));
    }

    @GetMapping("/api/v1/mcp/capabilities/{capabilityId}/impact")
    public ApiResponse<Map<String, Object>> impact(@PathVariable String capabilityId) {
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.capabilityImpact(capabilityId));
    }

    @PatchMapping("/api/v1/mcp/capabilities/{capabilityId}/status")
    public ApiResponse<Map<String, Object>> updateMcpCapabilityStatus(@PathVariable String capabilityId, @RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.updateCapabilityStatus(capabilityId, body));
    }

    @PutMapping("/api/v1/graph-mcp-mappings/{mappingId}")
    public ApiResponse<Map<String, Object>> updateGraphMcpMapping(@PathVariable String mappingId, @RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.updateGraphMcpMapping(mappingId, body));
    }
}
