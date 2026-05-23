# 后端 Java Agent 交接单

## 1. 交接信息

- 交出 Agent：Copilot 后端执行
- 接手 Agent：前端 Agent、测试联调 Agent、后续后端 Agent
- 交接日期：2026-05-23
- 对应任务编号：KG-BE-001、KG-BE-002、KG-BE-003（部分）

## 2. 本轮已完成范围

### 已真实接通 PostgreSQL 的接口

1. `GET /api/v1/graphs/taxonomy`
2. `GET /api/v1/graphs/assets`
3. `GET /api/v1/graphs/assets/{graphId}`
4. `GET /api/v1/graphs/assets/categories`
5. `POST /api/v1/knowledge/sources`
6. `GET /api/v1/knowledge/sources`
7. `GET /api/v1/knowledge/sources/{sourceId}/tasks`
8. `GET /api/v1/knowledge/sources/{sourceId}/blocks`
9. `GET /api/v1/knowledge/sources/{sourceId}/candidates`
10. `POST /api/v1/knowledge/sources/{sourceId}/actions`
11. `POST /api/v1/knowledge/sources/{sourceId}/retry`

### 已完成的后端能力

1. `spring-jdbc` + PostgreSQL 驱动已接入。
2. Spring Boot 已新增 datasource 配置，默认连 `smart_support`。
3. 新增 `PgGraphMetadataRepository`，支持 taxonomy 与 graph asset 元数据读取。
4. 新增 `GraphTaxonomyService`、`GraphAssetService`。
5. 新增 `KnowledgeRepository`、`KnowledgeSourceService`。
6. 新增 graph/knowledge DTO，避免继续直接返回散乱 Mock `Map`。
7. 知识源创建支持文本/文件元数据入库，并自动创建首个 `parse` 任务。
8. `actions` 会按 action 映射到 `parse/extract/graph_build/retry` 任务类型并落库。

## 3. 仍未完成范围

1. 未接入 Python AI Service 的 parse/extract 真正执行回写。
2. `blocks` / `candidates` 当前是只读真实查询；如果数据库还没有数据，会返回空列表。
3. `graph assets detail` 当前只返回 PostgreSQL 元数据，`nodes/edges/revisions` 仍是占位空列表或简化 revision stub。
4. `update draft`、`publish/rollback` 仍走旧 Mock 流程，尚未接 Neo4j。
5. `POST /api/v1/graphs/search/diagnosis` 尚未实现真实检索。

## 4. 新增类

### graph

- `GraphTaxonomyController`
- `GraphTaxonomyService`
- `GraphAssetService`
- `PgGraphMetadataRepository`
- `dto/GraphCategoryDto`
- `dto/GraphEntityTypeDto`
- `dto/GraphRelationTypeDto`
- `dto/GraphTaxonomyResponse`
- `dto/GraphAssetSummaryDto`
- `dto/GraphAssetDetailDto`

### knowledge

- `KnowledgeRepository`
- `KnowledgeSourceService`
- `dto/KnowledgeSourceDto`
- `dto/KnowledgeIngestionTaskDto`
- `dto/KnowledgeBlockDto`
- `dto/CandidateEntityDto`
- `dto/CandidateRelationDto`
- `dto/KnowledgeCandidatesResponse`
- `dto/KnowledgeCandidatesSummaryDto`

## 5. 验证结果

### 编译验证

```bash
mvn -f backend-java/pom.xml -DskipTests package
```

结果：通过。

### 行为验证

1. 用 JDK 25 绝对路径启动 jar 成功。
2. `GET /api/v1/graphs/taxonomy` 返回 8 个分类、28 个实体类型、22 个关系类型。
3. `GET /api/v1/knowledge/sources` 返回真实 PostgreSQL 分页结果。
4. `POST /api/v1/knowledge/sources` 成功创建文本知识源。
5. `GET /api/v1/knowledge/sources/{sourceId}/tasks` 返回自动创建的 `parse` 任务。

### 环境注意事项

1. 当前 `mvn spring-boot:run` 会因 Maven 进程实际使用旧 Java 运行时而失败。
2. 本机可用 JDK 25 绝对路径启动 jar：

```bash
/Users/lucas/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home/bin/java -jar backend-java/target/backend-java-0.1.0-SNAPSHOT.jar
```

## 6. 下游接手建议

1. 前端 Agent 现在可以直接对接真实 taxonomy 和 knowledge source/task 基础能力。
2. 后续后端 Agent 优先继续做：Python parse/extract 回写、review task、graph publish/rollback、diagnosis search。
3. 联调时必须带 `X-Project-Id`；写接口还必须带 `X-Idempotency-Key`。
4. 若本地启动失败，先确认不是 Maven 使用旧 Java 运行时导致；直接用 JDK 25 `java -jar` 更稳定。
