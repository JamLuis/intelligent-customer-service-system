package com.company.smartsupport.mock;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.company.smartsupport.common.PageResult;
import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.integration.AiServiceClient;
import com.company.smartsupport.integration.McpServerClient;

/**
 * 仅保留当前仍被 Controller 引用的占位实现：
 *   - GraphController:           updateGraphDraft / graphAction
 *   - DiagnosisController:       startDiagnosis / getCase
 *   - SupportSessionController:  createSession / updateSessionContext
 *   - TraceController:           getTrace / traceSteps / traceMcpCalls
 *   - McpController:             capabilities / capabilityImpact / updateCapabilityStatus / updateGraphMcpMapping / updateRoute / submitFeedback
 *   - Approval/Knowledge:        placeholder
 * 不再硬编码任何业务本体（设备号、船舶、告警规则、关系类型等），保持平台通用性。
 * 真实业务数据来自 GraphTaxonomyService / GraphAssetService / KnowledgeService 等真实服务。
 */
@Service
public class MockSupportService {

    private final Map<String, Map<String, Object>> sessions = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> cases = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> traces = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> capabilities = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> routes = new LinkedHashMap<>();
    private final AiServiceClient aiServiceClient;
    private final McpServerClient mcpServerClient;

    public MockSupportService(AiServiceClient aiServiceClient, McpServerClient mcpServerClient) {
        this.aiServiceClient = aiServiceClient;
        this.mcpServerClient = mcpServerClient;
    }

    public Map<String, Object> createSession(Map<String, Object> body, String projectId) {
        String questionText = stringValue(body, "questionText");
        if (!StringUtils.hasText(questionText)) {
            throw new SmartSupportException("ICSS-DIAG-400-QUESTION_EMPTY", "问题不能为空");
        }
        String sessionId = uuid();
        boolean contextRequired = "context_required".equals(body.get("mockScenario"));
        Map<String, Object> session = mapOf(
                "sessionId", sessionId,
                "projectId", projectId,
                "questionText", questionText,
                "status", contextRequired ? "waiting_context" : "ready",
                "entities", List.of(),
                "missingFields", contextRequired ? List.of("context") : List.of(),
                "createdAt", now());
        sessions.put(sessionId, session);
        return session;
    }

    public Map<String, Object> updateSessionContext(String sessionId, Map<String, Object> body) {
        Map<String, Object> session = require(sessions, sessionId, "ICSS-DIAG-404-SESSION_NOT_FOUND", "会话不存在");
        session.put("status", "ready");
        session.put("missingFields", List.of());
        session.put("followUpAnswer", body.getOrDefault("followUpAnswer", ""));
        return session;
    }

    public Map<String, Object> startDiagnosis(Map<String, Object> body, String projectId) {
        String sessionId = stringValue(body, "sessionId");
        Map<String, Object> session = require(sessions, sessionId, "ICSS-DIAG-404-SESSION_NOT_FOUND", "会话不存在");
        if ("waiting_context".equals(session.get("status"))) {
            throw new SmartSupportException("ICSS-DIAG-409-CONTEXT_REQUIRED", "诊断上下文不足，请补充必要字段");
        }
        String caseId = uuid();
        String traceId = uuid();
        boolean degraded = "mcp_degraded".equals(body.get("mockScenario"));
        Map<String, Object> aiResult = aiServiceClient.runDiagnosis(mapOf(
                "sessionId", sessionId,
                "questionText", session.getOrDefault("questionText", ""),
                "projectId", projectId,
                "mockScenario", body.get("mockScenario"))).orElse(Map.of());
        Map<String, Object> mcpResult = mcpServerClient.invoke("diagnostic.placeholder", mapOf(
                "projectId", projectId,
                "mockScenario", body.get("mockScenario"))).orElse(Map.of());
        Map<String, Object> trace = createTrace(traceId, caseId, projectId, degraded, aiResult, mcpResult);
        Map<String, Object> diagnosticCase = mapOf(
                "caseId", caseId,
                "sessionId", sessionId,
                "projectId", projectId,
                "status", "concluded",
                "rootCause", aiResult.getOrDefault("rootCause", degraded ? "实时通道超时，已基于静态知识给出降级诊断" : "未提供真实诊断结果，请实现 AI Service /diagnosis/run"),
                "confidenceScore", aiResult.getOrDefault("confidenceScore", degraded ? 60.0 : 70.0),
                "evidenceItems", aiResult.getOrDefault("evidenceItems", List.of()),
                "recommendedActions", aiResult.getOrDefault("recommendedActions", List.of()),
                "latestTrace", mapOf("traceId", traceId, "status", trace.get("status"), "degraded", degraded));
        cases.put(caseId, diagnosticCase);
        traces.put(traceId, trace);
        session.put("status", "analyzed");
        return mapOf(
                "caseId", caseId,
                "traceId", traceId,
                "caseStatus", "concluded",
                "traceStatus", trace.get("status"),
                "sseUrl", "/api/v1/diagnosis/cases/" + caseId + "/events");
    }

    public Map<String, Object> getCase(String caseId) {
        return require(cases, caseId, "ICSS-DIAG-404-CASE_NOT_FOUND", "诊断案例不存在");
    }

    public Map<String, Object> getTrace(String traceId, boolean includeSteps, boolean includeMcpCalls) {
        Map<String, Object> trace = new LinkedHashMap<>(require(traces, traceId, "ICSS-TRACE-404-NOT_FOUND", "执行轨迹不存在"));
        if (!includeSteps) {
            trace.remove("reasoningSteps");
        }
        if (!includeMcpCalls) {
            trace.remove("mcpCalls");
        }
        return trace;
    }

    public PageResult<Map<String, Object>> traceSteps(String traceId, int pageNo, int pageSize) {
        Map<String, Object> trace = require(traces, traceId, "ICSS-TRACE-404-NOT_FOUND", "执行轨迹不存在");
        return PageResult.of(listValue(trace.get("reasoningSteps")), pageNo, pageSize);
    }

    public PageResult<Map<String, Object>> traceMcpCalls(String traceId, int pageNo, int pageSize) {
        Map<String, Object> trace = require(traces, traceId, "ICSS-TRACE-404-NOT_FOUND", "执行轨迹不存在");
        return PageResult.of(listValue(trace.get("mcpCalls")), pageNo, pageSize);
    }

    public Map<String, Object> updateGraphDraft(String graphId, Map<String, Object> body) {
        return mapOf(
                "graphId", graphId,
                "draftVersion", "draft-" + System.currentTimeMillis(),
                "status", "reviewing",
                "editReason", body.getOrDefault("editReason", ""));
    }

    public Map<String, Object> graphAction(String graphId, Map<String, Object> body) {
        String action = stringValue(body, "action");
        return mapOf(
                "graphId", graphId,
                "graphStatus", "rollback".equals(action) ? "rolled_back" : "published",
                "activeVersion", body.getOrDefault("targetVersion", ""));
    }

    public PageResult<Map<String, Object>> capabilities(int pageNo, int pageSize) {
        mcpServerClient.tools().ifPresent(tools -> {
            for (Map<String, Object> tool : tools) {
                String code = String.valueOf(tool.get("name"));
                String id = "cap-" + code.replace('.', '-');
                capabilities.putIfAbsent(id, mapOf(
                        "capabilityId", id,
                        "capabilityCode", code,
                        "capabilityName", code,
                        "category", tool.getOrDefault("category", "unknown"),
                        "riskLevel", tool.getOrDefault("level", "L0"),
                        "status", "enabled",
                        "lastHealthStatus", "healthy",
                        "boundary", tool.getOrDefault("description", "MCP tool")));
            }
        });
        return PageResult.of(new ArrayList<>(capabilities.values()), pageNo, pageSize);
    }

    public Map<String, Object> capabilityImpact(String capabilityId) {
        Map<String, Object> capability = require(capabilities, capabilityId, "ICSS-MCP-404-CAPABILITY_NOT_FOUND", "MCP 能力不存在");
        return mapOf(
                "capabilityId", capabilityId,
                "capabilityCode", capability.get("capabilityCode"),
                "impactRoutes", List.of(),
                "recentCallCount7d", 0);
    }

    public Map<String, Object> updateCapabilityStatus(String capabilityId, Map<String, Object> body) {
        Map<String, Object> capability = require(capabilities, capabilityId, "ICSS-MCP-404-CAPABILITY_NOT_FOUND", "MCP 能力不存在");
        String targetStatus = stringValue(body, "targetStatus");
        if ("disabled".equals(targetStatus) && !Boolean.TRUE.equals(body.get("impactConfirmed"))) {
            throw new SmartSupportException("ICSS-MCP-409-IMPACT_CONFIRM_REQUIRED", "停用前需要确认影响范围");
        }
        capability.put("status", StringUtils.hasText(targetStatus) ? targetStatus : "enabled");
        capability.put("changedAt", now());
        return capability;
    }

    public Map<String, Object> updateGraphMcpMapping(String mappingId, Map<String, Object> body) {
        return mapOf(
                "mappingId", mappingId,
                "status", "enabled",
                "capabilityIds", body.getOrDefault("capabilityIds", List.of()),
                "updatedAt", now());
    }

    public Map<String, Object> updateRoute(String routeId, Map<String, Object> body) {
        Map<String, Object> route = routes.computeIfAbsent(routeId, id -> mapOf(
                "routeId", id,
                "routeName", "placeholder route",
                "version", 1));
        String action = stringValue(body, "action");
        route.put("routeStatus", switch (action) {
            case "disable" -> "disabled";
            case "archive" -> "archived";
            case "rebuild" -> "reviewing";
            default -> "active";
        });
        route.put("version", ((Number) route.getOrDefault("version", 1)).intValue() + 1);
        return route;
    }

    public Map<String, Object> submitFeedback(Map<String, Object> body) {
        String rating = stringValue(body, "rating");
        boolean rebuildRequired = "invalid".equals(rating) || "partial".equals(rating);
        return mapOf(
                "evaluationId", uuid(),
                "rebuildRequired", rebuildRequired,
                "routeStatus", rebuildRequired ? "reviewing" : "active");
    }

    public Map<String, Object> placeholder(String status, String reason) {
        return mapOf("status", status, "reason", reason, "mock", true);
    }

    private Map<String, Object> createTrace(String traceId, String caseId, String projectId, boolean degraded, Map<String, Object> aiResult, Map<String, Object> mcpResult) {
        Object graphPaths = aiResult.getOrDefault("graphPaths", List.of());
        Object reasoningSteps = aiResult.getOrDefault("reasoningSteps", List.of());
        Object mcpCalls = List.of(mapOf(
                "callId", uuid(),
                "capabilityId", "cap-placeholder",
                "capabilityCode", "diagnostic.placeholder",
                "status", mcpResult.getOrDefault("status", degraded ? "timeout" : "success"),
                "requestSummary", "",
                "responseSummary", mcpResult.getOrDefault("summary", ""),
                "durationMs", degraded ? 5000 : 0));
        return mapOf(
                "traceId", traceId,
                "caseId", caseId,
                "routeId", "",
                "projectId", projectId,
                "status", aiResult.getOrDefault("status", degraded ? "partial" : "completed"),
                "graphPaths", graphPaths,
                "reasoningSteps", reasoningSteps,
                "mcpCalls", mcpCalls,
                "degraded", degraded,
                "failureReason", degraded ? "实时通道不可用，已降级" : null,
                "durationMs", degraded ? 5000 : 0);
    }

    private Map<String, Object> require(Map<String, Map<String, Object>> store, String id, String code, String message) {
        if (!StringUtils.hasText(id) || !store.containsKey(id)) {
            throw new SmartSupportException(code, message);
        }
        return store.get(id);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listValue(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private String stringValue(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

    private String now() {
        return OffsetDateTime.now().toString();
    }

    public static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
