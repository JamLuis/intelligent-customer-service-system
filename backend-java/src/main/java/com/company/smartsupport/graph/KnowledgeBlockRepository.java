package com.company.smartsupport.graph;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.company.smartsupport.common.SmartSupportException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class KnowledgeBlockRepository {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public KnowledgeBlockRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public boolean hasAnyEmbeddingVersion(String projectId, String embeddingModel, String embeddingVersion) {
        Integer found = jdbcClient.sql("""
                SELECT 1
                FROM knowledge_block
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND embedding IS NOT NULL
                  AND embedding_model = :embeddingModel
                  AND embedding_version = :embeddingVersion
                LIMIT 1
                """)
                .param("projectId", projectId)
                .param("embeddingModel", embeddingModel)
                .param("embeddingVersion", embeddingVersion)
                .query(Integer.class)
                .optional()
                .orElse(null);
        return found != null;
    }

    public List<BlockRecallRow> vectorRecall(
            String projectId,
            List<Double> queryVector,
            String embeddingModel,
            String embeddingVersion,
            int limit) {
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }
        String vectorLiteral = toVectorLiteral(queryVector);
        return jdbcClient.sql("""
                SELECT block_id,
                       source_id,
                       left(coalesce(normalized_text, raw_text), 500) AS raw_text_summary,
                       metadata::text AS metadata_json,
                       embedding_model,
                       embedding_version,
                       created_at,
                       greatest(0, 1 - (embedding <=> CAST(:queryVector AS vector))) AS score
                FROM knowledge_block
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND embedding IS NOT NULL
                  AND embedding_model = :embeddingModel
                  AND embedding_version = :embeddingVersion
                ORDER BY embedding <=> CAST(:queryVector AS vector)
                LIMIT :limit
                """)
                .param("projectId", projectId)
                .param("queryVector", vectorLiteral)
                .param("embeddingModel", embeddingModel)
                .param("embeddingVersion", embeddingVersion)
                .param("limit", limit)
                .query((rs, rowNum) -> mapRow(rs.getString("block_id"), rs.getString("source_id"),
                        rs.getString("raw_text_summary"), rs.getString("metadata_json"),
                        rs.getString("embedding_model"), rs.getString("embedding_version"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getDouble("score")))
                .list();
    }

    public List<BlockRecallRow> bm25Recall(String projectId, List<String> keywords, int limit) {
        String query = String.join(" ", keywords == null ? List.of() : keywords).trim();
        if (query.isBlank()) {
            return List.of();
        }
        return jdbcClient.sql("""
                SELECT block_id,
                       source_id,
                       left(coalesce(normalized_text, raw_text), 500) AS raw_text_summary,
                       metadata::text AS metadata_json,
                       embedding_model,
                       embedding_version,
                       created_at,
                       ts_rank_cd(ts, plainto_tsquery('simple', :query)) AS score
                FROM knowledge_block
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND ts @@ plainto_tsquery('simple', :query)
                ORDER BY score DESC, created_at DESC
                LIMIT :limit
                """)
                .param("projectId", projectId)
                .param("query", query)
                .param("limit", limit)
                .query((rs, rowNum) -> mapRow(rs.getString("block_id"), rs.getString("source_id"),
                        rs.getString("raw_text_summary"), rs.getString("metadata_json"),
                        rs.getString("embedding_model"), rs.getString("embedding_version"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getDouble("score")))
                .list();
    }

    private BlockRecallRow mapRow(String blockId, String sourceId, String rawTextSummary, String metadataJson,
            String embeddingModel, String embeddingVersion, OffsetDateTime createdAt, double score) {
        return new BlockRecallRow(blockId, sourceId, rawTextSummary, readObjectMap(metadataJson), embeddingModel, embeddingVersion, createdAt, score);
    }

    private Map<String, Object> readObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (IOException ex) {
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "知识块 metadata JSON 解析失败");
        }
    }

    private String toVectorLiteral(List<Double> vector) {
        return "[" + vector.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("") + "]";
    }

    public record BlockRecallRow(
            String blockId,
            String sourceId,
            String rawTextSummary,
            Map<String, Object> metadata,
            String embeddingModel,
            String embeddingVersion,
            OffsetDateTime createdAt,
            double score) {
    }
}