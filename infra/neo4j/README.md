# Neo4j

Neo4j stores the unified engineering knowledge graph:

- document entities
- code entities
- protocol entities
- API and database entities
- case and operation entities

## V0.3.1 强制 Label 约束

按照 `03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md` §8：

- 商用实体 **仅** 使用 `:KnowledgeEntity`，业务类型走 `entityType` 属性。
- 商用关系 **仅** 使用 `[:RELATION]`，业务类型走 `relationType` 属性。
- 平台保护实体：`:SourceBlock` / `:Document` / `:Section`，租户不可删。
- 平台保护关系：`[:HAS_EVIDENCE]` / `[:HAS_SECTION]` / `[:IN_SECTION]`。

任何动态拼 Label / 关系类型的代码均不合规。`init.cypher` 已建立必需的 UNIQUE 约束与 6 个检索索引，幂等可重跑。

## 初始化命令

```bash
docker exec -i <neo4j-container> cypher-shell -u neo4j -p <pwd> -f /imports/init.cypher
```

或本地：

```bash
cypher-shell -a bolt://localhost:7687 -u neo4j -p test -f infra/neo4j/init.cypher
```

校验：

```cypher
CALL db.labels();             // ⊆ {KnowledgeEntity, SourceBlock, Document, Section}
CALL db.relationshipTypes();  // ⊆ {RELATION, HAS_EVIDENCE, HAS_SECTION, IN_SECTION}
SHOW CONSTRAINTS;
SHOW INDEXES;
```

- cross-source relations and evidence paths