package com.company.smartsupport.graph;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.graph.dto.GraphAssetDetailDto;
import com.company.smartsupport.graph.dto.GraphAssetSummaryDto;
import com.company.smartsupport.graph.dto.GraphCategoryDto;
import com.company.smartsupport.graph.dto.GraphEntityTypeDto;
import com.company.smartsupport.graph.dto.GraphRelationTypeDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class PgGraphMetadataRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public PgGraphMetadataRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public List<GraphCategoryDto> listCategories(String projectId) {
        return jdbcClient.sql("""
                SELECT category_id,
                       category_name,
                       domain,
                       description,
                       entity_type_scope::text AS entity_type_scope_json,
                       relation_type_scope::text AS relation_type_scope_json,
                       status,
                       sort_order
                FROM (
                    SELECT gc.*,
                           row_number() OVER (
                               PARTITION BY gc.category_id
                               ORDER BY CASE WHEN gc.project_id = :projectId THEN 0 ELSE 1 END, gc.sort_order, gc.category_id
                           ) AS rn
                    FROM graph_category gc
                    WHERE gc.tenant_id = 'default'
                      AND gc.status = 'enabled'
                      AND gc.project_id IN ('*', :projectId)
                ) scoped
                WHERE rn = 1
                ORDER BY sort_order, category_id
                """)
                .param("projectId", projectId)
                .query((rs, rowNum) -> new GraphCategoryDto(
                        rs.getString("category_id"),
                        rs.getString("category_name"),
                        rs.getString("domain"),
                        rs.getString("description"),
                        readStringList(rs.getString("entity_type_scope_json")),
                        readStringList(rs.getString("relation_type_scope_json")),
                        rs.getString("status"),
                        rs.getInt("sort_order")))
                .list();
    }

    public List<GraphEntityTypeDto> listEntityTypes() {
        return jdbcClient.sql("""
                SELECT entity_type,
                       label,
                       description,
                       unique_key_schema::text AS unique_key_schema_json,
                       property_schema::text AS property_schema_json,
                       extractor_rules::text AS extractor_rules_json,
                       status,
                       sort_order
                FROM graph_entity_type
                WHERE status = 'enabled'
                ORDER BY sort_order, entity_type
                """)
                .query((rs, rowNum) -> new GraphEntityTypeDto(
                        rs.getString("entity_type"),
                        rs.getString("label"),
                        rs.getString("description"),
                        readStringList(rs.getString("unique_key_schema_json")),
                        readObjectMap(rs.getString("property_schema_json")),
                        readObjectMap(rs.getString("extractor_rules_json")),
                        rs.getString("status"),
                        rs.getInt("sort_order")))
                .list();
    }

    public List<GraphRelationTypeDto> listRelationTypes() {
        return jdbcClient.sql("""
                SELECT relation_type,
                       label,
                       description,
                       from_entity_types::text AS from_entity_types_json,
                       to_entity_types::text AS to_entity_types_json,
                       property_schema::text AS property_schema_json,
                       inverse_relation_type,
                       status,
                       sort_order
                FROM graph_relation_type
                WHERE status = 'enabled'
                ORDER BY sort_order, relation_type
                """)
                .query((rs, rowNum) -> new GraphRelationTypeDto(
                        rs.getString("relation_type"),
                        rs.getString("label"),
                        rs.getString("description"),
                        readStringList(rs.getString("from_entity_types_json")),
                        readStringList(rs.getString("to_entity_types_json")),
                        readObjectMap(rs.getString("property_schema_json")),
                        rs.getString("inverse_relation_type"),
                        rs.getString("status"),
                        rs.getInt("sort_order")))
                .list();
    }

            public List<GraphAssetSummaryDto> listGraphAssets(
                String projectId,
                String graphCategoryId,
                String entityType,
                String relationType,
                String sourceId) {
            String categoryFilter = blankToNull(graphCategoryId);
            String sourceFilter = blankToNull(sourceId);
            String sql = """
                SELECT graph_id,
                       graph_name,
                       graph_category_id,
                       graph_category_name,
                       status,
                       node_count,
                       edge_count,
                       confidence,
                       active_revision_id,
                       CAST(source_refs AS text) AS source_refs_json,
                       CAST(entity_types AS text) AS entity_types_json,
                       CAST(relation_types AS text) AS relation_types_json
                FROM graph_asset
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND deleted_at IS NULL
                """ + (categoryFilter == null ? "" : "  AND graph_category_id = :graphCategoryId\n")
                    + (sourceFilter == null ? "" : "  AND source_refs @> CAST(:sourceRefFilter AS jsonb)\n") + """
                ORDER BY updated_at DESC, graph_name, graph_id
                """;
            var spec = jdbcClient.sql(sql)
                .param("projectId", projectId);
            if (categoryFilter != null) {
                spec = spec.param("graphCategoryId", categoryFilter);
            }
            if (sourceFilter != null) {
                spec = spec.param("sourceRefFilter", writeJson(List.of(sourceFilter)));
            }
            List<GraphAssetSummaryDto> rows = spec
                .query((rs, rowNum) -> new GraphAssetSummaryDto(
                    rs.getString("graph_id"),
                    rs.getString("graph_name"),
                    rs.getString("graph_category_id"),
                    rs.getString("graph_category_name"),
                    rs.getString("status"),
                    rs.getInt("node_count"),
                    rs.getInt("edge_count"),
                    rs.getDouble("confidence"),
                    rs.getString("active_revision_id"),
                    readStringList(rs.getString("source_refs_json")),
                    readStringList(rs.getString("entity_types_json")),
                    readStringList(rs.getString("relation_types_json"))))
                .list();
            String entityFilter = blankToNull(entityType);
            String relationFilter = blankToNull(relationType);
            return rows.stream()
                    .filter(row -> entityFilter == null || row.entityTypes().contains(entityFilter))
                    .filter(row -> relationFilter == null || row.relationTypes().contains(relationFilter))
                    .toList();
            }

            public GraphAssetDetailDto getGraphAsset(String projectId, String graphId) {
            return jdbcClient.sql("""
                SELECT graph_id,
                       graph_name,
                       graph_category_id,
                       graph_category_name,
                       status,
                       node_count,
                       edge_count,
                       confidence,
                       active_revision_id,
                       neo4j_graph_ref,
                       CAST(source_refs AS text) AS source_refs_json,
                       CAST(entity_types AS text) AS entity_types_json,
                       CAST(relation_types AS text) AS relation_types_json,
                                             CAST(classification_path AS text) AS classification_path_json,
                                             (
                                                 SELECT CAST(gr.diff_payload AS text)
                                                 FROM graph_revision gr
                                                 WHERE gr.revision_id = graph_asset.active_revision_id
                                                 LIMIT 1
                                             ) AS diff_payload_json
                FROM graph_asset
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND graph_id = CAST(:graphId AS uuid)
                  AND deleted_at IS NULL
                """)
                .param("projectId", projectId)
                .param("graphId", graphId)
                .query((rs, rowNum) -> {
                    List<String> sourceRefs = readStringList(rs.getString("source_refs_json"));
                    Map<String, Object> diffPayload = readObjectMap(rs.getString("diff_payload_json"));
                    return new GraphAssetDetailDto(
                            rs.getString("graph_id"),
                            rs.getString("graph_name"),
                            rs.getString("graph_category_id"),
                            rs.getString("graph_category_name"),
                            rs.getString("status"),
                            rs.getInt("node_count"),
                            rs.getInt("edge_count"),
                            rs.getDouble("confidence"),
                            rs.getString("active_revision_id"),
                            rs.getString("neo4j_graph_ref"),
                            sourceRefs,
                            listSources(projectId, sourceRefs),
                            listEvidenceBlocks(projectId, sourceRefs),
                            readStringList(rs.getString("entity_types_json")),
                            readStringList(rs.getString("relation_types_json")),
                            readStringList(rs.getString("classification_path_json")),
                            readObjectList(diffPayload.get("nodes")),
                            readObjectList(diffPayload.get("edges")),
                            buildRevisionStub(rs.getString("active_revision_id")));
                })
                .optional()
                .orElseThrow(() -> new SmartSupportException("ICSS-KG-404-GRAPH_NOT_FOUND", "图谱资产不存在"));
            }

    public Map<String, Object> updateGraphDraft(String projectId, String graphId, Map<String, Object> body, String actorId) {
        GraphAssetDetailDto current = getGraphAsset(projectId, graphId);
        List<Map<String, Object>> nodes = objectList(body.get("nodes"));
        List<Map<String, Object>> edges = objectList(body.get("edges"));
        if (nodes.isEmpty()) {
            throw new SmartSupportException("ICSS-KG-400-GRAPH_DRAFT_INVALID", "图谱草稿至少需要一个实体");
        }
        Set<String> nodeIds = new LinkedHashSet<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = text(node.get("entityId"), text(node.get("id"), ""));
            if (nodeId.isBlank()) {
                throw new SmartSupportException("ICSS-KG-400-GRAPH_DRAFT_INVALID", "实体缺少 ID");
            }
            nodeIds.add(nodeId);
        }
        for (Map<String, Object> edge : edges) {
            String source = text(edge.get("source"), "");
            String target = text(edge.get("target"), "");
            if (!nodeIds.contains(source) || !nodeIds.contains(target)) {
                throw new SmartSupportException("ICSS-KG-400-GRAPH_DRAFT_INVALID", "关系端点不存在");
            }
        }
        Map<String, Object> diffPayload = Map.of("nodes", nodes, "edges", edges);
        List<String> entityTypes = nodes.stream()
                .map(node -> text(node.get("entityType"), text(node.get("type"), text(nested(node, "data", "entityType"), "entity"))))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        List<String> relationTypes = edges.stream()
                .map(this::relationType)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        jdbcClient.sql("""
                UPDATE graph_revision
                SET diff_payload = CAST(:diffPayload AS jsonb),
                    change_summary = :changeSummary,
                    status = 'draft'
                WHERE revision_id = CAST(:revisionId AS uuid)
                  AND graph_id = CAST(:graphId AS uuid)
                """)
                .param("diffPayload", writeJson(diffPayload))
                .param("changeSummary", text(body.get("changeSummary"), "manual draft update"))
                .param("revisionId", current.activeRevisionId())
                .param("graphId", graphId)
                .update();
        jdbcClient.sql("""
                UPDATE graph_asset
                SET node_count = :nodeCount,
                    edge_count = :edgeCount,
                    entity_types = CAST(:entityTypes AS jsonb),
                    relation_types = CAST(:relationTypes AS jsonb),
                    status = 'draft',
                    updated_by = :actorId,
                    updated_at = now()
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND graph_id = CAST(:graphId AS uuid)
                  AND deleted_at IS NULL
                """)
                .param("nodeCount", nodes.size())
                .param("edgeCount", edges.size())
                .param("entityTypes", writeJson(entityTypes))
                .param("relationTypes", writeJson(relationTypes))
                .param("actorId", actorId == null || actorId.isBlank() ? "system" : actorId)
                .param("projectId", projectId)
                .param("graphId", graphId)
                .update();
        return Map.of(
                "graphId", graphId,
                "revisionId", current.activeRevisionId(),
                "nodeCount", nodes.size(),
                "edgeCount", edges.size(),
                "entityTypes", entityTypes,
                "relationTypes", relationTypes,
                "status", "draft");
    }

        public Map<String, Object> deleteGraphAsset(String projectId, String graphId, String actorId) {
                GraphAssetDetailDto current = getGraphAsset(projectId, graphId);
                int updated = jdbcClient.sql("""
                                UPDATE graph_asset
                                SET status = 'deprecated',
                                        deleted_at = now(),
                                        updated_by = :actorId,
                                        updated_at = now()
                                WHERE tenant_id = 'default'
                                    AND project_id = :projectId
                                    AND graph_id = CAST(:graphId AS uuid)
                                    AND deleted_at IS NULL
                                """)
                                .param("actorId", actorId == null || actorId.isBlank() ? "system" : actorId)
                                .param("projectId", projectId)
                                .param("graphId", graphId)
                                .update();
                if (updated != 1) {
                        throw new SmartSupportException("ICSS-KG-404-GRAPH_NOT_FOUND", "图谱资产不存在");
                }
                jdbcClient.sql("""
                                UPDATE graph_revision
                                SET status = 'deprecated'
                                WHERE graph_id = CAST(:graphId AS uuid)
                                    AND status <> 'deprecated'
                                """)
                                .param("graphId", graphId)
                                .update();
                return Map.of(
                                "graphId", graphId,
                                "revisionId", current.activeRevisionId(),
                                "status", "deleted");
        }

    private List<Map<String, Object>> listSources(String projectId, List<String> sourceIds) {
        if (sourceIds.isEmpty()) {
            return List.of();
        }
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
                       raw_text,
                       created_at
                FROM knowledge_source
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND CAST(source_id AS text) IN (:sourceIds)
                  AND deleted_at IS NULL
                ORDER BY created_at DESC, source_id DESC
                """)
                .param("projectId", projectId)
                .param("sourceIds", sourceIds)
                .query((rs, rowNum) -> {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("sourceId", rs.getString("source_id"));
                    item.put("sourceType", rs.getString("source_type"));
                    item.put("graphCategoryId", rs.getString("graph_category_id"));
                    item.put("graphCategoryName", rs.getString("graph_category_name"));
                    item.put("fileName", rs.getString("file_name"));
                    item.put("objectKey", rs.getString("object_key"));
                    item.put("fileSizeBytes", rs.getLong("file_size_bytes"));
                    item.put("sensitivityLevel", rs.getString("sensitivity_level"));
                    item.put("status", rs.getString("status"));
                    item.put("parserStatus", rs.getString("parser_status"));
                    item.put("extractStatus", rs.getString("extract_status"));
                    item.put("graphBuildStatus", rs.getString("graph_build_status"));
                    item.put("failureReason", rs.getString("failure_reason"));
                    item.put("rawText", rs.getString("raw_text"));
                    item.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return item;
                })
                .list();
    }

    private List<Map<String, Object>> listEvidenceBlocks(String projectId, List<String> sourceIds) {
        if (sourceIds.isEmpty()) {
            return List.of();
        }
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
                       CAST(metadata AS text) AS metadata_json,
                       created_at
                FROM knowledge_block
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND CAST(source_id AS text) IN (:sourceIds)
                ORDER BY source_id, page_no NULLS LAST, row_no NULLS LAST, created_at, block_id
                """)
                .param("projectId", projectId)
                .param("sourceIds", sourceIds)
                .query((rs, rowNum) -> {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("blockId", rs.getString("block_id"));
                    item.put("sourceId", rs.getString("source_id"));
                    item.put("graphCategoryId", rs.getString("graph_category_id"));
                    item.put("blockType", rs.getString("block_type"));
                    item.put("sectionPath", rs.getString("section_path"));
                    item.put("pageNo", rs.getObject("page_no"));
                    item.put("rowNo", rs.getObject("row_no"));
                    item.put("colNo", rs.getObject("col_no"));
                    item.put("rawText", rs.getString("raw_text"));
                    item.put("normalizedText", rs.getString("normalized_text"));
                    item.put("metadata", readObjectMap(rs.getString("metadata_json")));
                    item.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return item;
                })
                .list();
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (IOException ex) {
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "图谱分类 JSON 解析失败");
        }
    }

    private Map<String, Object> readObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (IOException ex) {
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "图谱属性 JSON 解析失败");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readObjectList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private Object nested(Map<String, Object> source, String parent, String child) {
        Object value = source.get(parent);
        if (value instanceof Map<?, ?> map) {
            return map.get(child);
        }
        return null;
    }

    private String relationType(Map<String, Object> edge) {
        return text(edge.get("relationType"), text(edge.get("type"), text(edge.get("label"), text(nested(edge, "data", "relationType"), "RELATED_TO"))));
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (IOException ex) {
            throw new SmartSupportException("ICSS-SYS-500-INTERNAL_ERROR", "图谱草稿 JSON 序列化失败");
        }
    }

    private List<Map<String, Object>> buildRevisionStub(String activeRevisionId) {
        if (activeRevisionId == null || activeRevisionId.isBlank()) {
            return List.of();
        }
        return List.of(Map.of(
                "revisionId", activeRevisionId,
                "status", "published"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
