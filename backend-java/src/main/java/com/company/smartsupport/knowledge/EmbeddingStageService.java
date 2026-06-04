package com.company.smartsupport.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.integration.AiServiceClient;
import com.company.smartsupport.modelconfig.KnowledgeModelConfigService;

@Service
public class EmbeddingStageService {

    private final KnowledgeRepository knowledgeRepository;
    private final AiServiceClient aiServiceClient;
    private final KnowledgeModelConfigService knowledgeModelConfigService;

    public EmbeddingStageService(
            KnowledgeRepository knowledgeRepository,
            AiServiceClient aiServiceClient,
            KnowledgeModelConfigService knowledgeModelConfigService) {
        this.knowledgeRepository = knowledgeRepository;
        this.aiServiceClient = aiServiceClient;
        this.knowledgeModelConfigService = knowledgeModelConfigService;
    }

    public void embedBlocks(String projectId, List<Map<String, Object>> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        List<Map<String, Object>> payloadBlocks = new ArrayList<>();
        for (Map<String, Object> block : blocks) {
            String text = text(block.get("normalizedText"));
            if (text.isBlank()) {
                text = text(block.get("rawText"));
            }
            if (text.isBlank()) {
                continue;
            }
            payloadBlocks.add(Map.of("blockId", text(block.get("blockId")), "text", text));
        }
        if (payloadBlocks.isEmpty()) {
            return;
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("blocks", payloadBlocks);
        request.put("llmConfig", knowledgeModelConfigService.activeAiConfig(projectId));
        Map<String, Object> response = aiServiceClient.embedKnowledge(request)
                .orElseThrow(() -> new SmartSupportException("ICSS-KG-502-AI_SERVICE_UNAVAILABLE", "AI Service embedding 调用失败"));
        String embeddingModel = text(response.get("embeddingModel"));
        String embeddingVersion = text(response.get("embeddingVersion"));
        int embeddingDim = response.get("embeddingDim") instanceof Number number ? number.intValue() : 0;
        for (Map<String, Object> item : objectList(response.get("embeddings"))) {
            knowledgeRepository.updateBlockEmbedding(projectId, text(item.get("blockId")), doubleList(item.get("vector")), embeddingModel, embeddingVersion, embeddingDim);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<Double> doubleList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::doubleValue)
                .toList();
    }
}
