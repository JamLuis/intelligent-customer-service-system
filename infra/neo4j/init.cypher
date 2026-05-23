// ============================================================================
// Neo4j 知识图谱 V0.3.1 初始化脚本
// 对应 07F §8 真实知识图谱模型与单一 Label 约束
//
// 强制约束（不允许任何业务 Label / 业务 RelType）：
//   1. 商用实体仅使用 :KnowledgeEntity，业务类型走 entityType 属性
//   2. 商用关系仅使用 [:RELATION]，业务类型走 relationType 属性
//   3. 平台保护实体仅 :SourceBlock / :Document / :Section
//   4. 平台保护关系仅 [:HAS_EVIDENCE] / [:HAS_SECTION] / [:IN_SECTION]
//
// 本脚本幂等可重复执行：cypher-shell -f init.cypher
// ============================================================================

// ---------------------------------------------------------------------------
// 1. KnowledgeEntity 唯一约束 + 检索索引
// ---------------------------------------------------------------------------
CREATE CONSTRAINT knowledge_entity_uniq IF NOT EXISTS
  FOR (e:KnowledgeEntity)
  REQUIRE (e.tenantId, e.projectId, e.entityType, e.entityId) IS UNIQUE;

CREATE INDEX knowledge_entity_type IF NOT EXISTS
  FOR (e:KnowledgeEntity)
  ON (e.tenantId, e.projectId, e.entityType, e.status);

CREATE INDEX knowledge_entity_name IF NOT EXISTS
  FOR (e:KnowledgeEntity)
  ON (e.tenantId, e.projectId, e.entityName);

CREATE INDEX knowledge_entity_category IF NOT EXISTS
  FOR (e:KnowledgeEntity)
  ON (e.tenantId, e.projectId, e.category);

CREATE INDEX knowledge_entity_revision IF NOT EXISTS
  FOR (e:KnowledgeEntity)
  ON (e.tenantId, e.projectId, e.revisionId, e.status);

// 别名列表（属性为字符串数组）走范围/包含查询，使用全文索引
CREATE FULLTEXT INDEX knowledge_entity_aliases_fulltext IF NOT EXISTS
  FOR (e:KnowledgeEntity)
  ON EACH [e.aliases, e.entityName];

// ---------------------------------------------------------------------------
// 2. RELATION 索引（关系上 relationId 在业务上唯一，但 Neo4j 5 暂不支持
//     关系唯一约束跨多属性，因此用复合索引 + 应用侧 MERGE 唯一保证）
// ---------------------------------------------------------------------------
CREATE INDEX relation_type_status IF NOT EXISTS
  FOR ()-[r:RELATION]-()
  ON (r.tenantId, r.projectId, r.relationType, r.status);

CREATE INDEX relation_revision_status IF NOT EXISTS
  FOR ()-[r:RELATION]-()
  ON (r.tenantId, r.projectId, r.revisionId, r.status);

CREATE INDEX relation_id IF NOT EXISTS
  FOR ()-[r:RELATION]-()
  ON (r.relationId);

// ---------------------------------------------------------------------------
// 3. SourceBlock / Document / Section 平台保护实体
// ---------------------------------------------------------------------------
CREATE CONSTRAINT source_block_uniq IF NOT EXISTS
  FOR (b:SourceBlock)
  REQUIRE (b.tenantId, b.projectId, b.blockId) IS UNIQUE;

CREATE INDEX source_block_source IF NOT EXISTS
  FOR (b:SourceBlock)
  ON (b.tenantId, b.projectId, b.sourceId);

CREATE CONSTRAINT document_uniq IF NOT EXISTS
  FOR (d:Document)
  REQUIRE (d.tenantId, d.projectId, d.sourceId) IS UNIQUE;

CREATE CONSTRAINT section_uniq IF NOT EXISTS
  FOR (s:Section)
  REQUIRE (s.tenantId, s.projectId, s.sourceId, s.sectionPath) IS UNIQUE;

// ---------------------------------------------------------------------------
// 4. HAS_EVIDENCE / HAS_SECTION / IN_SECTION 平台保护关系（仅建索引）
// ---------------------------------------------------------------------------
CREATE INDEX has_evidence_block IF NOT EXISTS
  FOR ()-[r:HAS_EVIDENCE]-()
  ON (r.tenantId, r.projectId);

// ---------------------------------------------------------------------------
// 5. 校验：脚本结束后人工可用以下命令确认环境只含允许的 Label/Relation
//      CALL db.labels()           // 期望 ⊆ {KnowledgeEntity, SourceBlock, Document, Section}
//      CALL db.relationshipTypes() // 期望 ⊆ {RELATION, HAS_EVIDENCE, HAS_SECTION, IN_SECTION}
//      SHOW CONSTRAINTS
//      SHOW INDEXES
// ---------------------------------------------------------------------------
RETURN 'neo4j-init-v0.3.1-ok' AS status;
