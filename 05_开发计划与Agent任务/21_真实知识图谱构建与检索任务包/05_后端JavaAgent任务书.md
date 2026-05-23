# 后端 Java Agent 任务书

## 1. 角色边界

后端 Java Agent 负责 Spring Boot 主后台中的真实 KG API、任务状态机、PostgreSQL Repository、Neo4j Repository、AI Service client、权限审计和诊断检索编排。

不负责：Python 解析/抽取算法、不负责前端页面、不负责数据库 SQL 定义、不直接调用 LLM。

## 2. 必读文件

1. `03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md`
2. `04_数据库与API/09_API文档.md`
3. `05_开发计划与Agent任务/21_真实知识图谱构建与检索任务包/02_任务总表.md`
4. 数据库 Agent 交接结果
5. 接口 Agent 交接结果

## 3. 推荐包结构

```text
backend-java/src/main/java/com/company/smartsupport/
  knowledge/
    KnowledgeController.java
    KnowledgeApplicationService.java
    KnowledgeSourceService.java
    IngestionTaskService.java
    KnowledgeBlockRepository.java
    CandidateRepository.java
    dto/
  graph/
    GraphController.java
    GraphSearchController.java
    GraphApplicationService.java
    GraphTaxonomyService.java
    GraphAssetService.java
    GraphReviewService.java
    Neo4jGraphRepository.java
    PgGraphMetadataRepository.java
    dto/
  integration/
    AiGraphClient.java
    Neo4jClientConfig.java
  audit/
    AuditService.java
```

## 4. 任务拆分

### KG-BE-001 Java KG 包结构与 DTO

- 输入：接口 Agent KG-001~KG-015 契约。
- 输出：Controller、DTO、Service interface 骨架。
- DoD：
  - Maven 编译通过。
  - DTO 字段与 API 文档一致。
  - 保留现有 Mock API 路径兼容。

### KG-BE-002 Taxonomy 真实查询服务

- 输入：`graph_category`、`graph_entity_type`、`graph_relation_type` seed。
- 输出：`GET /api/v1/graphs/taxonomy`。
- DoD：
  - 从数据库读取分类、实体类型、关系类型。
  - 支持 `projectId` 和全局 `*` 分类合并。
  - 前端不再依赖写死分类。

### KG-BE-003 Knowledge source 与 task 状态机

- 输入：KG-002、KG-005 API。
- 输出：source 创建、action 执行、task 状态流。
- 状态要求：
  - source：uploaded -> parsing -> extracted -> graph_ready -> published / failed
  - task：pending -> running -> success / failed / canceled
- DoD：
  - 创建结构化文本 source。
  - 创建文件 source 元数据。
  - parse/extract/build/retry action 有幂等处理。
  - 失败写错误原因。

### KG-BE-004 保存 blocks/candidates

- 输入：Python `/knowledge/parse`、`/knowledge/extract` 返回。
- 输出：`knowledge_block`、`graph_candidate_entity`、`graph_candidate_relation` 保存逻辑。
- DoD：
  - block 保留 sourceId、blockId、blockType、pageNo、sectionPath、rawText、normalizedText、metadata、embedding。
  - candidate 保留 evidenceBlockIds、confidence、extractor、status。
  - 重跑时按 sourceId + content_hash 去重或覆盖策略明确。

### KG-BE-005 Review task 生成与处理

- 输入：候选 confidence、conflict 标记。
- 输出：review task 列表、处理 API。
- DoD：
  - 低置信度实体/关系生成 `low_confidence` 任务。
  - 冲突候选生成 `conflict` 任务。
  - 处理结果能改变候选状态为 approved/rejected/merged。

### KG-BE-006 Neo4j Repository 与 constraints

- 输入：07F §8。
- 输出：Neo4j 查询/写入 Repository。
- DoD：
  - 参数化 Cypher，不拼接用户输入。
  - draft 节点/关系写入携带 tenantId、projectId、entityId、relationId、revisionId、status、sourceRefs、evidenceRefs、confidence。
  - 初始化 constraints/index 的方式明确。

### KG-BE-007 Graph draft/publish/rollback

- 输入：候选 accepted/reviewed 结果。
- 输出：graph draft、publish、rollback API。
- DoD：
  - build 生成 draft revision。
  - publish 切换 activeRevisionId。
  - rollback 不删除历史，只切 active revision。
  - 诊断检索不命中 draft/reviewing。

### KG-BE-008 管理图谱查询与证据溯源

- 输入：Neo4j published graph、PostgreSQL block/evidence。
- 输出：assets/detail/neighbors/paths/evidence API。
- DoD：
  - 支持 graphCategoryId/entityType/relationType/status 筛选。
  - 支持实体邻居 depth 1-3。
  - 支持 source-target path 查询。
  - evidence 能返回 source、block、页码/章节、原文摘要。

### KG-BE-009 诊断检索 API

- 输入：用户问题、上下文、Neo4j、pgvector、Python retrieval plan。
- 输出：`POST /api/v1/graphs/search/diagnosis`。
- DoD：
  - 返回 matchedEntities、graphPaths、sourceEvidence、vectorEvidence、suggestedMcpCapabilities、confidence。
  - 只使用 published active revision。
  - 记录 graph_query_log。
  - 向量结果只作为证据补充，不直接生成关系。

## 5. 关键类职责

| 类 | 职责 |
| --- | --- |
| `KnowledgeApplicationService` | source/action 编排入口 |
| `IngestionTaskService` | 状态机、重试、失败处理 |
| `GraphTaxonomyService` | 分类、本体、关系类型读取缓存 |
| `GraphAssetService` | 图谱资产、草稿、发布、回滚 |
| `Neo4jGraphRepository` | Neo4j 读写、路径查询 |
| `GraphSearchService` | 诊断检索融合 Neo4j/pgvector/evidence |
| `GraphReviewService` | 低置信度和冲突复核 |
| `AiGraphClient` | 调用 Python parse/extract/retrieve-plan |

## 6. 禁止事项

1. 不要把 Python 候选直接发布为 Neo4j published。
2. 不要在 Controller 中写业务状态机。
3. 不要让前端传来的 `projectId` 覆盖后端鉴权上下文。
4. 不要在 Cypher 中字符串拼接用户输入。
5. 不要让 draft/reviewing 图谱进入诊断检索。

## 7. 验证命令

```bash
mvn -f backend-java/pom.xml -DskipTests package
```

如完成真实接口，补充 curl smoke：

```bash
curl http://127.0.0.1:8088/api/v1/graphs/taxonomy
```

## 8. 交接给前端/测试

必须说明：

1. 哪些 API 已真实，哪些仍 Mock。
2. 发布/回滚是否可用。
3. Neo4j 是否需要先手工初始化 constraints。
4. task action 的幂等键规则。
5. 诊断检索目前支持哪些 graphCategoryId。
