package com.company.smartsupport.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.graph.GraphTaxonomyService;
import com.company.smartsupport.graph.dto.GraphTaxonomyResponse;
import com.company.smartsupport.integration.AiServiceClient;
import com.company.smartsupport.modelconfig.KnowledgeModelConfigDto;
import com.company.smartsupport.modelconfig.KnowledgeModelConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ExtractStageService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final KnowledgeRepository knowledgeRepository;
    private final GraphTaxonomyService graphTaxonomyService;
    private final AiServiceClient aiServiceClient;
    private final KnowledgeModelConfigService knowledgeModelConfigService;
    private final ObjectMapper objectMapper;

    public ExtractStageService(
            KnowledgeRepository knowledgeRepository,
            GraphTaxonomyService graphTaxonomyService,
            AiServiceClient aiServiceClient,
            KnowledgeModelConfigService knowledgeModelConfigService,
            ObjectMapper objectMapper) {
        this.knowledgeRepository = knowledgeRepository;
        this.graphTaxonomyService = graphTaxonomyService;
        this.aiServiceClient = aiServiceClient;
        this.knowledgeModelConfigService = knowledgeModelConfigService;
        this.objectMapper = objectMapper;
    }

    public ExtractStageResult run(String projectId, KnowledgeRepository.SourceRecord source, List<Map<String, Object>> blocks, Map<String, String> persistedBlockIds) {
        GraphTaxonomyResponse taxonomy = graphTaxonomyService.getTaxonomy(projectId);
        Map<String, Object> taxonomyPayload = objectMapper.convertValue(taxonomy, MAP_TYPE);
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
        return new ExtractStageResult(graphNodes, graphEdges);
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

    public record ExtractStageResult(List<Map<String, Object>> graphNodes, List<Map<String, Object>> graphEdges) {
    }

    private record EntityBuildItem(String candidateId, String entityType, String name, double confidence, List<Map<String, Object>> evidenceRefs) {
    }
}
