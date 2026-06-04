package com.company.smartsupport.knowledge;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.company.smartsupport.graph.Neo4jGraphRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GraphBuildStageService {

    private final KnowledgeRepository knowledgeRepository;
    private final Neo4jGraphRepository neo4jGraphRepository;
    private final ObjectMapper objectMapper;

    public GraphBuildStageService(
            KnowledgeRepository knowledgeRepository,
            Neo4jGraphRepository neo4jGraphRepository,
            ObjectMapper objectMapper) {
        this.knowledgeRepository = knowledgeRepository;
        this.neo4jGraphRepository = neo4jGraphRepository;
        this.objectMapper = objectMapper;
    }

    public KnowledgeRepository.GraphBuildRecord run(String projectId, KnowledgeRepository.SourceRecord source, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        KnowledgeRepository.GraphBuildRecord graph = knowledgeRepository.createDraftGraph(projectId, source, nodes, edges);
        writeNeo4jDraft(projectId, source, graph.revisionId(), nodes, edges);
        return graph;
    }

    private void writeNeo4jDraft(String projectId, KnowledgeRepository.SourceRecord source, String revisionId, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        for (Map<String, Object> node : nodes) {
            neo4jGraphRepository.upsertKnowledgeEntity(Map.ofEntries(
                    Map.entry("projectId", projectId),
                    Map.entry("entityType", text(node.get("entityType"))),
                    Map.entry("entityId", text(node.get("entityId"))),
                    Map.entry("entityName", text(node.get("label"))),
                    Map.entry("aliases", List.of()),
                    Map.entry("graphCategoryIds", List.of(source.graphCategoryId())),
                    Map.entry("sourceRefs", List.of(source.sourceId())),
                    Map.entry("evidenceRefs", neo4jEvidenceRefs(node.get("evidenceRefs"))),
                    Map.entry("confidence", number(node.get("confidence"))),
                    Map.entry("status", "draft"),
                    Map.entry("revisionId", revisionId),
                    Map.entry("properties", "{}")));
        }
        for (Map<String, Object> edge : edges) {
            neo4jGraphRepository.upsertRelation(Map.ofEntries(
                    Map.entry("projectId", projectId),
                    Map.entry("sourceEntityType", text(edge.get("sourceEntityType"))),
                    Map.entry("sourceEntityId", text(edge.get("source"))),
                    Map.entry("targetEntityType", text(edge.get("targetEntityType"))),
                    Map.entry("targetEntityId", text(edge.get("target"))),
                    Map.entry("relationId", text(edge.get("relationId"))),
                    Map.entry("relationType", text(edge.get("relationType"))),
                    Map.entry("sourceRefs", List.of(source.sourceId())),
                    Map.entry("evidenceRefs", neo4jEvidenceRefs(edge.get("evidenceRefs"))),
                    Map.entry("confidence", number(edge.get("confidence"))),
                    Map.entry("status", "draft"),
                    Map.entry("revisionId", revisionId),
                    Map.entry("properties", "{}")));
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private List<String> neo4jEvidenceRefs(Object value) {
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
