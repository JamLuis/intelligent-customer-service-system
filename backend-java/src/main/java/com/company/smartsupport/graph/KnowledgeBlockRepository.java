package com.company.smartsupport.graph;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
                                             kb.source_id,
                                             ks.file_name,
                                             ks.source_type,
                                             ks.graph_category_name,
                                             left(coalesce(kb.normalized_text, kb.raw_text), 500) AS raw_text_summary,
                                             CAST(kb.metadata AS text) AS metadata_json,
                                             kb.embedding_model,
                                             kb.embedding_version,
                                             kb.created_at,
                                             greatest(0, 1 - (kb.embedding <=> CAST(:queryVector AS vector))) AS score
                                FROM knowledge_block kb
                                LEFT JOIN knowledge_source ks
                                    ON ks.tenant_id = kb.tenant_id
                                 AND ks.project_id = kb.project_id
                                 AND ks.source_id = kb.source_id
                                 AND ks.deleted_at IS NULL
                                WHERE kb.tenant_id = 'default'
                                    AND kb.project_id = :projectId
                                    AND kb.embedding IS NOT NULL
                                    AND kb.embedding_model = :embeddingModel
                                    AND kb.embedding_version = :embeddingVersion
                                ORDER BY kb.embedding <=> CAST(:queryVector AS vector)
                LIMIT :limit
                """)
                .param("projectId", projectId)
                .param("queryVector", vectorLiteral)
                .param("embeddingModel", embeddingModel)
                .param("embeddingVersion", embeddingVersion)
                .param("limit", limit)
                .query((rs, rowNum) -> mapRow(rs.getString("block_id"), rs.getString("source_id"),
                    rs.getString("file_name"), rs.getString("source_type"), rs.getString("graph_category_name"),
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
                                             kb.source_id,
                                             ks.file_name,
                                             ks.source_type,
                                             ks.graph_category_name,
                                             left(coalesce(kb.normalized_text, kb.raw_text), 500) AS raw_text_summary,
                                             CAST(kb.metadata AS text) AS metadata_json,
                                             kb.embedding_model,
                                             kb.embedding_version,
                                             kb.created_at,
                                             ts_rank_cd(kb.ts, plainto_tsquery('simple', :query)) AS score
                                FROM knowledge_block kb
                                LEFT JOIN knowledge_source ks
                                    ON ks.tenant_id = kb.tenant_id
                                 AND ks.project_id = kb.project_id
                                 AND ks.source_id = kb.source_id
                                 AND ks.deleted_at IS NULL
                                WHERE kb.tenant_id = 'default'
                                    AND kb.project_id = :projectId
                                    AND kb.ts @@ plainto_tsquery('simple', :query)
                                ORDER BY score DESC, kb.created_at DESC
                LIMIT :limit
                """)
                .param("projectId", projectId)
                .param("query", query)
                .param("limit", limit)
                .query((rs, rowNum) -> mapRow(rs.getString("block_id"), rs.getString("source_id"),
                    rs.getString("file_name"), rs.getString("source_type"), rs.getString("graph_category_name"),
                        rs.getString("raw_text_summary"), rs.getString("metadata_json"),
                        rs.getString("embedding_model"), rs.getString("embedding_version"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getDouble("score")))
                .list();
    }

    public List<BlockRecallRow> lexicalRecall(String projectId, List<String> keywords, int limit) {
        List<String> terms = keywords == null ? List.of() : keywords.stream()
                .map(String::trim)
                .filter(term -> term.length() >= 2)
                .distinct()
                .limit(12)
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("projectId", projectId);
        params.put("limit", limit);
        for (int index = 0; index < terms.size(); index++) {
            String name = "term" + index;
            conditions.add("coalesce(kb.normalized_text, kb.raw_text) ILIKE :" + name);
            params.put(name, "%" + terms.get(index) + "%");
        }
        String whereClause = String.join(" OR ", conditions);
        var spec = jdbcClient.sql("""
                SELECT block_id,
                       kb.source_id,
                       ks.file_name,
                       ks.source_type,
                       ks.graph_category_name,
                       left(coalesce(kb.normalized_text, kb.raw_text), 500) AS raw_text_summary,
                       CAST(kb.metadata AS text) AS metadata_json,
                       kb.embedding_model,
                       kb.embedding_version,
                       kb.created_at,
                       (
                """ + scoreExpression(terms.size()) + """
                       )::double precision / :termCount AS score
                FROM knowledge_block kb
                LEFT JOIN knowledge_source ks
                    ON ks.tenant_id = kb.tenant_id
                 AND ks.project_id = kb.project_id
                 AND ks.source_id = kb.source_id
                 AND ks.deleted_at IS NULL
                WHERE kb.tenant_id = 'default'
                  AND kb.project_id = :projectId
                  AND (
                """ + whereClause + """
                  )
                ORDER BY score DESC, kb.created_at DESC
                LIMIT :limit
                """)
                .param("termCount", Math.max(1, terms.size()));
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        return spec.query((rs, rowNum) -> mapRow(rs.getString("block_id"), rs.getString("source_id"),
                    rs.getString("file_name"), rs.getString("source_type"), rs.getString("graph_category_name"),
                        rs.getString("raw_text_summary"), rs.getString("metadata_json"),
                        rs.getString("embedding_model"), rs.getString("embedding_version"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getDouble("score")))
                .list();
    }

    public List<BlockRecallRow> sectionRecall(String projectId, String sourceId, String sectionPath, int limit) {
        if (sourceId == null || sourceId.isBlank() || sectionPath == null || sectionPath.isBlank()) {
            return List.of();
        }
        return jdbcClient.sql("""
                SELECT block_id,
                       kb.source_id,
                       ks.file_name,
                       ks.source_type,
                       ks.graph_category_name,
                       left(coalesce(kb.normalized_text, kb.raw_text), 1200) AS raw_text_summary,
                       CAST(kb.metadata AS text) AS metadata_json,
                       kb.embedding_model,
                       kb.embedding_version,
                       kb.created_at,
                       1.0 AS score
                FROM knowledge_block kb
                LEFT JOIN knowledge_source ks
                    ON ks.tenant_id = kb.tenant_id
                 AND ks.project_id = kb.project_id
                 AND ks.source_id = kb.source_id
                 AND ks.deleted_at IS NULL
                WHERE kb.tenant_id = 'default'
                  AND kb.project_id = :projectId
                  AND kb.source_id = CAST(:sourceId AS uuid)
                  AND kb.section_path = :sectionPath
                ORDER BY NULLIF(kb.metadata ->> 'lineNo', '')::integer NULLS LAST, kb.created_at
                LIMIT :limit
                """)
                .param("projectId", projectId)
                .param("sourceId", sourceId)
                .param("sectionPath", sectionPath)
                .param("limit", Math.max(1, Math.min(limit, 40)))
                .query((rs, rowNum) -> mapRow(rs.getString("block_id"), rs.getString("source_id"),
                        rs.getString("file_name"), rs.getString("source_type"), rs.getString("graph_category_name"),
                        rs.getString("raw_text_summary"), rs.getString("metadata_json"),
                        rs.getString("embedding_model"), rs.getString("embedding_version"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getDouble("score")))
                .list();
    }

    private BlockRecallRow mapRow(String blockId, String sourceId, String sourceFileName, String sourceType, String graphCategoryName, String rawTextSummary, String metadataJson,
            String embeddingModel, String embeddingVersion, OffsetDateTime createdAt, double score) {
        return new BlockRecallRow(blockId, sourceId, sourceFileName, sourceType, graphCategoryName, rawTextSummary, readObjectMap(metadataJson), embeddingModel, embeddingVersion, createdAt, score);
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

    private String scoreExpression(int size) {
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            parts.add("CASE WHEN coalesce(kb.normalized_text, kb.raw_text) ILIKE :term" + index + " THEN 1 ELSE 0 END");
        }
        return String.join(" + ", parts);
    }

    public record BlockRecallRow(
            String blockId,
            String sourceId,
            String sourceFileName,
            String sourceType,
            String graphCategoryName,
            String rawTextSummary,
            Map<String, Object> metadata,
            String embeddingModel,
            String embeddingVersion,
            OffsetDateTime createdAt,
            double score) {
    }
}