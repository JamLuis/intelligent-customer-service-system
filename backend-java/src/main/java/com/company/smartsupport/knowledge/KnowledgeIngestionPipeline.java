package com.company.smartsupport.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.graph.GraphTaxonomyService;
import com.company.smartsupport.graph.Neo4jGraphRepository;
import com.company.smartsupport.graph.dto.GraphTaxonomyResponse;
import com.company.smartsupport.integration.AiServiceClient;
import com.company.smartsupport.modelconfig.KnowledgeModelConfigDto;
import com.company.smartsupport.modelconfig.KnowledgeModelConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class KnowledgeIngestionPipeline {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final KnowledgeRepository knowledgeRepository;
    private final GraphTaxonomyService graphTaxonomyService;
    private final AiServiceClient aiServiceClient;
    private final Neo4jGraphRepository neo4jGraphRepository;
    private final KnowledgeModelConfigService knowledgeModelConfigService;
    private final ObjectMapper objectMapper;

    public KnowledgeIngestionPipeline(
            KnowledgeRepository knowledgeRepository,
            GraphTaxonomyService graphTaxonomyService,
            AiServiceClient aiServiceClient,
            Neo4jGraphRepository neo4jGraphRepository,
            KnowledgeModelConfigService knowledgeModelConfigService,
            ObjectMapper objectMapper) {
        this.knowledgeRepository = knowledgeRepository;
        this.graphTaxonomyService = graphTaxonomyService;
        this.aiServiceClient = aiServiceClient;
        this.neo4jGraphRepository = neo4jGraphRepository;
        this.knowledgeModelConfigService = knowledgeModelConfigService;
        this.objectMapper = objectMapper;
    }

    public void run(String projectId, String sourceId) {
        KnowledgeRepository.SourceRecord source = knowledgeRepository.findSource(projectId, sourceId);
        try {
            knowledgeRepository.resetGeneratedArtifacts(projectId, sourceId);
            knowledgeRepository.updateSourceStage(projectId, sourceId, "parsing", "running", "pending", "pending", null);

            GraphTaxonomyResponse taxonomy = graphTaxonomyService.getTaxonomy(projectId);
            Map<String, Object> taxonomyPayload = objectMapper.convertValue(taxonomy, MAP_TYPE);
            Map<String, Object> parseResponse = aiServiceClient.parseKnowledge(Map.of(
                    "sourceId", source.sourceId(),
                    "sourceType", source.sourceType(),
                    "fileName", source.fileName(),
                    "graphCategoryId", source.graphCategoryId(),
                    "rawText", source.rawText() == null ? "" : source.rawText()))
                    .orElseThrow(() -> new SmartSupportException("ICSS-KG-502-AI_SERVICE_UNAVAILABLE", "AI Service parse 调用失败"));
            List<Map<String, Object>> blocks = objectList(parseResponse.get("blocks"));
            Map<String, String> persistedBlockIds = new LinkedHashMap<>();
            for (Map<String, Object> block : blocks) {
                String originalBlockId = text(block.get("blockId"));
                String persistedBlockId = knowledgeRepository.upsertBlock(projectId, source, block);
                persistedBlockIds.put(originalBlockId, persistedBlockId);
                block.put("blockId", persistedBlockId);
            }
            knowledgeRepository.completeLatestTask(sourceId, "parse", Map.of("blocks", blocks.size()));

            knowledgeRepository.createTask(sourceId, "extract");
            knowledgeRepository.updateSourceStage(projectId, sourceId, "extracted", "success", "running", "pending", null);
                KnowledgeModelConfigDto modelConfig = knowledgeModelConfigService.getConfig(projectId);
                Map<String, Object> extractRequest = new LinkedHashMap<>();
                extractRequest.put("sourceId", source.sourceId());
                extractRequest.put("fileName", source.fileName());
                extractRequest.put("graphCategoryId", source.graphCategoryId());
                extractRequest.put("blocks", blocks);
                extractRequest.put("taxonomy", taxonomyPayload);
                extractRequest.put("enableLlmExtract", modelConfig.extractEnabled());
                extractRequest.put("llmConfig", knowledgeModelConfigService.activeAiConfig(projectId));
                Map<String, Object> extractResponse = aiServiceClient.extractKnowledge(extractRequest)
                    .orElseThrow(() -> new SmartSupportException("ICSS-KG-502-AI_SERVICE_UNAVAILABLE", "AI Service extract 调用失败"));
            List<Map<String, Object>> candidateEntities = objectList(extractResponse.get("candidateEntities"));
            List<Map<String, Object>> candidateRelations = objectList(extractResponse.get("candidateRelations"));

            Map<String, EntityBuildItem> entityByTempId = new LinkedHashMap<>();
            List<Map<String, Object>> graphNodes = new ArrayList<>();
            for (Map<String, Object> candidate : candidateEntities) {
                String blockId = persistedBlockIds.getOrDefault(text(candidate.get("blockId")), text(candidate.get("blockId")));
                String candidateId = knowledgeRepository.insertCandidateEntity(projectId, source, candidate, blockId);
                String tempId = text(candidate.get("tempId"));
                EntityBuildItem item = new EntityBuildItem(
                        candidateId,
                        text(candidate.get("entityType")),
                        text(candidate.get("canonicalName")),
                        number(candidate.get("confidence")),
                        objectList(candidate.get("evidence")));
                if (!tempId.isBlank()) {
                    entityByTempId.put(tempId, item);
                }
                graphNodes.add(nodePayload(item));
            }

            List<Map<String, Object>> graphEdges = new ArrayList<>();
            for (Map<String, Object> relation : candidateRelations) {
                EntityBuildItem sourceItem = entityByTempId.get(text(relation.get("sourceTempId")));
                EntityBuildItem targetItem = entityByTempId.get(text(relation.get("targetTempId")));
                if (sourceItem == null || targetItem == null) {
                    continue;
                }
                String relationId = knowledgeRepository.insertCandidateRelation(projectId, source, relation, sourceItem.candidateId(), targetItem.candidateId());
                graphEdges.add(edgePayload(relationId, relation, sourceItem, targetItem));
            }
            knowledgeRepository.completeLatestTask(sourceId, "extract", Map.of("entities", graphNodes.size(), "relations", graphEdges.size()));

            knowledgeRepository.createTask(sourceId, "graph_build");
            knowledgeRepository.updateSourceStage(projectId, sourceId, "graph_ready", "success", "success", "running", null);
            KnowledgeRepository.GraphBuildRecord graph = knowledgeRepository.createDraftGraph(projectId, source, graphNodes, graphEdges);
            writeNeo4jDraft(projectId, source, graph.revisionId(), graphNodes, graphEdges);
            knowledgeRepository.completeLatestTask(sourceId, "graph_build", Map.of(
                    "graphId", graph.graphId(),
                    "revisionId", graph.revisionId(),
                    "nodes", graphNodes.size(),
                    "edges", graphEdges.size()));
            knowledgeRepository.updateSourceStage(projectId, sourceId, "graph_ready", "success", "success", "success", null);
        } catch (RuntimeException ex) {
            knowledgeRepository.updateSourceStage(projectId, sourceId, "failed", "failed", "failed", "failed", ex.getMessage());
            knowledgeRepository.failLatestTask(sourceId, "parse", ex.getMessage());
            knowledgeRepository.failLatestTask(sourceId, "extract", ex.getMessage());
            knowledgeRepository.failLatestTask(sourceId, "graph_build", ex.getMessage());
            throw ex;
        }
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

    private Map<String, Object> nodePayload(EntityBuildItem item) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", item.candidateId());
        node.put("entityId", item.candidateId());
        node.put("label", item.name());
        node.put("entityType", item.entityType());
        node.put("status", "draft");
        node.put("confidence", item.confidence());
        node.put("evidenceRefs", item.evidenceRefs());
        return node;
    }

    private Map<String, Object> edgePayload(String relationId, Map<String, Object> relation, EntityBuildItem source, EntityBuildItem target) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", relationId);
        edge.put("relationId", relationId);
        edge.put("source", source.candidateId());
        edge.put("target", target.candidateId());
        edge.put("sourceEntityType", source.entityType());
        edge.put("targetEntityType", target.entityType());
        edge.put("relationType", text(relation.get("relationType")));
        edge.put("label", text(relation.get("relationType")));
        edge.put("status", "draft");
        edge.put("confidence", number(relation.get("confidence")));
        edge.put("evidenceRefs", objectList(relation.get("evidence")));
        return edge;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
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

    private record EntityBuildItem(String candidateId, String entityType, String name, double confidence, List<Map<String, Object>> evidenceRefs) {
    }
}
