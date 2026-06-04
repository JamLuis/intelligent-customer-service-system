package com.company.smartsupport.chat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.graph.GraphSearchService;
import com.company.smartsupport.integration.AiServiceClient;
import com.company.smartsupport.modelconfig.ModelProfileDto;
import com.company.smartsupport.modelconfig.ModelProfileService;

@Service
public class ChatAnswerService {

    private final GraphSearchService graphSearchService;
    private final ModelProfileService modelProfileService;
    private final AiServiceClient aiServiceClient;

    public ChatAnswerService(GraphSearchService graphSearchService, ModelProfileService modelProfileService, AiServiceClient aiServiceClient) {
        this.graphSearchService = graphSearchService;
        this.modelProfileService = modelProfileService;
        this.aiServiceClient = aiServiceClient;
    }

    public Map<String, Object> answer(String projectId, Map<String, Object> body) {
        String questionText = text(body.get("questionText"));
        if (questionText.isBlank()) {
            throw new SmartSupportException("ICSS-DIAG-400-QUESTION_EMPTY", "问题不能为空");
        }
        ModelProfileDto profile = modelProfileService.activeProfile(projectId, ModelProfileService.PURPOSE_CHAT_ANSWER);
        Map<String, Object> chatAiConfig = modelProfileService.activeAiConfig(projectId, ModelProfileService.PURPOSE_CHAT_ANSWER);
        boolean forceGraphGrounding = booleanValue(body.get("forceGraphGrounding"), false) || profile.forceGraphGrounding();
        Map<String, Object> searchRequest = new LinkedHashMap<>();
        searchRequest.put("questionText", questionText);
        searchRequest.put("keywords", body.getOrDefault("keywords", List.of()));
        searchRequest.put("limit", intValue(body.get("limit"), 8));
        searchRequest.put("recencyAware", body.getOrDefault("recencyAware", false));
        applyQueryEmbedding(searchRequest, questionText, chatAiConfig);
        Map<String, Object> retrieval = graphSearchService.searchDiagnosis(projectId, searchRequest);
        Map<String, Object> aiRequest = new LinkedHashMap<>();
        aiRequest.put("questionText", questionText);
        aiRequest.put("forceGraphGrounding", forceGraphGrounding);
        aiRequest.put("llmConfig", chatAiConfig);
        aiRequest.put("vectorEvidence", retrieval.getOrDefault("vectorEvidence", List.of()));
        aiRequest.put("graphPaths", retrieval.getOrDefault("graphPaths", List.of()));
        aiRequest.put("matchedEntities", retrieval.getOrDefault("matchedEntities", List.of()));
        Map<String, Object> answer = aiServiceClient.answerChat(aiRequest)
                .orElseGet(() -> fallbackAnswer(questionText, retrieval, profile, forceGraphGrounding, true));
        Map<String, Object> response = new LinkedHashMap<>(answer);
        response.put("retrieval", retrieval);
        response.put("modelProfile", Map.of(
                "purpose", profile.purpose(),
                "providerMode", profile.providerMode(),
                "profileKey", profile.profileKey(),
                "displayName", profile.displayName(),
                "model", profile.model(),
                "forceGraphGrounding", forceGraphGrounding));
        response.putIfAbsent("degraded", false);
        response.putIfAbsent("forceGraphGrounding", forceGraphGrounding);
        return response;
    }

    private void applyQueryEmbedding(Map<String, Object> searchRequest, String questionText, Map<String, Object> aiConfig) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("blocks", List.of(Map.of("blockId", "query", "text", questionText)));
        request.put("llmConfig", aiConfig);
        aiServiceClient.embedKnowledge(request).ifPresent(response -> {
            List<?> embeddings = response.get("embeddings") instanceof List<?> list ? list : List.of();
            if (embeddings.isEmpty() || !(embeddings.getFirst() instanceof Map<?, ?> first)) {
                return;
            }
            Object vector = first.get("vector");
            if (vector instanceof List<?> list && !list.isEmpty()) {
                searchRequest.put("queryVector", list);
                searchRequest.put("embeddingModel", response.getOrDefault("embeddingModel", aiConfig.get("embeddingModel")));
                searchRequest.put("embeddingVersion", response.getOrDefault("embeddingVersion", "v1"));
            }
        });
    }

    private Map<String, Object> fallbackAnswer(String questionText, Map<String, Object> retrieval, ModelProfileDto profile, boolean forceGraphGrounding, boolean degraded) {
        List<?> evidence = retrieval.get("vectorEvidence") instanceof List<?> list ? list : List.of();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("answer", evidence.isEmpty()
                ? "知识库中没有检索到足够证据，暂时不能基于关系图谱回答该问题。"
                : "已检索到知识库证据，但对话模型不可用。请先查看右侧证据与来源块，再判断关系是否正确。");
        response.put("confidence", evidence.isEmpty() ? 0.0 : 0.35);
        response.put("evidenceRefs", evidence);
        response.put("graphPaths", retrieval.getOrDefault("graphPaths", List.of()));
        response.put("missingContext", List.of());
        response.put("cannotAnswerReason", evidence.isEmpty() ? "NO_KNOWLEDGE_EVIDENCE" : "MODEL_UNAVAILABLE");
        response.put("modelProfileId", profile.profileKey());
        response.put("forceGraphGrounding", forceGraphGrounding);
        response.put("degraded", degraded);
        return response;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}