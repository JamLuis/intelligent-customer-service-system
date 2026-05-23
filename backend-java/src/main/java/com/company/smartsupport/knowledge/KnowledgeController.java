package com.company.smartsupport.knowledge;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.PageResult;
import com.company.smartsupport.common.RequestContext;
import com.company.smartsupport.knowledge.dto.KnowledgeBlockDto;
import com.company.smartsupport.knowledge.dto.KnowledgeCandidatesResponse;
import com.company.smartsupport.knowledge.dto.KnowledgeIngestionTaskDto;
import com.company.smartsupport.knowledge.dto.KnowledgeSourceDto;
import com.company.smartsupport.mock.MockSupportService;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final MockSupportService mockSupportService;
    private final RequestContext requestContext;
    private final KnowledgeSourceService knowledgeSourceService;

    public KnowledgeController(
            MockSupportService mockSupportService,
            RequestContext requestContext,
            KnowledgeSourceService knowledgeSourceService) {
        this.mockSupportService = mockSupportService;
        this.requestContext = requestContext;
        this.knowledgeSourceService = knowledgeSourceService;
    }

    @PostMapping("/sources")
    public ApiResponse<KnowledgeSourceDto> createKnowledgeSource(@RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), knowledgeSourceService.createKnowledgeSource(body, projectId));
    }

    @GetMapping("/sources")
    public ApiResponse<PageResult<KnowledgeSourceDto>> listKnowledgeSources(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), knowledgeSourceService.listKnowledgeSources(projectId, pageNo, pageSize));
    }

    @GetMapping("/sources/{sourceId}/tasks")
    public ApiResponse<PageResult<KnowledgeIngestionTaskDto>> tasks(@PathVariable String sourceId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), knowledgeSourceService.listTasks(projectId, sourceId, pageNo, pageSize));
    }

    @GetMapping("/sources/{sourceId}/blocks")
    public ApiResponse<PageResult<KnowledgeBlockDto>> blocks(
            @PathVariable String sourceId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), knowledgeSourceService.listBlocks(projectId, sourceId, pageNo, pageSize));
    }

    @GetMapping("/sources/{sourceId}/candidates")
    public ApiResponse<KnowledgeCandidatesResponse> candidates(@PathVariable String sourceId) {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), knowledgeSourceService.getCandidates(projectId, sourceId));
    }

    @PostMapping("/sources/{sourceId}/actions")
    public ApiResponse<KnowledgeIngestionTaskDto> actions(@PathVariable String sourceId, @RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), knowledgeSourceService.createAction(projectId, sourceId, body));
    }

    @PostMapping("/sources/{sourceId}/retry")
    public ApiResponse<KnowledgeIngestionTaskDto> retry(@PathVariable String sourceId) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), knowledgeSourceService.retry(projectId, sourceId));
    }

    @PostMapping("/cases")
    public ApiResponse<Map<String, Object>> publishKnowledgeCase(@RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.placeholder("deferred", "V0.1 使用知识源入图替代知识案例发布"));
    }
}
