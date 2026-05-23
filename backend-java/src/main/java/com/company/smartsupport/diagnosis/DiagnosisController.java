package com.company.smartsupport.diagnosis;

import java.io.IOException;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.RequestContext;
import com.company.smartsupport.mock.MockSupportService;

@RestController
@RequestMapping("/api/v1/diagnosis/cases")
public class DiagnosisController {

    private final MockSupportService mockSupportService;
    private final RequestContext requestContext;

    public DiagnosisController(MockSupportService mockSupportService, RequestContext requestContext) {
        this.mockSupportService = mockSupportService;
        this.requestContext = requestContext;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> startDiagnosis(@RequestBody Map<String, Object> body) {
        requestContext.requireIdempotencyKey();
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.startDiagnosis(body, projectId));
    }

    @GetMapping("/{caseId}")
    public ApiResponse<Map<String, Object>> getDiagnosisCase(@PathVariable String caseId,
            @RequestParam(defaultValue = "true") boolean includeLatestTrace) {
        requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), mockSupportService.getCase(caseId));
    }

    @GetMapping("/{caseId}/events")
    public SseEmitter subscribeDiagnosisEvents(@PathVariable String caseId) throws IOException {
        requestContext.requireProjectId();
        SseEmitter emitter = new SseEmitter(30_000L);
        emitter.send(SseEmitter.event().name("diagnosis.started").data(Map.of("caseId", caseId, "status", "diagnosing")));
        emitter.send(SseEmitter.event().name("diagnosis.step").data(Map.of("caseId", caseId, "stepType", "graph", "status", "success", "message", "图谱路径命中")));
        emitter.send(SseEmitter.event().name("diagnosis.mcp_call").data(Map.of("caseId", caseId, "capabilityCode", "device.getStatus", "status", "success", "durationMs", 87)));
        emitter.send(SseEmitter.event().name("diagnosis.completed").data(Map.of("caseId", caseId, "caseStatus", "concluded")));
        emitter.complete();
        return emitter;
    }
}
