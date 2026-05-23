package com.company.smartsupport.graph;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.smartsupport.common.RequestContext;
import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.graph.cache.GraphCacheService;
import com.company.smartsupport.graph.dto.GraphObjectActionRequest;
import com.company.smartsupport.graph.dto.GraphObjectActionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FreezeService {

    private final Neo4jGraphRepository neo4jGraphRepository;
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RequestContext requestContext;
    private final GraphCacheService graphCacheService;

    public FreezeService(
            Neo4jGraphRepository neo4jGraphRepository,
            JdbcClient jdbcClient,
            ObjectMapper objectMapper,
            RequestContext requestContext,
            GraphCacheService graphCacheService) {
        this.neo4jGraphRepository = neo4jGraphRepository;
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.requestContext = requestContext;
        this.graphCacheService = graphCacheService;
    }

    @Transactional
    public GraphObjectActionResponse applyEntityAction(String projectId, String entityId, GraphObjectActionRequest request) {
        return applyAction("entity", entityId, request,
                neo4jGraphRepository.findEntityStatus(projectId, entityId),
                (from, to) -> neo4jGraphRepository.updateEntityStatus(projectId, entityId, from, to, request.reason()),
                projectId);
    }

    @Transactional
    public GraphObjectActionResponse applyRelationAction(String projectId, String relationId, GraphObjectActionRequest request) {
        return applyAction("relation", relationId, request,
                neo4jGraphRepository.findRelationStatus(projectId, relationId),
                (from, to) -> neo4jGraphRepository.updateRelationStatus(projectId, relationId, from, to, request.reason()),
                projectId);
    }

    private GraphObjectActionResponse applyAction(
            String objectType,
            String objectId,
            GraphObjectActionRequest request,
            Optional<String> currentStatus,
            StatusUpdater updater,
            String projectId) {
        String action = request == null ? null : request.action();
        if (!"freeze".equals(action) && !"unfreeze".equals(action)) {
            throw new SmartSupportException("ICSS-COMMON-400-INVALID_PARAMETER", "action 仅允许 freeze 或 unfreeze");
        }

        requestContext.requirePermission("freeze".equals(action) ? "graph:freeze" : "graph:unfreeze",
                "ICSS-KG-403-FREEZE_FORBIDDEN", "无冻结或解冻图谱对象权限");

        String from = currentStatus.orElseThrow(() -> new SmartSupportException("ICSS-KG-404-GRAPH_NOT_FOUND", "图谱对象不存在"));
        String to = "freeze".equals(action) ? "frozen" : "published";
        String expected = "freeze".equals(action) ? "published" : "frozen";
        if (!expected.equals(from)) {
            throw new SmartSupportException("ICSS-KG-409-FROZEN_NODE",
                    "freeze 仅允许 published -> frozen，unfreeze 仅允许 frozen -> published；当前状态: " + from);
        }

        updater.update(from, to);
        graphCacheService.invalidateOnGraphMutation(projectId, action);
        OffsetDateTime operatedAt = OffsetDateTime.now();
        insertAudit(projectId, objectType, objectId, action, from, to, request.reason(), operatedAt);
        return new GraphObjectActionResponse(objectType, objectId, action, from, to, request.reason(), operatedAt);
    }

    private void insertAudit(String projectId, String objectType, String objectId, String action,
            String from, String to, String reason, OffsetDateTime operatedAt) {
        jdbcClient.sql("""
                INSERT INTO audit_log (
                    tenant_id,
                    project_id,
                    actor_id,
                    actor_role,
                    action_type,
                    object_type,
                    object_id,
                    summary,
                    detail_payload,
                    created_at
                ) VALUES (
                    'default',
                    :projectId,
                    :actorId,
                    NULL,
                    :actionType,
                    :objectType,
                    CAST(:objectUuid AS uuid),
                    :summary,
                    CAST(:detailPayload AS jsonb),
                    :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("actorId", requestContext.actorId())
                .param("actionType", "graph:" + action)
                .param("objectType", "graph_" + objectType)
                .param("objectUuid", uuidOrNull(objectId))
                .param("summary", action + " graph " + objectType + " " + objectId)
                .param("detailPayload", toJson(Map.of(
                        "objectId", objectId,
                        "action", action,
                        "previousStatus", from,
                        "currentStatus", to,
                        "reason", reason == null ? "" : reason,
                        "operatedAt", operatedAt.toString())))
                .param("createdAt", operatedAt)
                .update();
    }

    private String uuidOrNull(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "审计详情序列化失败");
        }
    }

    @FunctionalInterface
    private interface StatusUpdater {
        void update(String from, String to);
    }
}