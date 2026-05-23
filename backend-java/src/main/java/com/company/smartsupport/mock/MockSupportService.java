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

@Service
public class MockSupportService {

    private final Map<String, Map<String, Object>> sessions = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> cases = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> traces = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> sources = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> capabilities = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> routes = new LinkedHashMap<>();
    private final AiServiceClient aiServiceClient;
    private final McpServerClient mcpServerClient;

    public MockSupportService(AiServiceClient aiServiceClient, McpServerClient mcpServerClient) {
        this.aiServiceClient = aiServiceClient;
        this.mcpServerClient = mcpServerClient;
        seedCapabilities();
        seedRoute();
    }

    public Map<String, Object> createSession(Map<String, Object> body, String projectId) {
        String questionText = stringValue(body, "questionText");
        if (!StringUtils.hasText(questionText)) {
            throw new SmartSupportException("ICSS-DIAG-400-QUESTION_EMPTY", "问题不能为空");
        }
        String sessionId = uuid();
        boolean contextRequired = "context_required".equals(body.get("mockScenario")) || questionText.contains("缺少");
        Map<String, Object> session = mapOf(
                "sessionId", sessionId,
                "projectId", projectId,
                "status", contextRequired ? "waiting_context" : "ready",
                "issueCategory", questionText.contains("日志") ? "log" : "alarm",
                "entities", List.of(mapOf("type", "deviceId", "value", body.getOrDefault("deviceId", "TC-003"), "confidence", 0.92)),
                "missingFields", contextRequired ? List.of("deviceId", "timeRange") : List.of(),
                "createdAt", now());
        sessions.put(sessionId, session);
        return session;
    }

    public Map<String, Object> updateSessionContext(String sessionId, Map<String, Object> body) {
        Map<String, Object> session = require(sessions, sessionId, "ICSS-DIAG-404-SESSION_NOT_FOUND", "会话不存在");
        session.put("status", "ready");
        session.put("missingFields", List.of());
        session.put("followUpAnswer", body.getOrDefault("followUpAnswer", "已补充设备和时间范围"));
        return session;
    }

    public Map<String, Object> startDiagnosis(Map<String, Object> body, String projectId) {
        String sessionId = stringValue(body, "sessionId");
        Map<String, Object> session = require(sessions, sessionId, "ICSS-DIAG-404-SESSION_NOT_FOUND", "会话不存在");
        if ("waiting_context".equals(session.get("status"))) {
            throw new SmartSupportException("ICSS-DIAG-409-CONTEXT_REQUIRED", "诊断上下文不足，请补充设备或时间范围");
        }
        String caseId = uuid();
        String traceId = uuid();
        boolean degraded = "mcp_degraded".equals(body.get("mockScenario"));
        Map<String, Object> aiResult = aiServiceClient.runDiagnosis(mapOf(
            "sessionId", sessionId,
            "questionText", session.getOrDefault("questionText", ""),
            "projectId", projectId,
            "mockScenario", body.get("mockScenario"))).orElse(Map.of());
        Map<String, Object> mcpResult = mcpServerClient.invoke("device.getStatus", mapOf(
            "projectId", projectId,
            "deviceId", "TC-003",
            "mockScenario", body.get("mockScenario"))).orElse(Map.of());
        Map<String, Object> trace = createTrace(traceId, caseId, projectId, degraded, aiResult, mcpResult);
        Map<String, Object> diagnosticCase = mapOf(
                "caseId", caseId,
                "sessionId", sessionId,
                "projectId", projectId,
                "status", "concluded",
            "rootCause", aiResult.getOrDefault("rootCause", degraded ? "实时日志查询超时，已基于静态知识给出降级诊断" : "告警规则阈值配置与设备状态不一致，导致报警未触发"),
            "confidenceScore", aiResult.getOrDefault("confidenceScore", degraded ? 71.5 : 88.0),
                "evidenceItems", List.of(
                        mapOf("source", "graph", "summary", "设备 TC-003 绑定告警规则 AR-17", "confidence", 0.91),
                mapOf("source", "mcp", "summary", mcpResult.getOrDefault("summary", degraded ? "log.searchErrors timeout" : "device.getStatus 返回在线"), "confidence", degraded ? 0.4 : 0.87)),
            "recommendedActions", aiResult.getOrDefault("recommendedActions", List.of(mapOf("actionCode", "CHECK_ALARM_RULE", "riskLevel", "L1", "summary", "核对告警规则阈值与启用状态"))),
                "latestTrace", mapOf("traceId", traceId, "status", trace.get("status"), "degraded", degraded));
        cases.put(caseId, diagnosticCase);
        traces.put(traceId, trace);
        session.put("status", "analyzed");
        return mapOf("caseId", caseId, "traceId", traceId, "caseStatus", "concluded", "traceStatus", trace.get("status"), "sseUrl", "/api/v1/diagnosis/cases/" + caseId + "/events");
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

    public Map<String, Object> createKnowledgeSource(Map<String, Object> body, String projectId) {
        String sourceType = stringValue(body, "sourceType");
        if (!List.of("doc", "docx", "xls", "xlsx", "pdf", "jpg", "png", "text").contains(sourceType)) {
            throw new SmartSupportException("ICSS-KNOW-400-UNSUPPORTED_SOURCE_TYPE", "知识源类型不支持");
        }
        String sourceId = uuid();
        Map<String, Object> source = mapOf(
                "sourceId", sourceId,
                "projectId", projectId,
                "sourceType", sourceType,
                "fileName", body.getOrDefault("fileName", sourceType.equals("text") ? "raw-text.txt" : "upload." + sourceType),
                "status", "graph_ready",
                "parserStatus", "success",
                "extractStatus", "success",
                "graphBuildStatus", "success",
                "createdAt", now());
            aiServiceClient.ingestKnowledge(mapOf(
                "sourceId", sourceId,
                "sourceType", sourceType,
                "rawText", body.get("rawText"),
                "projectId", projectId)).ifPresent(result -> {
                    source.put("parserStatus", result.getOrDefault("parserStatus", source.get("parserStatus")));
                    source.put("extractStatus", result.getOrDefault("extractStatus", source.get("extractStatus")));
                    source.put("graphBuildStatus", result.getOrDefault("graphBuildStatus", source.get("graphBuildStatus")));
                    source.put("aiTasks", result.getOrDefault("tasks", List.of()));
                });
        sources.put(sourceId, source);
        return source;
    }

    public PageResult<Map<String, Object>> listKnowledgeSources(int pageNo, int pageSize) {
        return PageResult.of(new ArrayList<>(sources.values()), pageNo, pageSize);
    }

    public PageResult<Map<String, Object>> ingestionTasks(String sourceId, int pageNo, int pageSize) {
        require(sources, sourceId, "ICSS-KNOW-404-SOURCE_NOT_FOUND", "知识源不存在");
        List<Map<String, Object>> tasks = List.of(
                mapOf("taskId", uuid(), "taskType", "parse", "status", "success", "progress", 100, "resultPayload", mapOf("pages", 12), "errorMessage", null, "startedAt", now(), "completedAt", now()),
                mapOf("taskId", uuid(), "taskType", "graph_build", "status", "success", "progress", 100, "resultPayload", mapOf("nodes", 28, "edges", 41), "errorMessage", null, "startedAt", now(), "completedAt", now()));
        return PageResult.of(tasks, pageNo, pageSize);
    }

    public Map<String, Object> retryKnowledgeSource(String sourceId) {
        require(sources, sourceId, "ICSS-KNOW-404-SOURCE_NOT_FOUND", "知识源不存在");
        return mapOf("sourceId", sourceId, "taskId", uuid(), "status", "running", "progress", 0);
    }

    public PageResult<Map<String, Object>> graphAssets(int pageNo, int pageSize) {
        Map<String, Object> aiGraphs = aiServiceClient.queryGraphs(mapOf("projectId", "P001", "keyword", "alarm")).orElse(Map.of());
        Object aiItems = aiGraphs.get("items");
        if (aiItems instanceof List<?> list && !list.isEmpty()) {
            return PageResult.of(castList(list), pageNo, pageSize);
        }
        List<Map<String, Object>> items = List.of(
                mapOf("graphId", "graph-device-alarm", "graphName", "设备告警关系子图", "nodes", List.of(mapOf("id", "device:TC-003", "label", "设备 TC-003"), mapOf("id", "rule:AR-17", "label", "告警规则 AR-17")), "edges", List.of(mapOf("source", "device:TC-003", "target", "rule:AR-17", "type", "BOUND_TO")), "sourceRefs", List.of("knowledge:alarm-rule-doc"), "confidence", 0.89, "activeRevisionId", "rev-3"));
        return PageResult.of(items, pageNo, pageSize);
    }

    public Map<String, Object> graphAssetDetail(String graphId) {
        return mapOf("graphId", graphId, "graphName", "设备告警关系子图", "status", "published", "neo4jGraphRef", "neo4j://graph/" + graphId, "sourceRefs", List.of("knowledge:alarm-rule-doc"), "revisions", List.of(mapOf("revisionId", "rev-3", "status", "published")), "activeRevisionId", "rev-3");
    }

    public Map<String, Object> updateGraphDraft(String graphId, Map<String, Object> body) {
        return mapOf("graphId", graphId, "draftVersion", "draft-" + System.currentTimeMillis(), "status", "reviewing", "editReason", body.getOrDefault("editReason", "mock draft update"));
    }

    public Map<String, Object> graphAction(String graphId, Map<String, Object> body) {
        String action = stringValue(body, "action");
        return mapOf("graphId", graphId, "graphStatus", "rollback".equals(action) ? "rolled_back" : "published", "activeVersion", body.getOrDefault("targetVersion", "rev-3"));
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
        return mapOf("capabilityId", capabilityId, "capabilityCode", capability.get("capabilityCode"), "impactRoutes", List.of(mapOf("routeId", "route-device-offline", "routeName", "设备离线诊断路径", "projectId", "P001", "issueCategory", "device")), "recentCallCount7d", 128);
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
        return mapOf("mappingId", mappingId, "status", "enabled", "capabilityIds", body.getOrDefault("capabilityIds", List.of("cap-device-status")), "updatedAt", now());
    }

    public Map<String, Object> updateRoute(String routeId, Map<String, Object> body) {
        Map<String, Object> route = routes.computeIfAbsent(routeId, id -> mapOf("routeId", id, "routeName", "Mock 诊断路径", "version", 1));
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
        return mapOf("evaluationId", uuid(), "rebuildRequired", rebuildRequired, "routeStatus", rebuildRequired ? "reviewing" : "active");
    }

    public Map<String, Object> placeholder(String status, String reason) {
        return mapOf("status", status, "reason", reason, "mock", true);
    }

        private Map<String, Object> createTrace(String traceId, String caseId, String projectId, boolean degraded, Map<String, Object> aiResult, Map<String, Object> mcpResult) {
        Object graphPaths = aiResult.getOrDefault("graphPaths", List.of(mapOf("nodes", List.of("device:TC-003", "alarmRule:AR-17"), "relation", "BOUND_TO", "sourceRef", "graph-device-alarm")));
        Object reasoningSteps = aiResult.getOrDefault("reasoningSteps", List.of(
            mapOf("stepId", uuid(), "stepOrder", 1, "stepType", "graph", "stepName", "查询设备告警关系", "status", "success", "inputSummary", "deviceId=TC-003", "outputSummary", "命中告警规则 AR-17", "durationMs", 42),
            mapOf("stepId", uuid(), "stepOrder", 2, "stepType", "mcp", "stepName", "查询设备状态", "status", degraded ? "failed" : "success", "inputSummary", "device.getStatus", "outputSummary", degraded ? "timeout" : "online", "durationMs", degraded ? 5000 : 87)));
        return mapOf(
                "traceId", traceId,
                "caseId", caseId,
                "routeId", "route-device-alarm",
                "projectId", projectId,
            "status", aiResult.getOrDefault("status", degraded ? "partial" : "completed"),
            "graphPaths", graphPaths,
            "reasoningSteps", reasoningSteps,
            "mcpCalls", List.of(mapOf("callId", uuid(), "capabilityId", "cap-device-status", "capabilityCode", "device.getStatus", "status", mcpResult.getOrDefault("status", degraded ? "timeout" : "success"), "requestSummary", "deviceId=TC-003", "responseSummary", mcpResult.getOrDefault("summary", degraded ? "查询超时" : "设备在线，心跳正常"), "durationMs", degraded ? 5000 : 87)),
                "degraded", degraded,
                "failureReason", degraded ? "log.searchErrors timeout，已降级为静态知识诊断" : null,
                "durationMs", degraded ? 5280 : 156);
    }

    private void seedCapabilities() {
        addCapability("cap-device-status", "device.getStatus", "设备状态查询", "device", "L0", "enabled");
        addCapability("cap-alarm-rules", "alarm.getRules", "告警规则查询", "alarm", "L0", "enabled");
        addCapability("cap-config-snapshot", "config.getSnapshot", "配置快照查询", "config", "L0", "enabled");
        addCapability("cap-log-errors", "log.searchErrors", "错误日志查询", "log", "L0", "enabled");
        addCapability("cap-statistics-rebuild", "statistics.rebuild", "统计重算", "statistics", "L4", "draft");
    }

    private void addCapability(String id, String code, String name, String category, String riskLevel, String status) {
        capabilities.put(id, mapOf("capabilityId", id, "capabilityCode", code, "capabilityName", name, "category", category, "riskLevel", riskLevel, "status", status, "lastHealthStatus", "healthy", "boundary", "V0.1 mock capability"));
    }

    private void seedRoute() {
        routes.put("route-device-alarm", mapOf("routeId", "route-device-alarm", "routeName", "设备告警诊断路径", "routeStatus", "active", "version", 1));
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
            return castList(list);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(List<?> list) {
        return (List<Map<String, Object>>) list;
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
