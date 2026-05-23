# 真实知识图谱 API 补充契约

## 1. 文档信息

- 文档编号：09J
- 适用范围：真实知识图谱构建与检索 V0.2
- Base Path：`/api/v1`
- 上游依据：`03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md`、KG-API-001、KG-API-002
- 依赖数据库：`real_kg_schema.sql`、`graph_protected_taxonomy.sql`
- 设计原则：
  - **通用平台**：不内置任何业务领域本体，本文示例中出现的 `<EntityTypeA>` `<RELATION_A>` `<category-id>` 等占位名均须由租户通过 KG-ADMIN-* 注册后代入。
  - 真实关系图谱优先；pgvector 只作为语义召回和证据补充；LLM 抽取结果只能作为候选，不能直接发布。

## 2. 通用请求头

| Header | 必填 | 说明 |
| --- | --- | --- |
| Authorization | 是 | Bearer token |
| X-Project-Id | 是 | 项目隔离依据，后端以鉴权上下文为准 |
| X-Idempotency-Key | 写接口必填 | 创建 source、action、草稿保存、发布/回滚、复核处理必须携带 |

## 3. 状态枚举

| 对象 | 状态 |
| --- | --- |
| knowledge_source | uploaded、parsing、extracted、graph_ready、published、failed |
| knowledge_ingestion_task | pending、running、success、failed、canceled |
| graph_candidate_entity/relation | candidate、accepted、reviewing、rejected、conflict、merged |
| graph_asset | draft、reviewing、published、deprecated、rolled_back |
| graph_review_task | pending、approved、rejected、merged、canceled |

## 4. 数据结构

### 4.1 TaxonomyCategory

```json
{
  "categoryId": "<category-id>",
  "categoryName": "<分类名>",
  "domain": "<租户自定义域标签>",
  "description": "<说明>",
  "entityTypeScope": ["<EntityTypeA>", "<EntityTypeB>"],
  "relationTypeScope": ["<RELATION_A>"],
  "status": "enabled",
  "sortOrder": 10
}
```

### 4.2 KnowledgeBlock

```json
{
  "blockId": "uuid",
  "sourceId": "uuid",
  "graphCategoryId": "<category-id>",
  "blockType": "paragraph",
  "sectionPath": "<章节路径>",
  "pageNo": 3,
  "rowNo": null,
  "colNo": null,
  "rawText": "<原文>",
  "normalizedText": "<规范化后文本>",
  "contentHash": "sha256",
  "metadata": {"parser": "docling"},
  "createdAt": "2026-05-23T10:00:00+08:00"
}
```

### 4.3 CandidateEntity

```json
{
  "candidateId": "uuid",
  "sourceId": "uuid",
  "blockId": "uuid",
  "graphCategoryId": "<category-id>",
  "entityType": "<EntityTypeA>",
  "rawName": "<原始名称>",
  "canonicalName": "<规范名>",
  "uniqueKey": {"<keyField>": "<value>"},
  "properties": {"<prop>": "<value>"},
  "evidenceBlockIds": ["uuid"],
  "confidence": 0.88,
  "extractor": "rule",
  "status": "candidate",
  "reviewReason": null
}
```

### 4.4 CandidateRelation

```json
{
  "candidateRelationId": "uuid",
  "sourceId": "uuid",
  "sourceCandidateId": "uuid",
  "targetCandidateId": "uuid",
  "graphCategoryId": "<category-id>",
  "relationType": "<RELATION_A>",
  "properties": {"<prop>": "<value>"},
  "evidenceBlockIds": ["uuid"],
  "confidence": 0.84,
  "extractor": "llm",
  "status": "reviewing",
  "reviewReason": "confidence_below_publish_threshold"
}
```

### 4.5 GraphNode / GraphEdge

```json
{
  "nodes": [
    {
      "id": "<EntityTypeA>:<entityId>",
      "entityId": "<entityId>",
      "entityType": "<EntityTypeA>",
      "name": "<名称>",
      "aliases": ["<别名>"],
      "graphCategoryIds": ["<category-id>"],
      "sourceRefs": ["sourceId"],
      "confidence": 0.91,
      "status": "published",
      "revisionId": "uuid",
      "properties": {}
    }
  ],
  "edges": [
    {
      "id": "relationId",
      "source": "<EntityTypeA>:<entityId>",
      "target": "<EntityTypeB>:<entityId>",
      "relationType": "<RELATION_A>",
      "sourceRefs": ["sourceId"],
      "evidenceRefs": ["blockId"],
      "confidence": 0.91,
      "status": "published",
      "revisionId": "uuid",
      "properties": {"<prop>": "<value>"}
    }
  ]
}
```

## 5. API 清单

| 编号 | 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- | --- |
| KG-001 | GET | `/api/v1/graphs/taxonomy` | graph:view | 图谱分类、本体、关系类型配置 |
| KG-002 | POST | `/api/v1/knowledge/sources` | knowledge:upload | 创建知识源，必须带 graphCategoryId |
| KG-003 | GET | `/api/v1/knowledge/sources/{sourceId}/blocks` | knowledge:view | 查看解析块 |
| KG-004 | GET | `/api/v1/knowledge/sources/{sourceId}/candidates` | knowledge:view | 查看候选实体/关系 |
| KG-005 | POST | `/api/v1/knowledge/sources/{sourceId}/actions` | knowledge:retry / graph:publish | parse/extract/build/publish/retry |
| KG-006 | GET | `/api/v1/graphs/assets` | graph:view | 分类、实体、关系筛选图谱资产 |
| KG-007 | GET | `/api/v1/graphs/assets/{graphId}` | graph:view | 图谱详情，包含 nodes/edges/sourceRefs/revisions |
| KG-008 | PATCH | `/api/v1/graphs/assets/{graphId}/draft` | graph:edit | 保存草稿节点关系 |
| KG-009 | POST | `/api/v1/graphs/assets/{graphId}/versions/actions` | graph:publish / graph:rollback | publish/rollback/deprecate |
| KG-010 | POST | `/api/v1/graphs/search/diagnosis` | graph:view | 诊断检索子图 |
| KG-011 | GET | `/api/v1/graphs/entities/{entityId}/neighbors` | graph:view | 实体邻居 |
| KG-012 | GET | `/api/v1/graphs/paths` | graph:view | source-target 路径查询 |
| KG-013 | GET | `/api/v1/graphs/evidence` | graph:view | 证据块溯源 |
| KG-014 | GET | `/api/v1/graphs/review-tasks` | graph:review | 低置信度/冲突复核任务 |
| KG-015 | PATCH | `/api/v1/graphs/review-tasks/{taskId}` | graph:review | 复核通过/驳回/合并 |
| KG-016 | POST | `/api/v1/graphs/taxonomy/categories` | graph:admin | 租户注册业务分类 |
| KG-017 | PUT | `/api/v1/graphs/taxonomy/categories/{categoryId}` | graph:admin | 更新分类 |
| KG-018 | DELETE | `/api/v1/graphs/taxonomy/categories/{categoryId}` | graph:admin | 启用/禁用/删除分类 |
| KG-019 | POST/PUT/DELETE | `/api/v1/graphs/taxonomy/entity-types[/{entityType}]` | graph:admin | 租户注册/更新/禁用实体类型（平台保护类型只读） |
| KG-020 | POST/PUT/DELETE | `/api/v1/graphs/taxonomy/relation-types[/{relationType}]` | graph:admin | 租户注册/更新/禁用关系类型（平台保护关系只读） |
| KG-021 | POST | `/api/v1/graphs/taxonomy/import` | graph:admin | 批量导入本体包（JSON），默认 dry-run 预览 |

## 6. API 详情

### KG-001 getGraphTaxonomy

- 方法：GET
- 路径：`/api/v1/graphs/taxonomy`
- Query：`projectId?`、`includeDisabled=false`
- 成功响应 data：

```json
{
  "categories": [],
  "entityTypes": [],
  "relationTypes": []
}
```

错误码：`ICSS-AUTH-403-PROJECT_DENIED`。

### KG-002 createKnowledgeSource

- 方法：POST
- 路径：`/api/v1/knowledge/sources`
- Content-Type：`application/json` 或 `multipart/form-data`
- 必填：`graphCategoryId`、`sourceType`、`sensitivityLevel`
- 二选一：`rawText` 或 `file`

请求 JSON（示例中 `<category-id>` 为租户已注册的分类名）：

```json
{
  "graphCategoryId": "<category-id>",
  "sourceType": "text",
  "rawText": "<上传文本>",
  "sensitivityLevel": "internal"
}
```

成功响应 data：

```json
{
  "sourceId": "uuid",
  "status": "uploaded",
  "parserStatus": "pending",
  "extractStatus": "pending",
  "graphBuildStatus": "pending",
  "graphCategoryId": "<category-id>"
}
```

错误码：`ICSS-KG-400-CATEGORY_INVALID`、`ICSS-KG-400-UNSUPPORTED_SOURCE_TYPE`、`ICSS-KG-400-FILE_INVALID`、`ICSS-KNOW-409-SOURCE_DUPLICATED`。

### KG-003 getKnowledgeSourceBlocks

- 方法：GET
- 路径：`/api/v1/knowledge/sources/{sourceId}/blocks`
- Query：`blockType?`、`keyword?`、`pageNo`、`pageSize`
- 成功响应：统一分页，items 为 `KnowledgeBlock`。
- 权限：客服默认不可见 `rawText`，运维/管理员可见原文摘要，审计可按权限导出。

错误码：`ICSS-KG-404-SOURCE_NOT_FOUND`、`ICSS-AUTH-403-PROJECT_DENIED`。

### KG-004 getKnowledgeSourceCandidates

- 方法：GET
- 路径：`/api/v1/knowledge/sources/{sourceId}/candidates`
- Query：`objectType=entity|relation|all`、`status?`、`entityType?`、`relationType?`
- 成功响应 data：

```json
{
  "sourceId": "uuid",
  "entities": [],
  "relations": [],
  "summary": {
    "entityCount": 3,
    "relationCount": 2,
    "reviewingCount": 1,
    "conflictCount": 0
  }
}
```

错误码：`ICSS-KG-404-SOURCE_NOT_FOUND`。

### KG-005 runKnowledgeSourceAction

- 方法：POST
- 路径：`/api/v1/knowledge/sources/{sourceId}/actions`
- Action：`parse`、`extract`、`normalize`、`build`、`publish`、`retry`
- 幂等：必填 `X-Idempotency-Key`

请求 Body：

```json
{
  "action": "extract",
  "options": {
    "force": false,
    "useLlm": true
  }
}
```

成功响应 data：`sourceId`、`taskId`、`taskType`、`status`、`progress`。

错误码：`ICSS-KG-404-SOURCE_NOT_FOUND`、`ICSS-KNOW-409-TASK_RUNNING`、`ICSS-KG-422-PARSE_FAILED`、`ICSS-KG-422-EXTRACTION_EMPTY`、`ICSS-KG-502-AI_SERVICE_UNAVAILABLE`。

### KG-006 queryGraphAssets

- 方法：GET
- 路径：`/api/v1/graphs/assets`
- Query：`graphCategoryId?`、`entityType?`、`relationType?`、`keyword?`、`status?`、`pageNo`、`pageSize`
- 成功响应：统一分页，items 含 `graphId`、`graphName`、`graphCategoryId`、`status`、`activeRevisionId`、`nodeCount`、`edgeCount`、`confidence`。

错误码：`ICSS-GRAPH-400-INVALID_QUERY`。

### KG-007 getGraphAssetDetail

- 方法：GET
- 路径：`/api/v1/graphs/assets/{graphId}`
- Query：`revisionId?`、`includeEvidence=false`
- 成功响应 data：`graphId`、`status`、`activeRevisionId`、`revisions`、`nodes`、`edges`、`sourceRefs`。

错误码：`ICSS-KG-404-GRAPH_NOT_FOUND`。

### KG-008 updateGraphDraft

- 方法：PATCH
- 路径：`/api/v1/graphs/assets/{graphId}/draft`
- 幂等：必填 `X-Idempotency-Key`
- 说明：只保存草稿，不发布。

请求 Body：

```json
{
  "baseRevisionId": "uuid",
  "nodeChanges": [],
  "edgeChanges": [],
  "editReason": "<编辑原因>"
}
```

错误码：`ICSS-KG-404-GRAPH_NOT_FOUND`、`ICSS-KG-409-REVISION_CONFLICT`、`ICSS-GRAPH-409-REVISION_LOCKED`。

### KG-009 runGraphVersionAction

- 方法：POST
- 路径：`/api/v1/graphs/assets/{graphId}/versions/actions`
- Action：`publish`、`rollback`、`deprecate`
- 幂等：必填 `X-Idempotency-Key`

请求 Body：

```json
{
  "action": "publish",
  "targetRevisionId": "uuid",
  "comment": "发布设备告警图谱"
}
```

错误码：`ICSS-KG-409-UNRESOLVED_CONFLICT`、`ICSS-KG-409-REVISION_CONFLICT`、`ICSS-KG-500-NEO4J_WRITE_FAILED`。

### KG-010 searchDiagnosisGraph

- 方法：POST
- 路径：`/api/v1/graphs/search/diagnosis`
- 说明：诊断检索只能命中 `published + activeRevision` 图谱。

请求 Body（示例中占位为租户自己已注册的名称）：

```json
{
  "questionText": "<问题描述>",
  "context": {
    "entities": [
      {"entityType": "<EntityTypeA>", "entityId": "<id>"}
    ],
    "timeRange": {"start": "2026-05-22T00:00:00+08:00", "end": "2026-05-23T00:00:00+08:00"}
  },
  "graphCategoryIds": ["<category-id>"],
  "maxDepth": 3
}
```

成功响应 data：

```json
{
  "matchedEntities": [],
  "graphPaths": [],
  "sourceEvidence": [],
  "vectorEvidence": [],
  "suggestedMcpCapabilities": [],
  "confidence": 0.86,
  "degraded": false,
  "queryId": "uuid"
}
```

错误码：`ICSS-KG-400-INVALID_SEARCH`、`ICSS-KG-500-VECTOR_QUERY_FAILED`、`ICSS-SYS-503-DOWNSTREAM_UNAVAILABLE`。

### KG-011 getGraphNeighbors

- 方法：GET
- 路径：`/api/v1/graphs/entities/{entityId}/neighbors`
- Query：`entityType`、`graphCategoryId?`、`relationTypes?`、`depth=1`、`revisionId?`
- 限制：`depth` 最大 3。

### KG-012 getGraphPaths

- 方法：GET
- 路径：`/api/v1/graphs/paths`
- Query：`sourceEntityId`、`targetEntityId`、`sourceEntityType?`、`targetEntityType?`、`maxDepth=3`、`graphCategoryId?`
- 成功响应 data：`paths`。

### KG-013 getGraphEvidence

- 方法：GET
- 路径：`/api/v1/graphs/evidence`
- Query：`entityId?`、`relationId?`、`evidenceRef?`
- 成功响应 data：`sourceId`、`blockId`、`pageNo`、`sectionPath`、`rawTextSummary`、`metadata`。
- 字段权限：无原文权限时只返回摘要和脱敏原因。

### KG-014 listGraphReviewTasks

- 方法：GET
- 路径：`/api/v1/graphs/review-tasks`
- Query：`graphCategoryId?`、`objectType?`、`reasonCode?`、`priority?`、`status?`、`pageNo`、`pageSize`
- 成功响应：统一分页。

### KG-015 updateGraphReviewTask

- 方法：PATCH
- 路径：`/api/v1/graphs/review-tasks/{taskId}`
- 幂等：必填 `X-Idempotency-Key`

请求 Body：

```json
{
  "decision": "approved",
  "decisionPayload": {
    "canonicalName": "<规范名>",
    "mergeToCandidateId": null
  },
  "comment": "<复核说明>"
}
```

错误码：`ICSS-KG-404-REVIEW_TASK_NOT_FOUND`、`ICSS-COMMON-400-INVALID_PARAMETER`、`ICSS-AUTH-403-OPERATION_DENIED`。

### KG-016 createGraphCategory

- 方法：POST；路径：`/api/v1/graphs/taxonomy/categories`。
- 必填：`categoryId`、`categoryName`、`domain`、`entityTypeScope`、`relationTypeScope`。
- 作用域：`(X-Project-Id)` 项目级；不允许 `projectId='*'`。
- 参考 `4.1 TaxonomyCategory`。
- 所有 `entityTypeScope` / `relationTypeScope` 中的名称必须已在本租户中注册，否则返回 `ICSS-KG-400-ENTITY_TYPE_INVALID` / `ICSS-KG-400-RELATION_TYPE_INVALID`。
- 冲突：`(tenantId, projectId, categoryId)` 唯一，已存在返回 `ICSS-KG-409-CATEGORY_DUPLICATED`。

### KG-017 updateGraphCategory

- 方法：PUT；路径：`/api/v1/graphs/taxonomy/categories/{categoryId}`。
- 允许修改：`categoryName`、`domain`、`description`、`entityTypeScope`、`relationTypeScope`、`status`、`sortOrder`。
- 不允许修改：`categoryId`。

### KG-018 deleteGraphCategory

- 方法：DELETE；路径：`/api/v1/graphs/taxonomy/categories/{categoryId}`。
- 语义：软删除（`status='disabled'`）。错误码：`ICSS-KG-404-CATEGORY_NOT_FOUND`、`ICSS-KG-409-CATEGORY_IN_USE`。

### KG-019 manageEntityType

- 路径：`POST /api/v1/graphs/taxonomy/entity-types`、`PUT /...{entityType}`、`DELETE /...{entityType}`。
- Body 参照 07F §7.3 示例。
- 保护类型 `SourceBlock`/`Document`/`Section` 不允许修改唯一键/删除，返回 `ICSS-KG-403-PROTECTED_TYPE`。
- 名称正则：`^[A-Z][A-Za-z0-9]{0,63}$`，不符返回 `ICSS-KG-400-NAME_INVALID`。
- 删除拒绝场景：该实体类型已被业务 graph_category/graph_relation_type/knowledge_source 引用返回 `ICSS-KG-409-ENTITY_TYPE_IN_USE`。

### KG-020 manageRelationType

- 路径：`POST /api/v1/graphs/taxonomy/relation-types`、`PUT /...{relationType}`、`DELETE /...{relationType}`。
- Body 参照 07F §7.3 示例。
- 保护关系 `HAS_EVIDENCE`/`HAS_SECTION`/`IN_SECTION` 不允许修改 from/to 类型集合与删除，返回 `ICSS-KG-403-PROTECTED_TYPE`。
- 名称正则：`^[A-Z][A-Z0-9_]{0,63}$`。
- `fromEntityTypes` / `toEntityTypes` 中的名称必须已注册或为通配 `*`。

### KG-021 importTaxonomy

- 方法：POST；路径：`/api/v1/graphs/taxonomy/import`。
- Body：`{ "dryRun": true, "entityTypes":[...], "relationTypes":[...], "categories":[...] }`；默认 `dryRun=true`。
- 幂等：必填 `X-Idempotency-Key`。
- 响应：`{ inserted, updated, skipped, errors[] }`；存在错误且非 dryRun 时事务回滚。

## 7. Mock 场景（占位本体表述）

| 场景 | 触发 | 预期 |
| --- | --- | --- |
| kg_empty_taxonomy | 初始部署后调用 KG-001 | `categories=[]`、`entityTypes=[仅平台保护类型]`、`relationTypes=[仅平台保护关系]` |
| kg_admin_register | 调用 KG-019/KG-020/KG-016 依次注册一条实体类型、关系类型、分类 | KG-001 可查到新注册项 |
| kg_protected_type_locked | 调用 KG-019 DELETE `SourceBlock` | 返回 `ICSS-KG-403-PROTECTED_TYPE` |
| kg_invalid_scope | KG-016 传入 `entityTypeScope` 含未注册名称 | 返回 `ICSS-KG-400-ENTITY_TYPE_INVALID` |
| kg_success_structured | 以租户已注册本体为前提，上传表述两实体关系的结构化文本 | 生成对应 candidate 实体与 candidate 关系 |
| kg_success_file | 上传 md/csv/xlsx/docx/pdf | 生成 blocks 与 candidates |
| kg_low_confidence | OCR 或 LLM 置信度低 | 生成 review task，不允许直接发布 |
| kg_conflict_relation | 同一实体被两份资料绑定到不同目标实体 | candidate relation 为 conflict，发布 409 |
| kg_publish_success | 无冲突且 evidenceRefs 完整 | activeRevisionId 生效 |
| kg_rollback_success | 指定旧版本回滚 | diagnosis search 命中回滚后版本 |
| kg_project_denied | 跨项目查询 | 403 或空结果，不泄露详情 |

## 8. 下游实现约束

1. 前端所有分类、实体类型、关系类型必须来自 KG-001；**禁止在前端代码中硬编码任何业务本体名称**，遇到空 taxonomy 需引导用户到管理页面执行 KG-016 到 KG-021 注册。
2. Java 后端是唯一发布图谱和写 Neo4j 的入口；在接受 candidate/source 创建请求时，必须校验 `entityType`/`relationType`/`graphCategoryId` 均已在本租户 taxonomy 中注册。
3. Python AI Service 只能返回 blocks/candidates/plans；candidates 中必须携带调用方传入的本体名称，不得比输入多出新名称。
4. `vectorEvidence` 不能被显示成真实关系，只能显示为语义证据。
5. 没有 `evidenceRefs` 的关系禁止进入 `published`。
6. 平台保护类型（`SourceBlock`/`Document`/`Section` 及 `HAS_EVIDENCE`/`HAS_SECTION`/`IN_SECTION`）仅可读，任何修改/删除请求返回 `ICSS-KG-403-PROTECTED_TYPE`。
