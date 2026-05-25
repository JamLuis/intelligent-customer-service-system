package com.company.smartsupport.graph;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.company.smartsupport.common.PageResult;
import com.company.smartsupport.graph.dto.GraphAssetDetailDto;
import com.company.smartsupport.graph.dto.GraphAssetSummaryDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GraphAssetService {

    private final PgGraphMetadataRepository pgGraphMetadataRepository;
    private final Neo4jGraphRepository neo4jGraphRepository;
    private final ObjectMapper objectMapper;

    public GraphAssetService(PgGraphMetadataRepository pgGraphMetadataRepository, Neo4jGraphRepository neo4jGraphRepository, ObjectMapper objectMapper) {
        this.pgGraphMetadataRepository = pgGraphMetadataRepository;
        this.neo4jGraphRepository = neo4jGraphRepository;
        this.objectMapper = objectMapper;
    }

    public PageResult<GraphAssetSummaryDto> queryGraphAssets(
            String projectId,
            String graphCategoryId,
            String entityType,
            String relationType,
            String sourceId,
            int pageNo,
            int pageSize) {
        return PageResult.of(
                pgGraphMetadataRepository.listGraphAssets(projectId, graphCategoryId, entityType, relationType, sourceId),
                pageNo,
                pageSize);
    }

    public GraphAssetDetailDto getGraphAsset(String projectId, String graphId) {
        return pgGraphMetadataRepository.getGraphAsset(projectId, graphId);
    }

    public Map<String, Object> updateGraphDraft(String projectId, String graphId, Map<String, Object> body, String actorId) {
        GraphAssetDetailDto before = pgGraphMetadataRepository.getGraphAsset(projectId, graphId);
        Map<String, Object> result = pgGraphMetadataRepository.updateGraphDraft(projectId, graphId, body, actorId);
        GraphAssetDetailDto after = pgGraphMetadataRepository.getGraphAsset(projectId, graphId);
        syncNeo4jDraft(projectId, before, after);
        return result;
    }

    public Map<String, Object> deleteGraphAsset(String projectId, String graphId, String actorId) {
        GraphAssetDetailDto before = pgGraphMetadataRepository.getGraphAsset(projectId, graphId);
        Map<String, Object> result = pgGraphMetadataRepository.deleteGraphAsset(projectId, graphId, actorId);
        neo4jGraphRepository.deleteGraphRevision(projectId, before.activeRevisionId());
        return result;
    }

    private void syncNeo4jDraft(String projectId, GraphAssetDetailDto before, GraphAssetDetailDto after) {
        List<String> previousRelationIds = before.edges().stream()
                .map(edge -> text(edge.get("relationId"), text(edge.get("id"), "")))
                .filter(value -> !value.isBlank())
                .toList();
        neo4jGraphRepository.deleteRelations(projectId, previousRelationIds);
        for (Map<String, Object> node : after.nodes()) {
            neo4jGraphRepository.upsertKnowledgeEntity(Map.ofEntries(
                    Map.entry("projectId", projectId),
                    Map.entry("entityType", text(node.get("entityType"), text(node.get("type"), text(nested(node, "data", "entityType"), "entity")))),
                    Map.entry("entityId", text(node.get("entityId"), text(node.get("id"), ""))),
                    Map.entry("entityName", text(node.get("label"), text(nested(node, "data", "label"), text(node.get("id"), "")))),
                    Map.entry("aliases", List.of()),
                    Map.entry("graphCategoryIds", List.of(after.graphCategoryId())),
                    Map.entry("sourceRefs", after.sourceRefs()),
                    Map.entry("evidenceRefs", evidenceRefs(node.getOrDefault("evidenceRefs", nested(node, "data", "evidenceRefs")))),
                    Map.entry("confidence", number(node.get("confidence"), 0.0)),
                    Map.entry("status", text(node.get("status"), text(nested(node, "data", "status"), "draft"))),
                    Map.entry("revisionId", after.activeRevisionId()),
                    Map.entry("properties", node.getOrDefault("properties", Map.of()))));
        }
        for (Map<String, Object> edge : after.edges()) {
            String sourceId = text(edge.get("source"), "");
            String targetId = text(edge.get("target"), "");
            Map<String, Object> source = findNode(after.nodes(), sourceId);
            Map<String, Object> target = findNode(after.nodes(), targetId);
            if (source.isEmpty() || target.isEmpty()) {
                continue;
            }
            neo4jGraphRepository.upsertRelation(Map.ofEntries(
                    Map.entry("projectId", projectId),
                    Map.entry("sourceEntityType", text(source.get("entityType"), text(nested(source, "data", "entityType"), "entity"))),
                    Map.entry("sourceEntityId", sourceId),
                    Map.entry("targetEntityType", text(target.get("entityType"), text(nested(target, "data", "entityType"), "entity"))),
                    Map.entry("targetEntityId", targetId),
                    Map.entry("relationId", text(edge.get("relationId"), text(edge.get("id"), ""))),
                    Map.entry("relationType", relationType(edge)),
                    Map.entry("sourceRefs", after.sourceRefs()),
                    Map.entry("evidenceRefs", evidenceRefs(edge.getOrDefault("evidenceRefs", nested(edge, "data", "evidenceRefs")))),
                    Map.entry("confidence", number(edge.get("confidence"), 0.0)),
                    Map.entry("status", text(edge.get("status"), text(nested(edge, "data", "status"), "draft"))),
                    Map.entry("revisionId", after.activeRevisionId()),
                    Map.entry("properties", edge.getOrDefault("properties", Map.of()))));
        }
    }

    private Map<String, Object> findNode(List<Map<String, Object>> nodes, String nodeId) {
        return nodes.stream()
                .filter(node -> nodeId.equals(text(node.get("entityId"), text(node.get("id"), ""))))
                .findFirst()
                .orElse(Map.of());
    }

    private String relationType(Map<String, Object> edge) {
        return text(edge.get("relationType"), text(edge.get("type"), text(edge.get("label"), text(nested(edge, "data", "relationType"), "RELATED_TO"))));
    }

    private Object nested(Map<String, Object> source, String parent, String child) {
        Object value = source.get(parent);
        if (value instanceof Map<?, ?> map) {
            return map.get(child);
        }
        return null;
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private List<String> evidenceRefs(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(item -> {
            if (item instanceof Map<?, ?> map) {
                try {
                    return objectMapper.writeValueAsString(map);
                } catch (JsonProcessingException ex) {
                    return String.valueOf(map);
                }
            }
            return String.valueOf(item);
        }).toList();
    }
}
