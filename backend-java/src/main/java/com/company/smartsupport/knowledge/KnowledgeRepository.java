package com.company.smartsupport.knowledge;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        String graphCategoryId = text(body, "graphCategoryId", "uncategorized");
        if (graphCategoryId == null || graphCategoryId.isBlank()) {
            graphCategoryId = "uncategorized";
        }
        String sourceType = requiredText(body, "sourceType", "ICSS-KG-400-UNSUPPORTED_SOURCE_TYPE", "知识源类型不能为空");
        validateSourceType(sourceType);
        String sensitivityLevel = text(body, "sensitivityLevel", "internal");
        String fileName = text(body, "fileName", "text".equals(sourceType) ? "raw-text.txt" : "upload." + sourceType);
        String objectKey = text(body, "objectKey", null);
        String rawText = text(body, "rawText", null);
        long fileSizeBytes = longValue(body.get("fileSizeBytes"));
        String sourceHash = text(body, "sourceHash", computeHash(sourceType, fileName, objectKey, rawText));

        if (existsSourceHash(projectId, sourceHash)) {
            if (booleanValue(body.get("overwriteDuplicate"), false)) {
                return findSourceByHash(projectId, sourceHash);
            }
            throw new SmartSupportException("ICSS-KNOW-409-SOURCE_DUPLICATED", "知识源重复上传，可开启覆盖重建后重新生成候选关系");
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

    public SourceRecord findSource(String projectId, String sourceId) {
        return jdbcClient.sql("""
                SELECT source_id,
                       source_type,
                       graph_category_id,
                       graph_category_name,
                       file_name,
                       raw_text
                FROM knowledge_source
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND source_id = CAST(:sourceId AS uuid)
                  AND deleted_at IS NULL
                """)
                .param("projectId", projectId)
                .param("sourceId", sourceId)
                .query((rs, rowNum) -> new SourceRecord(
                        rs.getString("source_id"),
                        rs.getString("source_type"),
                        rs.getString("graph_category_id"),
                        rs.getString("graph_category_name"),
                        rs.getString("file_name"),
                        rs.getString("raw_text")))
                .optional()
                .orElseThrow(() -> new SmartSupportException("ICSS-KG-404-SOURCE_NOT_FOUND", "知识源不存在"));
    }

    public void resetGeneratedArtifacts(String projectId, String sourceId) {
        ensureSource(projectId, sourceId);
        jdbcClient.sql("""
                DELETE FROM graph_candidate_relation
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND source_id = CAST(:sourceId AS uuid)
                """)
                .param("projectId", projectId)
                .param("sourceId", sourceId)
                .update();
        jdbcClient.sql("""
                DELETE FROM graph_candidate_entity
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND source_id = CAST(:sourceId AS uuid)
                """)
                .param("projectId", projectId)
                .param("sourceId", sourceId)
                .update();
        jdbcClient.sql("""
                DELETE FROM knowledge_block
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND source_id = CAST(:sourceId AS uuid)
                """)
                .param("projectId", projectId)
                .param("sourceId", sourceId)
                .update();
    }

    public void updateSourceStage(String projectId, String sourceId, String status, String parserStatus, String extractStatus, String graphBuildStatus, String failureReason) {
        jdbcClient.sql("""
                UPDATE knowledge_source
                SET status = :status,
                    parser_status = :parserStatus,
                    extract_status = :extractStatus,
                    graph_build_status = :graphBuildStatus,
                    failure_reason = :failureReason,
                    updated_at = now()
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND source_id = CAST(:sourceId AS uuid)
                """)
                .param("projectId", projectId)
                .param("sourceId", sourceId)
                .param("status", status)
                .param("parserStatus", parserStatus)
                .param("extractStatus", extractStatus)
                .param("graphBuildStatus", graphBuildStatus)
                .param("failureReason", failureReason)
                .update();
    }

    public String upsertBlock(String projectId, SourceRecord source, Map<String, Object> block) {
        String blockId = text(block, "blockId", UUID.randomUUID().toString());
        String rawText = text(block, "rawText", "");
        String normalizedText = text(block, "normalizedText", rawText);
        String contentHash = text(block, "contentHash", computeHash(text(block, "blockType", "paragraph"), "", "", normalizedText));
        return jdbcClient.sql("""
                INSERT INTO knowledge_block (
                    block_id,
                    source_id,
                    project_id,
                    graph_category_id,
                    block_type,
                    section_path,
                    page_no,
                    row_no,
                    col_no,
                    raw_text,
                    normalized_text,
                    content_hash,
                    metadata
                ) VALUES (
                    CAST(:blockId AS uuid),
                    CAST(:sourceId AS uuid),
                    :projectId,
                    :graphCategoryId,
                    :blockType,
                    :sectionPath,
                    :pageNo,
                    :rowNo,
                    :colNo,
                    :rawText,
                    :normalizedText,
                    :contentHash,
                    CAST(:metadata AS jsonb)
                )
                ON CONFLICT (tenant_id, project_id, source_id, content_hash)
                DO UPDATE SET raw_text = EXCLUDED.raw_text,
                              normalized_text = EXCLUDED.normalized_text,
                              metadata = EXCLUDED.metadata
                RETURNING block_id
                """)
                .param("blockId", blockId)
                .param("sourceId", source.sourceId())
                .param("projectId", projectId)
                .param("graphCategoryId", source.graphCategoryId())
                .param("blockType", text(block, "blockType", "paragraph"))
                .param("sectionPath", text(block, "sectionPath", ""))
                .param("pageNo", integerObject(block.get("pageNo")))
                .param("rowNo", integerObject(block.get("rowNo")))
                .param("colNo", integerObject(block.get("colNo")))
                .param("rawText", rawText)
                .param("normalizedText", normalizedText)
                .param("contentHash", contentHash)
                .param("metadata", writeJson(objectMap(block.get("metadata"))))
                .query(String.class)
                .single();
    }

    public void updateBlockEmbedding(String projectId, String blockId, List<Double> vector, String embeddingModel, String embeddingVersion, int embeddingDim) {
        if (vector == null || vector.isEmpty()) {
            return;
        }
        if (vector.size() != embeddingDim) {
            throw new SmartSupportException("ICSS-KG-422-EMBEDDING_DIM_MISMATCH", "embedding 维度与模型配置不一致");
        }
        jdbcClient.sql("""
                UPDATE knowledge_block
                SET embedding = CAST(:embedding AS vector),
                    embedding_model = :embeddingModel,
                    embedding_version = :embeddingVersion,
                    embedding_dim = :embeddingDim
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND block_id = CAST(:blockId AS uuid)
                """)
                .param("projectId", projectId)
                .param("blockId", blockId)
                .param("embedding", toVectorLiteral(vector))
                .param("embeddingModel", embeddingModel)
                .param("embeddingVersion", embeddingVersion)
                .param("embeddingDim", embeddingDim)
                .update();
    }

    public String insertCandidateEntity(String projectId, SourceRecord source, Map<String, Object> candidate, String blockId) {
        String candidateId = UUID.randomUUID().toString();
        jdbcClient.sql("""
                INSERT INTO graph_candidate_entity (
                    candidate_id,
                    source_id,
                    block_id,
                    project_id,
                    graph_category_id,
                    entity_type,
                    raw_name,
                    canonical_name,
                    unique_key,
                    properties,
                    evidence_block_ids,
                    confidence,
                    extractor,
                    status,
                    review_reason
                ) VALUES (
                    CAST(:candidateId AS uuid),
                    CAST(:sourceId AS uuid),
                    CAST(:blockId AS uuid),
                    :projectId,
                    :graphCategoryId,
                    :entityType,
                    :rawName,
                    :canonicalName,
                    CAST(:uniqueKey AS jsonb),
                    CAST(:properties AS jsonb),
                    CAST(:evidenceBlockIds AS jsonb),
                    :confidence,
                    :extractor,
                    :status,
                    :reviewReason
                )
                """)
                .param("candidateId", candidateId)
                .param("sourceId", source.sourceId())
                .param("blockId", blockId)
                .param("projectId", projectId)
                .param("graphCategoryId", source.graphCategoryId())
                .param("entityType", requiredText(candidate, "entityType", "ICSS-KG-400-ENTITY_TYPE_INVALID", "实体类型不能为空"))
                .param("rawName", requiredText(candidate, "rawName", "ICSS-KG-422-EXTRACT_INVALID", "实体原始名称不能为空"))
                .param("canonicalName", requiredText(candidate, "canonicalName", "ICSS-KG-422-EXTRACT_INVALID", "实体规范名称不能为空"))
                .param("uniqueKey", writeJson(objectMap(candidate.get("uniqueKey"))))
                .param("properties", writeJson(objectMap(candidate.get("properties"))))
                .param("evidenceBlockIds", writeJson(stringList(candidate.get("evidenceBlockIds"))))
                .param("confidence", bigDecimal(candidate.get("confidence")))
                .param("extractor", text(candidate, "extractor", "rule"))
                .param("status", text(candidate, "status", "candidate"))
                .param("reviewReason", text(candidate, "reviewReason", null))
                .update();
        return candidateId;
    }

    public String insertCandidateRelation(String projectId, SourceRecord source, Map<String, Object> relation, String sourceCandidateId, String targetCandidateId) {
        String relationId = UUID.randomUUID().toString();
        jdbcClient.sql("""
                INSERT INTO graph_candidate_relation (
                    candidate_relation_id,
                    source_id,
                    source_candidate_id,
                    target_candidate_id,
                    project_id,
                    graph_category_id,
                    relation_type,
                    properties,
                    evidence_block_ids,
                    evidence_refs,
                    confidence,
                    extractor,
                    status,
                    review_reason
                ) VALUES (
                    CAST(:relationId AS uuid),
                    CAST(:sourceId AS uuid),
                    CAST(:sourceCandidateId AS uuid),
                    CAST(:targetCandidateId AS uuid),
                    :projectId,
                    :graphCategoryId,
                    :relationType,
                    CAST(:properties AS jsonb),
                    CAST(:evidenceBlockIds AS jsonb),
                    CAST(:evidenceRefs AS jsonb),
                    :confidence,
                    :extractor,
                    :status,
                    :reviewReason
                )
                """)
                .param("relationId", relationId)
                .param("sourceId", source.sourceId())
                .param("sourceCandidateId", sourceCandidateId)
                .param("targetCandidateId", targetCandidateId)
                .param("projectId", projectId)
                .param("graphCategoryId", source.graphCategoryId())
                .param("relationType", requiredText(relation, "relationType", "ICSS-KG-400-RELATION_TYPE_INVALID", "关系类型不能为空"))
                .param("properties", writeJson(objectMap(relation.get("properties"))))
                .param("evidenceBlockIds", writeJson(stringList(relation.get("evidenceBlockIds"))))
                .param("evidenceRefs", writeJson(objectList(relation.get("evidence"))))
                .param("confidence", bigDecimal(relation.get("confidence")))
                .param("extractor", text(relation, "extractor", "rule"))
                .param("status", text(relation, "status", "candidate"))
                .param("reviewReason", text(relation, "reviewReason", null))
                .update();
        return relationId;
    }

    public GraphBuildRecord createDraftGraph(String projectId, SourceRecord source, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        String batchId = jdbcClient.sql("""
                INSERT INTO graph_build_batch (source_id, project_id, batch_type, status, stats_payload, completed_at)
                VALUES (CAST(:sourceId AS uuid), :projectId, 'knowledge', 'success', CAST(:stats AS jsonb), now())
                RETURNING batch_id
                """)
                .param("sourceId", source.sourceId())
                .param("projectId", projectId)
                .param("stats", writeJson(Map.of("nodes", nodes.size(), "edges", edges.size())))
                .query(String.class)
                .single();
        String graphId = jdbcClient.sql("""
                INSERT INTO graph_asset (
                    batch_id,
                    project_id,
                    graph_type,
                    graph_category_id,
                    graph_category_name,
                    graph_name,
                    source_refs,
                    entity_types,
                    relation_types,
                    classification_path,
                    node_count,
                    edge_count,
                    confidence,
                    status
                ) VALUES (
                    CAST(:batchId AS uuid),
                    :projectId,
                    'knowledge',
                    :graphCategoryId,
                    :graphCategoryName,
                    :graphName,
                    CAST(:sourceRefs AS jsonb),
                    CAST(:entityTypes AS jsonb),
                    CAST(:relationTypes AS jsonb),
                    CAST(:classificationPath AS jsonb),
                    :nodeCount,
                    :edgeCount,
                    :confidence,
                    'draft'
                )
                RETURNING graph_id
                """)
                .param("batchId", batchId)
                .param("projectId", projectId)
                .param("graphCategoryId", source.graphCategoryId())
                .param("graphCategoryName", source.graphCategoryName())
                .param("graphName", source.fileName() == null || source.fileName().isBlank() ? "知识图谱草稿" : source.fileName())
                .param("sourceRefs", writeJson(List.of(source.sourceId())))
                .param("entityTypes", writeJson(distinctValues(nodes, "entityType")))
                .param("relationTypes", writeJson(distinctValues(edges, "relationType")))
                .param("classificationPath", writeJson(List.of(source.graphCategoryName())))
                .param("nodeCount", nodes.size())
                .param("edgeCount", edges.size())
                .param("confidence", averageConfidence(nodes, edges))
                .query(String.class)
                .single();
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("nodes", nodes);
        diff.put("edges", edges);
        String revisionId = jdbcClient.sql("""
                INSERT INTO graph_revision (graph_id, revision_no, status, change_summary, diff_payload, source_refs)
                VALUES (CAST(:graphId AS uuid), 1, 'draft', 'source ingestion draft', CAST(:diffPayload AS jsonb), CAST(:sourceRefs AS jsonb))
                RETURNING revision_id
                """)
                .param("graphId", graphId)
                .param("diffPayload", writeJson(diff))
                .param("sourceRefs", writeJson(List.of(source.sourceId())))
                .query(String.class)
                .single();
        jdbcClient.sql("""
                UPDATE graph_asset
                SET active_revision_id = CAST(:revisionId AS uuid),
                    neo4j_graph_ref = :neo4jGraphRef,
                    updated_at = now()
                WHERE graph_id = CAST(:graphId AS uuid)
                """)
                .param("graphId", graphId)
                .param("revisionId", revisionId)
                .param("neo4jGraphRef", "kg:" + graphId + ":" + revisionId)
                .update();
        return new GraphBuildRecord(graphId, revisionId, batchId);
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

    public void completeLatestTask(String sourceId, String taskType, Map<String, Object> result) {
        jdbcClient.sql("""
                UPDATE knowledge_ingestion_task
                SET status = 'success',
                    progress = 100,
                    result_payload = CAST(:resultPayload AS jsonb),
                    started_at = coalesce(started_at, created_at),
                    completed_at = now()
                WHERE task_id = (
                    SELECT task_id
                    FROM knowledge_ingestion_task
                    WHERE source_id = CAST(:sourceId AS uuid)
                      AND task_type = :taskType
                    ORDER BY created_at DESC, task_id DESC
                    LIMIT 1
                )
                """)
                .param("sourceId", sourceId)
                .param("taskType", taskType)
                .param("resultPayload", writeJson(result))
                .update();
    }

    public void failLatestTask(String sourceId, String taskType, String message) {
        jdbcClient.sql("""
                UPDATE knowledge_ingestion_task
                SET status = 'failed',
                    error_message = :message,
                    started_at = coalesce(started_at, created_at),
                    completed_at = now()
                WHERE task_id = (
                    SELECT task_id
                    FROM knowledge_ingestion_task
                    WHERE source_id = CAST(:sourceId AS uuid)
                      AND task_type = :taskType
                    ORDER BY created_at DESC, task_id DESC
                    LIMIT 1
                )
                """)
                .param("sourceId", sourceId)
                .param("taskType", taskType)
                .param("message", message)
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

        private KnowledgeSourceDto findSourceByHash(String projectId, String sourceHash) {
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
                                    AND source_hash = :sourceHash
                                    AND deleted_at IS NULL
                                ORDER BY created_at DESC, source_id DESC
                                LIMIT 1
                                """)
                                .param("projectId", projectId)
                                .param("sourceHash", sourceHash)
                                .query((rs, rowNum) -> mapSource(rs.getString("source_id"), rs.getString("source_type"), rs.getString("graph_category_id"), rs.getString("graph_category_name"), rs.getString("file_name"), rs.getString("object_key"), rs.getLong("file_size_bytes"), rs.getString("sensitivity_level"), rs.getString("status"), rs.getString("parser_status"), rs.getString("extract_status"), rs.getString("graph_build_status"), rs.getString("failure_reason"), rs.getObject("created_at", OffsetDateTime.class)))
                                .single();
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

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
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

    private Integer integerObject(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal bigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (IOException ex) {
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "知识图谱 JSON 序列化失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private List<String> distinctValues(List<Map<String, Object>> rows, String key) {
        return rows.stream()
                .map(row -> row.get(key))
                .filter(value -> value != null && !String.valueOf(value).isBlank())
                .map(String::valueOf)
                .distinct()
                .toList();
    }

    private String toVectorLiteral(List<Double> vector) {
        return "[" + vector.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("") + "]";
    }

    private BigDecimal averageConfidence(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            if (node.get("confidence") instanceof Number number) {
                values.add(number.doubleValue());
            }
        }
        for (Map<String, Object> edge : edges) {
            if (edge.get("confidence") instanceof Number number) {
                values.add(number.doubleValue());
            }
        }
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return BigDecimal.valueOf(Math.min(1, Math.max(0, average)));
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

    public record SourceRecord(
            String sourceId,
            String sourceType,
            String graphCategoryId,
            String graphCategoryName,
            String fileName,
            String rawText) {
    }

    public record GraphBuildRecord(String graphId, String revisionId, String batchId) {
    }
}
