# 接口 Agent 任务书

## 1. 角色边界

接口 Agent 只负责 API 契约、请求响应、错误码、权限、Mock 场景和 SSE/状态事件说明，不写 Controller 实现、不改数据库 SQL、不改前端页面。

## 2. 必读文件

1. `03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md` §13、§14、§18、§22
2. `04_数据库与API/09_API文档.md`
3. `04_数据库与API/09B_接口规范.md`
4. `04_数据库与API/09C_错误码登记表.md`
5. `05_开发计划与Agent任务/21_真实知识图谱构建与检索任务包/02_任务总表.md`

## 3. 输出位置

| 类型 | 路径 |
| --- | --- |
| API 主文档 | `04_数据库与API/09_API文档.md` |
| 错误码 | `04_数据库与API/09C_错误码登记表.md` |
| Mock 场景 | `04_数据库与API/09D~09I` 中合适文件，或新增 `09J_真实知识图谱API补充契约.md` |

## 4. 必须补齐的 API

| 编号 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| KG-001 | GET | `/api/v1/graphs/taxonomy` | 图谱分类、本体、关系类型配置 |
| KG-002 | POST | `/api/v1/knowledge/sources` | 创建知识源，必须带 graphCategoryId |
| KG-003 | GET | `/api/v1/knowledge/sources/{sourceId}/blocks` | 查看解析块 |
| KG-004 | GET | `/api/v1/knowledge/sources/{sourceId}/candidates` | 查看候选实体/关系 |
| KG-005 | POST | `/api/v1/knowledge/sources/{sourceId}/actions` | parse/extract/build/publish/retry |
| KG-006 | GET | `/api/v1/graphs/assets` | 分类/实体/关系筛选 |
| KG-007 | GET | `/api/v1/graphs/assets/{graphId}` | 图谱详情，含 nodes/edges/sourceRefs/revisions |
| KG-008 | PATCH | `/api/v1/graphs/assets/{graphId}/draft` | 保存草稿节点关系 |
| KG-009 | POST | `/api/v1/graphs/assets/{graphId}/versions/actions` | publish/rollback/deprecate |
| KG-010 | POST | `/api/v1/graphs/search/diagnosis` | 诊断检索子图 |
| KG-011 | GET | `/api/v1/graphs/entities/{entityId}/neighbors` | 实体邻居 |
| KG-012 | GET | `/api/v1/graphs/paths` | source-target 路径查询 |
| KG-013 | GET | `/api/v1/graphs/evidence` | 证据块溯源 |
| KG-014 | GET | `/api/v1/graphs/review-tasks` | 低置信度/冲突复核任务 |
| KG-015 | PATCH | `/api/v1/graphs/review-tasks/{taskId}` | 复核通过/驳回/合并 |

## 5. 每个 API 必须包含

1. 业务说明。
2. 请求头：`X-Project-Id`、`Idempotency-Key` 是否必填。
3. 权限：查看、上传、编辑、发布、回滚、复核分别需要什么权限。
4. 请求参数：字段名、类型、必填、枚举、长度。
5. 响应结构：必须含 `sourceId`、`blockId`、`candidateId`、`graphId`、`revisionId`、`confidence`、`status` 等关键链路字段。
6. 错误码：至少覆盖 400、403、404、409、422、500、502。
7. Mock 场景：成功、低置信度、冲突、解析失败、越权、发布失败。

## 6. 状态枚举要求

必须明确以下状态，不允许前端/后端自行发明：

```text
knowledge_source: uploaded/parsing/extracted/graph_ready/published/failed
knowledge_ingestion_task: pending/running/success/failed/canceled
candidate: candidate/accepted/rejected/conflict/merged/reviewing
graph_asset: draft/reviewing/published/deprecated/rolled_back
review_task: pending/approved/rejected/merged
```

## 7. 错误码建议

| 错误码 | 场景 |
| --- | --- |
| ICSS-KG-4001 | graphCategoryId 缺失或不存在 |
| ICSS-KG-4002 | sourceType 不支持 |
| ICSS-KG-4003 | 文件大小或格式超限 |
| ICSS-KG-4031 | 无图谱查看权限 |
| ICSS-KG-4032 | 无图谱发布/回滚权限 |
| ICSS-KG-4041 | sourceId 不存在 |
| ICSS-KG-4042 | graphId/revisionId 不存在 |
| ICSS-KG-4091 | 存在未处理冲突，禁止发布 |
| ICSS-KG-4092 | revision 并发冲突 |
| ICSS-KG-4221 | 解析失败 |
| ICSS-KG-4222 | 抽取结果为空 |
| ICSS-KG-4223 | 候选关系不符合本体约束 |
| ICSS-KG-5001 | Neo4j 写入失败 |
| ICSS-KG-5002 | pgvector 查询失败 |
| ICSS-KG-5021 | AI Service 解析/抽取服务不可用 |

## 8. 禁止事项

1. 不要把 API 响应设计成任意 JSON，无字段说明。
2. 不要把候选实体和发布实体混在同一个状态里。
3. 不要让 `draft` 图谱出现在诊断检索响应中。
4. 不要省略 evidence/source 字段。
5. 不要设计前端直接传 Cypher 的接口。

## 9. 下游交接

交接给后端/前端 Agent 时必须说明：

1. 哪些接口先实现真实，哪些可保留 Mock。
2. 每个接口的权限和幂等要求。
3. graph node/edge 前端数据结构。
4. 诊断检索返回的 `graphPaths` 与 `evidenceItems` 如何对应。
5. 发布/回滚接口的并发控制方式。

## 8. V0.3.1 增量任务：KG-API-004

**输出**：`04_数据库与API/09_API文档.md`、`04_数据库与API/09J_KG_ADMIN接口.md`、`04_数据库与API/09C_错误码登记表.md`。参考 07F §12.1 / §13.2A / §13.4。

### 8.1 新增接口

| 编号 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| KG-022 | POST | `/api/v1/graphs/entities/{entityId}/actions` | body `{action: "freeze\|unfreeze", reason?: string}`；freeze 需 `graph:freeze`，unfreeze 需 `graph:unfreeze`；响应 `{entityId, status, lastModifiedAt}` |
| KG-023 | POST | `/api/v1/graphs/relations/{relationId}/actions` | 同上，作用于关系 |

### 8.2 KG-010 诊断检索 请求 / 响应 增量

```jsonc
// 请求体可选 traversalBudget
{
  "questionText": "...",
  "traversalBudget": {
    "maxNodes": 300,
    "maxEdges": 800,
    "maxDepthHardCap": 5,
    "maxFanOutPerNode": 80,
    "timeoutMs": 1500
  }
}
```

```jsonc
// 响应体增量字段
{
  "graphPaths": [...],
  "sourceEvidence": [{"blockId": "...", "weight": 0.85, "sourceType": "paragraph"}],
  "vectorEvidence": [{"blockId": "...", "weight": 0.72, "sourceType": "table_cell"}],
  "hybridScore": {"vector": 0.45, "bm25": 0.30, "graph": 0.20, "recency": 0.05},
  "budgetUsage": {"visitedNodes": 280, "visitedEdges": 700, "truncated": false},
  "embeddingModel": "bge-large-zh-v1.5",
  "embeddingVersion": "2024Q4"
}
```

### 8.3 KG-005 (block 上传) 增量要求

- 请求 body 增 `embeddingModel` `embeddingVersion`（必填）、`parentBlockId`（可选）。
- 响应回按原样 echo。

### 8.4 错码补充（同步到 09C）

| 错码 | HTTP | 含义 |
| --- | --- | --- |
| ICSS-KG-409-FROZEN_NODE | 409 | 目标实体/关系处于 frozen，拒绝自动合并/规范化 |
| ICSS-KG-422-EMBEDDING_VERSION_MISMATCH | 422 | 查询与库中 embedding_model/version 不一致 |
| ICSS-KG-413-BUDGET_EXCEEDED | 413 | Traversal Budget 超限（与响应 `truncated=true` 区别：budget=0 或 timeout 走本错码） |
| ICSS-KG-403-FREEZE_FORBIDDEN | 403 | 调用者缺 `graph:freeze`/`graph:unfreeze` 权限 |

### 8.5 Evidence 返回体统一

所有 evidence 返回体（KG-014 证据抽屉、KG-015 可视化、KG-010 诊断检索）统一为 `{blockId: string, weight: number, sourceType: "title\|paragraph\|table_cell\|table_caption\|list_item\|code\|json_field\|csv_cell\|ocr\|kv_pair"}`，weight 范围 [0,1]。
