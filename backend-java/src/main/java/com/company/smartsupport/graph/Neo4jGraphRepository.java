package com.company.smartsupport.graph;

import java.util.Map;
import java.util.Optional;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.company.smartsupport.common.SmartSupportException;

@Repository
public class Neo4jGraphRepository {

    private static final String TENANT_ID = "default";

    private final Driver driver;
    private final String database;

    public Neo4jGraphRepository(Driver driver, @Value("${smart-support.neo4j.database:neo4j}") String database) {
        this.driver = driver;
        this.database = database;
    }

    public Optional<String> findEntityStatus(String projectId, String entityId) {
        return findStatus("""
                MATCH (e:KnowledgeEntity {tenantId:$tenantId, projectId:$projectId, entityId:$entityId})
                RETURN coalesce(e.status, 'published') AS status
                LIMIT 1
                """, Map.of("projectId", projectId, "entityId", entityId));
    }

    public Optional<String> findRelationStatus(String projectId, String relationId) {
        return findStatus("""
                MATCH ()-[r:RELATION {tenantId:$tenantId, projectId:$projectId, relationId:$relationId}]->()
                RETURN coalesce(r.status, 'published') AS status
                LIMIT 1
                """, Map.of("projectId", projectId, "relationId", relationId));
    }

    public void updateEntityStatus(String projectId, String entityId, String expectedStatus, String targetStatus, String reason) {
        int updated = updateStatus("""
                MATCH (e:KnowledgeEntity {tenantId:$tenantId, projectId:$projectId, entityId:$entityId})
                WITH e, coalesce(e.status, 'published') AS previousStatus
                WHERE previousStatus = $expectedStatus
                SET e.status = $targetStatus,
                    e.freezeReason = $reason,
                    e.updatedAt = datetime()
                RETURN count(e) AS updated
                """, Map.of(
                "projectId", projectId,
                "entityId", entityId,
                "expectedStatus", expectedStatus,
                "targetStatus", targetStatus,
                "reason", reason == null ? "" : reason));
        if (updated != 1) {
            throw new SmartSupportException("ICSS-KG-409-FROZEN_NODE", "实体状态已变化，请刷新后重试");
        }
    }

    public void updateRelationStatus(String projectId, String relationId, String expectedStatus, String targetStatus, String reason) {
        int updated = updateStatus("""
                MATCH ()-[r:RELATION {tenantId:$tenantId, projectId:$projectId, relationId:$relationId}]->()
                WITH r, coalesce(r.status, 'published') AS previousStatus
                WHERE previousStatus = $expectedStatus
                SET r.status = $targetStatus,
                    r.freezeReason = $reason,
                    r.updatedAt = datetime()
                RETURN count(r) AS updated
                """, Map.of(
                "projectId", projectId,
                "relationId", relationId,
                "expectedStatus", expectedStatus,
                "targetStatus", targetStatus,
                "reason", reason == null ? "" : reason));
        if (updated != 1) {
            throw new SmartSupportException("ICSS-KG-409-FROZEN_NODE", "关系状态已变化，请刷新后重试");
        }
    }

    private Optional<String> findStatus(String cypher, Map<String, Object> params) {
        try (var session = driver.session(SessionConfig.forDatabase(database))) {
            var record = session.executeRead(tx -> tx.run(cypher, withTenant(params)).single());
            return Optional.ofNullable(record.get("status").asString(null));
        } catch (org.neo4j.driver.exceptions.NoSuchRecordException ex) {
            return Optional.empty();
        } catch (RuntimeException ex) {
            throw new SmartSupportException("ICSS-KG-500-NEO4J_WRITE_FAILED", "Neo4j 查询失败: " + ex.getMessage());
        }
    }

    private int updateStatus(String cypher, Map<String, Object> params) {
        try (var session = driver.session(SessionConfig.forDatabase(database))) {
            return session.executeWrite(tx -> tx.run(cypher, withTenant(params))
                    .single()
                    .get("updated")
                    .asInt());
        } catch (SmartSupportException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SmartSupportException("ICSS-KG-500-NEO4J_WRITE_FAILED", "Neo4j 写入失败: " + ex.getMessage());
        }
    }

    private org.neo4j.driver.Value withTenant(Map<String, Object> params) {
        java.util.HashMap<String, Object> copy = new java.util.HashMap<>(params);
        copy.put("tenantId", TENANT_ID);
        return Values.value(copy);
    }
}