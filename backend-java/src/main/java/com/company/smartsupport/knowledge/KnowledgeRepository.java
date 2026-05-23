package com.company.smartsupport.knowledge;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.knowledge.dto.CandidateEntityDto;
import com.company.smartsupport.knowledge.dto.CandidateRelationDto;
import com.company.smartsupport.knowledge.dto.KnowledgeBlockDto;
import com.company.smartsupport.knowledge.dto.KnowledgeCandidatesResponse;
import com.company.smartsupport.knowledge.dto.KnowledgeCandidatesSummaryDto;
import com.company.smartsupport.knowledge.dto.KnowledgeIngestionTaskDto;
import com.company.smartsupport.knowledge.dto.KnowledgeSourceDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class KnowledgeRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public KnowledgeRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public KnowledgeSourceDto createSource(Map<String, Object> body, String projectId) {
        String graphCategoryId = requiredText(body, "graphCategoryId", "ICSS-KG-400-CATEGORY_INVALID", "图谱分类不能为空");
        String sourceType = requiredText(body, "sourceType", "ICSS-KG-400-UNSUPPORTED_SOURCE_TYPE", "知识源类型不能为空");
        validateSourceType(sourceType);
        String sensitivityLevel = text(body, "sensitivityLevel", "internal");
        String fileName = text(body, "fileName", "text".equals(sourceType) ? "raw-text.txt" : "upload." + sourceType);
        String objectKey = text(body, "objectKey", null);
        String rawText = text(body, "rawText", null);
        long fileSizeBytes = longValue(body.get("fileSizeBytes"));
        String sourceHash = text(body, "sourceHash", computeHash(sourceType, fileName, objectKey, rawText));

        if (existsSourceHash(projectId, sourceHash)) {
            throw new SmartSupportException("ICSS-KNOW-409-SOURCE_DUPLICATED", "知识源重复上传");
        }

        String graphCategoryName = jdbcClient.sql("""
                SELECT category_name
                FROM graph_category
                WHERE tenant_id = 'default'
                  AND status = 'enabled'
                  AND category_id = :graphCategoryId
                  AND project_id IN ('*', :projectId)
                ORDER BY CASE WHEN project_id = :projectId THEN 0 ELSE 1 END
                LIMIT 1
                """)
                .param("graphCategoryId", graphCategoryId)
                .param("projectId", projectId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new SmartSupportException("ICSS-KG-400-CATEGORY_INVALID", "图谱分类不存在或已停用"));

        return jdbcClient.sql("""
                INSERT INTO knowledge_source (
                    project_id,
                    source_type,
                    graph_category_id,
                    graph_category_name,
                    file_name,
                    object_key,
                    source_hash,
                    file_size_bytes,
                    raw_text,
                    sensitivity_level,
                    status,
                    parser_status,
                    extract_status,
                    graph_build_status,
                    created_by
                ) VALUES (
                    :projectId,
                    :sourceType,
                    :graphCategoryId,
                    :graphCategoryName,
                    :fileName,
                    :objectKey,
                    :sourceHash,
                    :fileSizeBytes,
                    :rawText,
                    :sensitivityLevel,
                    'uploaded',
                    'pending',
                    'pending',
                    'pending',
                    'system'
                )
                RETURNING source_id,
                          source_type,
                          graph_category_id,
                          graph_category_name,
                          file_name,
                          object_key,
                          file_size_bytes,
                          sensitivity_level,
                          status,
                          parser_status,
                          extract_status,
                          graph_build_status,
                          failure_reason,
                          created_at
                """)
                .param("projectId", projectId)
                .param("sourceType", sourceType)
                .param("graphCategoryId", graphCategoryId)
                .param("graphCategoryName", graphCategoryName)
                .param("fileName", fileName)
                .param("objectKey", objectKey)
                .param("sourceHash", sourceHash)
                .param("fileSizeBytes", fileSizeBytes)
                .param("rawText", rawText)
                .param("sensitivityLevel", sensitivityLevel)
                .query((rs, rowNum) -> mapSource(rs.getString("source_id"), rs.getString("source_type"), rs.getString("graph_category_id"), rs.getString("graph_category_name"), rs.getString("file_name"), rs.getString("object_key"), rs.getLong("file_size_bytes"), rs.getString("sensitivity_level"), rs.getString("status"), rs.getString("parser_status"), rs.getString("extract_status"), rs.getString("graph_build_status"), rs.getString("failure_reason"), rs.getObject("created_at", OffsetDateTime.class)))
                .single();
    }

    public void createTask(String sourceId, String taskType) {
        jdbcClient.sql("""
                INSERT INTO knowledge_ingestion_task (source_id, task_type, status, progress)
                VALUES (CAST(:sourceId AS uuid), :taskType, 'pending', 0)
                """)
                .param("sourceId", sourceId)
                .param("taskType", taskType)
                .update();
    }

    public List<KnowledgeSourceDto> listSources(String projectId) {
        return jdbcClient.sql("""
                SELECT source_id,
                       source_type,
                       graph_category_id,
                       graph_category_name,
                       file_name,
                       object_key,
                       file_size_bytes,
                       sensitivity_level,
                       status,
                       parser_status,
                       extract_status,
                       graph_build_status,
                       failure_reason,
                       created_at
                FROM knowledge_source
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND deleted_at IS NULL
                ORDER BY created_at DESC, source_id DESC
                """)
                .param("projectId", projectId)
                .query((rs, rowNum) -> mapSource(rs.getString("source_id"), rs.getString("source_type"), rs.getString("graph_category_id"), rs.getString("graph_category_name"), rs.getString("file_name"), rs.getString("object_key"), rs.getLong("file_size_bytes"), rs.getString("sensitivity_level"), rs.getString("status"), rs.getString("parser_status"), rs.getString("extract_status"), rs.getString("graph_build_status"), rs.getString("failure_reason"), rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    public List<KnowledgeIngestionTaskDto> listTasks(String projectId, String sourceId) {
        ensureSource(projectId, sourceId);
        return jdbcClient.sql("""
                SELECT task_id,
                       task_type,
                       status,
                       progress,
                       result_payload::text AS result_payload_json,
                       error_message,
                       started_at,
                       completed_at
                FROM knowledge_ingestion_task
                WHERE source_id = CAST(:sourceId AS uuid)
                ORDER BY created_at DESC, task_id DESC
                """)
                .param("sourceId", sourceId)
                .query((rs, rowNum) -> new KnowledgeIngestionTaskDto(
                        rs.getString("task_id"),
                        rs.getString("task_type"),
                        rs.getString("status"),
                        rs.getBigDecimal("progress"),
                        readObjectMap(rs.getString("result_payload_json")),
                        rs.getString("error_message"),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class)))
                .list();
    }

    public List<KnowledgeBlockDto> listBlocks(String projectId, String sourceId) {
        ensureSource(projectId, sourceId);
        return jdbcClient.sql("""
                SELECT block_id,
                       source_id,
                       graph_category_id,
                       block_type,
                       section_path,
                       page_no,
                       row_no,
                       col_no,
                       raw_text,
                       normalized_text,
                       metadata::text AS metadata_json,
                       created_at
                FROM knowledge_block
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND source_id = CAST(:sourceId AS uuid)
                ORDER BY created_at, block_id
                """)
                .param("projectId", projectId)
                .param("sourceId", sourceId)
                .query((rs, rowNum) -> new KnowledgeBlockDto(
                        rs.getString("block_id"),
                        rs.getString("source_id"),
                        rs.getString("graph_category_id"),
                        rs.getString("block_type"),
                        rs.getString("section_path"),
                        integerValue(rs.getObject("page_no")),
                        integerValue(rs.getObject("row_no")),
                        integerValue(rs.getObject("col_no")),
                        rs.getString("raw_text"),
                        rs.getString("normalized_text"),
                        readObjectMap(rs.getString("metadata_json")),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    public KnowledgeCandidatesResponse getCandidates(String projectId, String sourceId) {
        ensureSource(projectId, sourceId);
        List<CandidateEntityDto> entities = jdbcClient.sql("""
                SELECT candidate_id,
                       block_id,
                       graph_category_id,
                       entity_type,
                       raw_name,
                       canonical_name,
                       unique_key::text AS unique_key_json,
                       properties::text AS properties_json,
                       evidence_block_ids::text AS evidence_block_ids_json,
                       confidence,
                       extractor,
                       status,
                       review_reason
                FROM graph_candidate_entity
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND source_id = CAST(:sourceId AS uuid)
                ORDER BY created_at, candidate_id
                """)
                .param("projectId", projectId)
                .param("sourceId", sourceId)
                .query((rs, rowNum) -> new CandidateEntityDto(
                        rs.getString("candidate_id"),
                        rs.getString("block_id"),
                        rs.getString("graph_category_id"),
                        rs.getString("entity_type"),
                        rs.getString("raw_name"),
                        rs.getString("canonical_name"),
                        readObjectMap(rs.getString("unique_key_json")),
                        readObjectMap(rs.getString("properties_json")),
                        readStringList(rs.getString("evidence_block_ids_json")),
                        rs.getDouble("confidence"),
                        rs.getString("extractor"),
                        rs.getString("status"),
                        rs.getString("review_reason")))
                .list();

        List<CandidateRelationDto> relations = jdbcClient.sql("""
                SELECT candidate_relation_id,
                       source_candidate_id,
                       target_candidate_id,
                       graph_category_id,
                       relation_type,
                       properties::text AS properties_json,
                       evidence_block_ids::text AS evidence_block_ids_json,
                       confidence,
                       extractor,
                       status,
                       review_reason
                FROM graph_candidate_relation
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND source_id = CAST(:sourceId AS uuid)
                ORDER BY created_at, candidate_relation_id
                """)
                .param("projectId", projectId)
                .param("sourceId", sourceId)
                .query((rs, rowNum) -> new CandidateRelationDto(
                        rs.getString("candidate_relation_id"),
                        rs.getString("source_candidate_id"),
                        rs.getString("target_candidate_id"),
                        rs.getString("graph_category_id"),
                        rs.getString("relation_type"),
                        readObjectMap(rs.getString("properties_json")),
                        readStringList(rs.getString("evidence_block_ids_json")),
                        rs.getDouble("confidence"),
                        rs.getString("extractor"),
                        rs.getString("status"),
                        rs.getString("review_reason")))
                .list();

        int reviewingCount = (int) entities.stream().filter(item -> "reviewing".equals(item.status()) || "conflict".equals(item.status())).count()
                + (int) relations.stream().filter(item -> "reviewing".equals(item.status()) || "conflict".equals(item.status())).count();
        int conflictCount = (int) entities.stream().filter(item -> "conflict".equals(item.status())).count()
                + (int) relations.stream().filter(item -> "conflict".equals(item.status())).count();

        return new KnowledgeCandidatesResponse(
                sourceId,
                entities,
                relations,
                new KnowledgeCandidatesSummaryDto(entities.size(), relations.size(), reviewingCount, conflictCount));
    }

    public void ensureSource(String projectId, String sourceId) {
        Integer found = jdbcClient.sql("""
                SELECT 1
                FROM knowledge_source
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND source_id = CAST(:sourceId AS uuid)
                  AND deleted_at IS NULL
                """)
                .param("projectId", projectId)
                .param("sourceId", sourceId)
                .query(Integer.class)
                .optional()
                .orElse(null);
        if (found == null) {
            throw new SmartSupportException("ICSS-KG-404-SOURCE_NOT_FOUND", "知识源不存在");
        }
    }

    private boolean existsSourceHash(String projectId, String sourceHash) {
        Integer found = jdbcClient.sql("""
                SELECT 1
                FROM knowledge_source
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND source_hash = :sourceHash
                  AND deleted_at IS NULL
                LIMIT 1
                """)
                .param("projectId", projectId)
                .param("sourceHash", sourceHash)
                .query(Integer.class)
                .optional()
                .orElse(null);
        return found != null;
    }

    private void validateSourceType(String sourceType) {
        if (!List.of("doc", "docx", "xls", "xlsx", "pdf", "jpg", "jpeg", "png", "text", "md", "log", "ini", "json", "csv").contains(sourceType)) {
            throw new SmartSupportException("ICSS-KG-400-UNSUPPORTED_SOURCE_TYPE", "知识源类型不支持");
        }
    }

    private KnowledgeSourceDto mapSource(
            String sourceId,
            String sourceType,
            String graphCategoryId,
            String graphCategoryName,
            String fileName,
            String objectKey,
            long fileSizeBytes,
            String sensitivityLevel,
            String status,
            String parserStatus,
            String extractStatus,
            String graphBuildStatus,
            String failureReason,
            OffsetDateTime createdAt) {
        return new KnowledgeSourceDto(
                sourceId,
                sourceType,
                graphCategoryId,
                graphCategoryName,
                fileName,
                objectKey,
                fileSizeBytes,
                sensitivityLevel,
                status,
                parserStatus,
                extractStatus,
                graphBuildStatus,
                failureReason,
                createdAt);
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (IOException ex) {
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "知识图谱数组 JSON 解析失败");
        }
    }

    private Map<String, Object> readObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (IOException ex) {
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "知识图谱对象 JSON 解析失败");
        }
    }

    private String requiredText(Map<String, Object> body, String key, String code, String message) {
        String value = text(body, key, null);
        if (value == null || value.isBlank()) {
            throw new SmartSupportException(code, message);
        }
        return value;
    }

    private String text(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private String computeHash(String sourceType, String fileName, String objectKey, String rawText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = String.join("|",
                    sourceType == null ? "" : sourceType,
                    fileName == null ? "" : fileName,
                    objectKey == null ? "" : objectKey,
                    rawText == null ? "" : rawText);
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "无法生成知识源哈希");
        }
    }

    public String resolveTaskType(String action) {
        return switch (action) {
            case "parse" -> "parse";
            case "extract", "normalize" -> "extract";
            case "build", "publish" -> "graph_build";
            case "retry" -> "retry";
            default -> throw new SmartSupportException("ICSS-COMMON-400-INVALID_PARAMETER", "不支持的 knowledge action");
        };
    }

    public KnowledgeIngestionTaskDto createActionTask(String projectId, String sourceId, String action) {
        ensureSource(projectId, sourceId);
        String taskType = resolveTaskType(action);
        return jdbcClient.sql("""
                INSERT INTO knowledge_ingestion_task (source_id, task_type, status, progress)
                VALUES (CAST(:sourceId AS uuid), :taskType, 'pending', 0)
                RETURNING task_id,
                          task_type,
                          status,
                          progress,
                          result_payload::text AS result_payload_json,
                          error_message,
                          started_at,
                          completed_at
                """)
                .param("sourceId", sourceId)
                .param("taskType", taskType)
                .query((rs, rowNum) -> new KnowledgeIngestionTaskDto(
                        rs.getString("task_id"),
                        rs.getString("task_type"),
                        rs.getString("status"),
                        rs.getBigDecimal("progress"),
                        readObjectMap(rs.getString("result_payload_json")),
                        rs.getString("error_message"),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class)))
                .single();
    }
}
