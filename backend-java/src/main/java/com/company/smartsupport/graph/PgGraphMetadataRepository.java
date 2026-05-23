package com.company.smartsupport.graph;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
                String relationType) {
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
                       source_refs::text AS source_refs_json,
                       entity_types::text AS entity_types_json,
                       relation_types::text AS relation_types_json
                FROM graph_asset
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND deleted_at IS NULL
                  AND (:graphCategoryId IS NULL OR graph_category_id = :graphCategoryId)
                  AND (:entityType IS NULL OR entity_types ? :entityType)
                  AND (:relationType IS NULL OR relation_types ? :relationType)
                ORDER BY updated_at DESC, graph_name, graph_id
                """)
                .param("projectId", projectId)
                .param("graphCategoryId", blankToNull(graphCategoryId))
                .param("entityType", blankToNull(entityType))
                .param("relationType", blankToNull(relationType))
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
                       source_refs::text AS source_refs_json,
                       entity_types::text AS entity_types_json,
                       relation_types::text AS relation_types_json,
                       classification_path::text AS classification_path_json
                FROM graph_asset
                WHERE tenant_id = 'default'
                  AND project_id = :projectId
                  AND graph_id = CAST(:graphId AS uuid)
                  AND deleted_at IS NULL
                """)
                .param("projectId", projectId)
                .param("graphId", graphId)
                .query((rs, rowNum) -> new GraphAssetDetailDto(
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
                    readStringList(rs.getString("source_refs_json")),
                    readStringList(rs.getString("entity_types_json")),
                    readStringList(rs.getString("relation_types_json")),
                    readStringList(rs.getString("classification_path_json")),
                    List.of(),
                    List.of(),
                    buildRevisionStub(rs.getString("active_revision_id"))))
                .optional()
                .orElseThrow(() -> new SmartSupportException("ICSS-KG-404-GRAPH_NOT_FOUND", "图谱资产不存在"));
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
