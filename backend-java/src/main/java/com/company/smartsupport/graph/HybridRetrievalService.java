package com.company.smartsupport.graph;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.company.smartsupport.graph.KnowledgeBlockRepository.BlockRecallRow;
import com.company.smartsupport.graph.dto.HybridEvidenceDto;

@Service
public class HybridRetrievalService {

    private final KnowledgeBlockRepository knowledgeBlockRepository;
    private final double vectorWeight;
    private final double bm25Weight;
    private final double graphWeight;
    private final double recencyWeight;

    public HybridRetrievalService(
            KnowledgeBlockRepository knowledgeBlockRepository,
            @Value("${smart-support.graph.search.hybrid.weights.vector:0.45}") double vectorWeight,
            @Value("${smart-support.graph.search.hybrid.weights.bm25:0.35}") double bm25Weight,
            @Value("${smart-support.graph.search.hybrid.weights.graph:0.20}") double graphWeight,
            @Value("${smart-support.graph.search.hybrid.weights.recency:0.05}") double recencyWeight) {
        this.knowledgeBlockRepository = knowledgeBlockRepository;
        this.vectorWeight = vectorWeight;
        this.bm25Weight = bm25Weight;
        this.graphWeight = graphWeight;
        this.recencyWeight = recencyWeight;
    }

    public List<HybridEvidenceDto> retrieve(HybridRetrievalRequest request) {
        List<String> keywords = request.keywords().isEmpty()
                ? simpleTokenize(request.questionText())
                : request.keywords();
        if (request.queryVector() != null && !request.queryVector().isEmpty()
                && !knowledgeBlockRepository.hasAnyEmbeddingVersion(request.projectId(), request.embeddingModel(), request.embeddingVersion())) {
            throw new EmbeddingVersionMismatchException("当前项目不存在匹配的 embedding_model/embedding_version 向量: "
                    + request.embeddingModel() + "/" + request.embeddingVersion());
        }

        CompletableFuture<List<BlockRecallRow>> vectorFuture = CompletableFuture.supplyAsync(() ->
                knowledgeBlockRepository.vectorRecall(request.projectId(), request.queryVector(), request.embeddingModel(), request.embeddingVersion(), request.limit()));
        CompletableFuture<List<BlockRecallRow>> bm25Future = CompletableFuture.supplyAsync(() ->
                knowledgeBlockRepository.bm25Recall(request.projectId(), keywords, request.limit()));
        CompletableFuture<List<BlockRecallRow>> graphFuture = CompletableFuture.completedFuture(List.of());
        CompletableFuture.allOf(vectorFuture, bm25Future, graphFuture).join();

        Map<String, HybridAccumulator> acc = new LinkedHashMap<>();
        vectorFuture.join().forEach(row -> acc.computeIfAbsent(row.blockId(), id -> new HybridAccumulator(row)).vectorScore = row.score());
        bm25Future.join().forEach(row -> acc.computeIfAbsent(row.blockId(), id -> new HybridAccumulator(row)).bm25Score = normalize(row.score()));
        graphFuture.join().forEach(row -> acc.computeIfAbsent(row.blockId(), id -> new HybridAccumulator(row)).graphBoostScore = normalize(row.score()));

        return acc.values().stream()
                .map(item -> item.toDto(request.recencyAware(), vectorWeight, bm25Weight, graphWeight, recencyWeight))
                .sorted((a, b) -> Double.compare(b.hybridScore(), a.hybridScore()))
                .limit(request.limit())
                .toList();
    }

    public List<String> simpleTokenize(String questionText) {
        if (questionText == null || questionText.isBlank()) {
            return List.of("empty");
        }
        String normalized = questionText.replaceAll("[^\\p{IsHan}A-Za-z0-9_]+", " ").trim();
        if (normalized.isBlank()) {
            return List.of("empty");
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens.isEmpty() ? List.of("empty") : tokens;
    }

    private double normalize(double value) {
        if (Double.isNaN(value) || value <= 0) {
            return 0;
        }
        return Math.min(1, value);
    }

    public record HybridRetrievalRequest(
            String projectId,
            String questionText,
            List<String> keywords,
            List<Double> queryVector,
            String embeddingModel,
            String embeddingVersion,
            boolean recencyAware,
            int limit) {
        public HybridRetrievalRequest {
            keywords = keywords == null ? List.of() : keywords;
            queryVector = queryVector == null ? List.of() : queryVector;
            limit = limit <= 0 ? 20 : Math.min(limit, 100);
        }
    }

    private static final class HybridAccumulator {
        private final BlockRecallRow row;
        private double vectorScore;
        private double bm25Score;
        private double graphBoostScore;

        private HybridAccumulator(BlockRecallRow row) {
            this.row = row;
        }

        private HybridEvidenceDto toDto(boolean recencyAware, double vectorWeight, double bm25Weight, double graphWeight, double recencyWeight) {
            double recencyScore = recencyAware ? recencyScore(row.createdAt()) : 0;
            double hybridScore = vectorWeight * vectorScore + bm25Weight * bm25Score + graphWeight * graphBoostScore
                    + (recencyAware ? recencyWeight * recencyScore : 0);
            return new HybridEvidenceDto(row.blockId(), row.sourceId(), row.rawTextSummary(), vectorScore, bm25Score,
                    graphBoostScore, recencyScore, hybridScore, row.embeddingModel(), row.embeddingVersion(), new HashMap<>(row.metadata()));
        }

        private double recencyScore(OffsetDateTime createdAt) {
            if (createdAt == null) {
                return 0;
            }
            long days = Math.max(0, Duration.between(createdAt, OffsetDateTime.now()).toDays());
            return 1.0 / (1.0 + days);
        }
    }
}