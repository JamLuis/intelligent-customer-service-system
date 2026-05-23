package com.company.smartsupport.graph;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.company.smartsupport.graph.HybridRetrievalService.HybridRetrievalRequest;
import com.company.smartsupport.graph.dto.BudgetUsage;
import com.company.smartsupport.graph.dto.HybridEvidenceDto;
import com.company.smartsupport.graph.dto.TraversalBudget;

@Service
public class GraphSearchService {

    private final TraversalBudgetExecutor traversalBudgetExecutor;
    private final HybridRetrievalService hybridRetrievalService;

    public GraphSearchService(TraversalBudgetExecutor traversalBudgetExecutor, HybridRetrievalService hybridRetrievalService) {
        this.traversalBudgetExecutor = traversalBudgetExecutor;
        this.hybridRetrievalService = hybridRetrievalService;
    }

    public Map<String, Object> searchDiagnosis(String projectId, Map<String, Object> request) {
        String questionText = text(request.get("questionText"));
        TraversalBudget appliedBudget = traversalBudgetExecutor.apply(parseBudget(request.get("traversalBudget")));
        List<String> keywords = stringList(request.get("keywords"));
        List<Double> queryVector = doubleList(request.get("queryVector"));
        String embeddingModel = textOrDefault(request.get("embeddingModel"), "text-embedding-v4");
        String embeddingVersion = textOrDefault(request.get("embeddingVersion"), "v1");
        boolean recencyAware = Boolean.TRUE.equals(request.get("recencyAware"));

        List<HybridEvidenceDto> vectorEvidence = hybridRetrievalService.retrieve(new HybridRetrievalRequest(
                projectId,
                questionText,
                keywords,
                queryVector,
                embeddingModel,
                embeddingVersion,
                recencyAware,
                intValue(request.get("limit"), 20)));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("matchedEntities", List.of());
        response.put("graphPaths", List.of());
        response.put("sourceEvidence", List.of());
        response.put("vectorEvidence", vectorEvidence);
        response.put("suggestedMcpCapabilities", List.of());
        response.put("confidence", vectorEvidence.isEmpty() ? 0 : vectorEvidence.getFirst().hybridScore());
        response.put("degraded", false);
        response.put("queryId", java.util.UUID.randomUUID().toString());
        response.put("embeddingModel", embeddingModel);
        response.put("embeddingVersion", embeddingVersion);
        response.put("budgetUsage", new BudgetUsage(0, 0, false, appliedBudget));
        return response;
    }

    private TraversalBudget parseBudget(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return TraversalBudget.defaults();
        }
        return new TraversalBudget(
                intValue(map.get("maxNodes"), 300),
                intValue(map.get("maxEdges"), 800),
                intValue(map.get("maxDepthHardCap"), 5),
                intValue(map.get("maxFanOutPerNode"), 80),
                longValue(map.get("timeoutMs"), 1500));
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::text).filter(item -> !item.isBlank()).toList();
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOrDefault(Object value, String defaultValue) {
        String text = text(value);
        return text.isBlank() ? defaultValue : text;
    }

    private int intValue(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private long longValue(Object value, long defaultValue) {
        return value instanceof Number number ? number.longValue() : defaultValue;
    }
}