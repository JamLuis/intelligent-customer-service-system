# 真实知识图谱 API 补充契约

## 1. 文档信息

- 文档编号：09J
- 适用范围：真实知识图谱构建与检索 V0.2
- Base Path：`/api/v1`
- 上游依据：`03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md`、KG-API-001、KG-API-002
- 依赖数据库：`real_kg_schema.sql`、`graph_taxonomy_seed.sql`
- 设计原则：真实关系图谱优先；pgvector 只作为语义召回和证据补充；LLM 抽取结果只能作为候选，不能直接发布。

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
  "categoryId": "device-alarm",
  "categoryName": "设备与告警",
  "domain": "event",
  "description": "维护设备、告警、规则和阈值关系",
  "entityTypeScope": ["Device", "Alarm", "AlarmRule"],
  "relationTypeScope": ["RAISED_BY", "BOUND_TO", "TRIGGERS"],
  "status": "enabled",
  "sortOrder": 40
}
```

### 4.2 KnowledgeBlock

```json
{
  "blockId": "uuid",
  "sourceId": "uuid",
  "graphCategoryId": "device-alarm",
  "blockType": "paragraph",
  "sectionPath": "第2章/告警规则",
  "pageNo": 3,
  "rowNo": null,
  "colNo": null,
  "rawText": "TC-003 绑定超载告警规则 AR-17",
  "normalizedText": "设备 TC-003 绑定 告警规则 AR-17",
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
  "graphCategoryId": "device-alarm",
  "entityType": "Device",
  "rawName": "TC-003",
  "canonicalName": "TC-003",
  "uniqueKey": {"deviceId": "TC-003"},
  "properties": {"deviceType": "temperature_sensor"},
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
  "graphCategoryId": "device-alarm",
  "relationType": "BOUND_TO",
  "properties": {"threshold": "90%", "enabled": true},
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
      "id": "Device:TC-003",
      "entityId": "TC-003",
      "entityType": "Device",
      "name": "TC-003",
      "aliases": ["TC003"],
      "graphCategoryIds": ["device-alarm"],
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
      "source": "Device:TC-003",
      "target": "AlarmRule:AR-17",
      "relationType": "BOUND_TO",
      "sourceRefs": ["sourceId"],
      "evidenceRefs": ["blockId"],
      "confidence": 0.91,
      "status": "published",
      "revisionId": "uuid",
      "properties": {"threshold": "90%"}
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

请求 JSON：

```json
{
  "graphCategoryId": "device-alarm",
  "sourceType": "text",
  "rawText": "TC-003 绑定 AR-17，阈值 90%",
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
  "graphCategoryId": "device-alarm"
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
  "editReason": "修正 TC-003 绑定关系"
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

请求 Body：

```json
{
  "questionText": "TC-003 超载告警不准",
  "context": {
    "deviceId": "TC-003",
    "vesselId": "MINX-001",
    "timeRange": {"start": "2026-05-22T00:00:00+08:00", "end": "2026-05-23T00:00:00+08:00"}
  },
  "graphCategoryIds": ["vessel-device", "device-alarm", "device-protocol"],
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
    "canonicalName": "TC-003",
    "mergeToCandidateId": null
  },
  "comment": "证据明确，允许入图"
}
```

错误码：`ICSS-KG-404-REVIEW_TASK_NOT_FOUND`、`ICSS-COMMON-400-INVALID_PARAMETER`、`ICSS-AUTH-403-OPERATION_DENIED`。

## 7. Mock 场景

| 场景 | 触发 | 预期 |
| --- | --- | --- |
| kg_success_structured | 结构化文本 TC-003 绑定 AR-17 | 生成 Device、AlarmRule、BOUND_TO |
| kg_success_file | 上传 md/csv/xlsx/docx/pdf | 生成 blocks 与 candidates |
| kg_low_confidence | OCR 或 LLM 置信度低 | 生成 review task，不允许直接发布 |
| kg_conflict_relation | TC-003 被两份资料绑定到不同船舶 | candidate relation 为 conflict，发布 409 |
| kg_publish_success | 无冲突且 evidenceRefs 完整 | activeRevisionId 生效 |
| kg_rollback_success | 指定旧版本回滚 | diagnosis search 命中回滚后版本 |
| kg_project_denied | 跨项目查询 | 403 或空结果，不泄露详情 |

## 8. 下游实现约束

1. 前端所有分类、实体类型、关系类型必须来自 KG-001。
2. Java 后端是唯一发布图谱和写 Neo4j 的入口。
3. Python AI Service 只能返回 blocks/candidates/plans。
4. `vectorEvidence` 不能被显示成真实关系，只能显示为语义证据。
5. 没有 `evidenceRefs` 的关系禁止进入 `published`。
