package com.company.smartsupport.knowledge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.integration.AiServiceClient;

@Service
public class ParseStageService {

    private final KnowledgeRepository knowledgeRepository;
    private final AiServiceClient aiServiceClient;

    public ParseStageService(KnowledgeRepository knowledgeRepository, AiServiceClient aiServiceClient) {
        this.knowledgeRepository = knowledgeRepository;
        this.aiServiceClient = aiServiceClient;
    }

    public ParseStageResult run(String projectId, KnowledgeRepository.SourceRecord source) {
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
        return new ParseStageResult(blocks, persistedBlockIds);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record ParseStageResult(List<Map<String, Object>> blocks, Map<String, String> persistedBlockIds) {
    }
}
