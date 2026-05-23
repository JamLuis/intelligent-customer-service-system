# Intelligent Customer Service System API 文档

## 1. 文档信息

- 版本：V0.1 P0 API 契约
- Base Path：`/api/v1`
- 编号规则：接口编号与 01_需求与业务分析/04D_产品交付衔接清单.md 保持一致；需要拆分的查询能力作为同一接口下的子路径，不新增产品编号。
- 接口边界：V0.1 使用 Mock + Adapter，真实 wnx 接入后只替换 Adapter / capability，不改变前端 API。
- 不包含范围：真实生产写操作、完整审批流执行、直接修改 wnx-serve 生产配置。
- 关联文档：09B_接口规范.md、09C_错误码登记表.md、09D_Mock场景清单.md、09E_接口字段映射表.md、09F_MCP能力契约.md、09G_SSE事件契约.md、09J_真实知识图谱API补充契约.md。

## 2. 接口清单

| 编号 | 接口名称 | 方法 | 路径 | 权限标识 | 状态 |
| --- | --- | --- | --- | --- | --- |
| API-001 | createSupportSession | POST | `/api/v1/support/sessions` | `session:create` | P0 |
| API-002 | updateSessionContext | PATCH | `/api/v1/support/sessions/{sessionId}/context` | `session:update` | P0 |
| API-003 | startDiagnosis | POST | `/api/v1/diagnosis/cases` | `diagnosis:start` | P0 |
| API-004 | getDiagnosisCase | GET | `/api/v1/diagnosis/cases/{caseId}` | `diagnosis:view` | P0 |
| API-005 | createApprovalOrder | POST | `/api/v1/approvals` | `approval:create` | P1 占位 |
| API-006 | reviewApprovalOrder | PATCH | `/api/v1/approvals/{approvalId}/review` | `approval:review` | P1 占位 |
| API-007 | executeApprovedAction | POST | `/api/v1/executions` | `execution:run` | V0.3 暂缓 |
| API-008 | publishKnowledgeCase | POST | `/api/v1/knowledge/cases` | `knowledge:publish_case` | P1 占位 |
| API-009 | getExecutionTrace | GET | `/api/v1/traces/{traceId}` | `trace:view_summary` | P0 |
| API-010 | createKnowledgeSource | POST | `/api/v1/knowledge/sources` | `knowledge:upload` | P0 |
| API-011 | getKnowledgeIngestionTask | GET | `/api/v1/knowledge/sources/{sourceId}/tasks` | `knowledge:view` | P0 |
| API-012 | queryGraphAsset | GET | `/api/v1/graphs/assets` | `graph:view` | P0 |
| API-012A | listGraphCategories | GET | `/api/v1/graphs/assets/categories` | `graph:view` | P0 |
| API-013 | updateGraphAssetDraft | PATCH | `/api/v1/graphs/assets/{graphId}/draft` | `graph:edit` | P0 |
| API-014 | publishOrRollbackGraph | POST | `/api/v1/graphs/assets/{graphId}/versions/actions` | `graph:publish` / `graph:rollback` | P0 |
| API-015 | listMcpCapabilities | GET | `/api/v1/mcp/capabilities` | `mcp:view` | P0 |
| API-016 | updateMcpCapabilityStatus | PATCH | `/api/v1/mcp/capabilities/{capabilityId}/status` | `mcp:update_status` | P0 |
| API-017 | updateGraphMcpMapping | PUT | `/api/v1/graph-mcp-mappings/{mappingId}` | `mcp:update_mapping` | P0 设计占位 |
| API-018 | updateRouteTemplate | PATCH | `/api/v1/routes/{routeId}/status` | `route:publish` / `route:disable` | P0 |
| API-019 | submitAnswerFeedback | POST | `/api/v1/routes/evaluations` | `route:feedback` | P0 |

真实知识图谱 V0.2 增量接口 `KG-001` ~ `KG-015` 已单独收敛到 `09J_真实知识图谱API补充契约.md`，后续后端和前端实现应优先引用 09J，不得继续按 Mock JSON 图谱扩展接口。

## 3. 通用要求

| 项 | 要求 |
| --- | --- |
| 鉴权 | 所有接口必须携带 `Authorization: Bearer <token>`；涉及项目数据时必须携带 `X-Project-Id` |
| 幂等 | 所有写接口必须携带 `X-Idempotency-Key` |
| 分页 | 列表统一使用 `pageNo/pageSize/sortBy/sortOrder` 和 `items/pageNo/pageSize/total/hasNext` |
| 错误码 | 必须引用 09C，不允许新增未登记错误码 |
| 字段脱敏 | 原始 MCP 返回、对象存储 key、审计 detailPayload 按角色分层返回 |
| Mock | V0.1 必须覆盖成功、上下文不足、MCP 降级、项目越权、字段越权 |

## 4. 接口详情

### API-001 createSupportSession

- 业务说明：创建问题会话，完成初步分类、实体抽取和上下文缺口识别。
- 关联功能：M1-F1。
- 调用方：前端问答工作台。
- 鉴权：`session:create` + 项目权限。
- 幂等：必填 `X-Idempotency-Key`。
- 限流：单用户 30 次/分钟。
- 字段映射：`questionText -> support_session.question_text`，`projectId -> support_session.project_id`，`entities -> support_session.entities`，`missingFields -> support_session.missing_fields`。

请求 Body：

| 参数 | 类型 | 必填 | 规则 | 说明 |
| --- | --- | --- | --- | --- |
| questionText | string | 是 | 1-2000 | 用户问题 |
| projectId | string | 是 | 必须授权 | 项目标识 |
| deviceId | string | 否 | 1-64 | 设备标识 |
| context | object | 否 | JSON object | 时间范围、页面、业务对象等上下文 |

成功响应 data：`sessionId`、`status`、`issueCategory`、`entities`、`missingFields`。

错误码：`ICSS-DIAG-400-QUESTION_EMPTY`、`ICSS-AUTH-403-PROJECT_DENIED`、`ICSS-COMMON-409-DUPLICATE_REQUEST`。

Mock：`success` 返回 ready；`context_required` 返回 waiting_context 和 missingFields。

### API-002 updateSessionContext

- 业务说明：补充追问答案，让会话从 `waiting_context` 进入 `ready`。
- 关联功能：M1-F2。
- 鉴权：`session:update` + 项目权限。
- 幂等：必填 `X-Idempotency-Key`。
- 字段映射：`followUpAnswer/contextPatch -> support_session.context_payload`，`missingFields -> support_session.missing_fields`。

Path：`sessionId`。

请求 Body：`followUpAnswer`、`contextPatch`。

成功响应 data：`sessionId`、`status`、`missingFields`、`entities`。

错误码：`ICSS-DIAG-404-SESSION_NOT_FOUND`、`ICSS-COMMON-400-INVALID_PARAMETER`。

### API-003 startDiagnosis

- 业务说明：基于会话启动诊断，创建诊断案例、执行轨迹和步骤日志。
- 关联功能：M2-F1、M3-F1、M6-F1。
- 鉴权：`diagnosis:start` + 项目权限。
- 幂等：必填 `X-Idempotency-Key`。
- 事务边界：需要创建 `diagnostic_case`、`execution_trace` 初始记录；后续步骤可异步补写。
- 字段映射：`sessionId -> diagnostic_case.session_id`，`caseId -> diagnostic_case.case_id`，`traceId -> execution_trace.trace_id`。

请求 Body：`sessionId`、`preferRouteId`、`mockScenario`。

成功响应 data：`caseId`、`traceId`、`caseStatus`、`traceStatus`、`sseUrl`。

错误码：`ICSS-DIAG-409-CONTEXT_REQUIRED`、`ICSS-DIAG-404-SESSION_NOT_FOUND`、`ICSS-DIAG-409-CASE_NOT_READY`。

补充子路径：`GET /api/v1/diagnosis/cases/{caseId}/events`，用于 SSE 诊断进度，事件格式见 09G。

### API-004 getDiagnosisCase

- 业务说明：查询诊断结果、证据摘要、建议动作和最近执行轨迹。
- 关联功能：M3-F1。
- 鉴权：`diagnosis:view` + 项目权限。
- 字段映射：`rootCause -> diagnostic_case.root_cause`，`confidenceScore -> diagnostic_case.confidence_score`，`evidenceItems -> diagnostic_case.evidence_summary`。

Path：`caseId`；Query：`includeLatestTrace`。

成功响应 data：`caseId`、`sessionId`、`status`、`rootCause`、`confidenceScore`、`evidenceItems`、`recommendedActions`、`latestTrace`。

错误码：`ICSS-DIAG-404-CASE_NOT_FOUND`、`ICSS-AUTH-403-PROJECT_DENIED`。

### API-005 createApprovalOrder

- 状态：P1 占位，V0.1 不进入实现。
- 原因：审批系统归属未定。
- V0.1 Mock：允许前端看到“需要审批”状态，但不创建真实审批流。
- 错误码：`ICSS-AUTH-403-OPERATION_DENIED`。

### API-006 reviewApprovalOrder

- 状态：P1 占位，V0.1 不进入实现。
- 原因：审批系统归属未定。
- V0.1 Mock：返回固定审批状态，不产生真实执行动作。

### API-007 executeApprovedAction

- 状态：V0.3 暂缓。
- 原因：V0.1 禁止真实生产写操作。
- 约束：后续所有执行类动作默认 L4/L5，必须审批通过、白名单允许、审计可追溯。

### API-008 publishKnowledgeCase

- 状态：P1 占位。
- 原因：V0.1 优先做知识源录入与图谱入图，不做完整案例库发布流程。
- V0.1 替代：使用 API-010 / API-011 / API-012 完成知识源生命周期。

### API-009 getExecutionTrace

- 业务说明：查询执行轨迹、图谱路径、推理步骤和 MCP 调用摘要。
- 关联功能：M6-F1。
- 鉴权：`trace:view_summary`；步骤明细需 `trace:view_detail`；原始返回引用需 `trace:view_raw`。
- 字段映射：`traceId -> execution_trace.trace_id`，`graphPaths -> execution_trace.graph_paths`，`mcpCalls -> mcp_call_log`。

Path：`traceId`；Query：`includeSteps`、`includeMcpCalls`、`includeRawRef`。

成功响应 data：`traceId`、`caseId`、`routeId`、`status`、`graphPaths`、`reasoningSteps`、`mcpCalls`、`degraded`、`failureReason`、`durationMs`。

错误码：`ICSS-TRACE-404-NOT_FOUND`、`ICSS-TRACE-409-INCOMPLETE`、`ICSS-AUTH-403-FIELD_DENIED`。

子路径：

| 子能力 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 步骤分页 | GET | `/api/v1/traces/{traceId}/steps` | 返回 execution_trace_step |
| MCP 调用分页 | GET | `/api/v1/traces/{traceId}/mcp-calls` | 返回 mcp_call_log |

### API-010 createKnowledgeSource

- 业务说明：上传文件或提交文本知识源。
- 关联功能：M8-F1。
- 鉴权：`knowledge:upload`。
- 幂等：必填 `X-Idempotency-Key`。
- Content-Type：`multipart/form-data` 或 `application/json`。
- 事务边界：创建 `knowledge_source` 和首个 `knowledge_ingestion_task`。

请求字段：`sourceType`、`file`、`rawText`、`graphCategoryId`、`graphCategoryName`、`sensitivityLevel`。

成功响应 data：`sourceId`、`status`、`parserStatus`、`extractStatus`、`graphBuildStatus`、`graphCategoryId`。

错误码：`ICSS-KNOW-400-UNSUPPORTED_SOURCE_TYPE`、`ICSS-KNOW-413-FILE_TOO_LARGE`、`ICSS-KNOW-409-SOURCE_DUPLICATED`。

### API-011 getKnowledgeIngestionTask

- 业务说明：查询知识源解析、OCR、抽取、入图任务状态。
- 关联功能：M8-F1。
- 鉴权：`knowledge:view`。
- 字段映射：`sourceId -> knowledge_source.source_id`，`tasks -> knowledge_ingestion_task`。

Path：`sourceId`；Query：`taskType`、`status`、`pageNo`、`pageSize`。

成功响应 data：统一分页，items 包含 `taskId`、`taskType`、`status`、`progress`、`resultPayload`、`errorMessage`、`startedAt`、`completedAt`。

错误码：`ICSS-KNOW-404-SOURCE_NOT_FOUND`、`ICSS-KNOW-409-TASK_RUNNING`。

补充子路径：`POST /api/v1/knowledge/sources/{sourceId}/retry`，用于重跑解析/抽取/入图，权限 `knowledge:retry`。

### API-012 queryGraphAsset

- 业务说明：查询图谱节点、关系、子图、来源和版本摘要。
- 关联功能：M7-F1。
- 鉴权：`graph:view`。
- 分页：统一分页。
- 字段映射：`graphId -> graph_asset.graph_id`，`sourceRefs -> graph_asset.source_refs`，`version -> graph_revision.revision_no`。

Query：`keyword`、`graphCategoryId`、`entityType`、`relationType`、`version`、`pageNo`、`pageSize`。

成功响应 data.items：`graphId`、`graphName`、`graphCategoryId`、`graphCategoryName`、`nodes`、`edges`、`sourceRefs`、`confidence`、`activeRevisionId`。

错误码：`ICSS-GRAPH-400-INVALID_QUERY`、`ICSS-GRAPH-404-ASSET_NOT_FOUND`。

### API-012A listGraphCategories

- 业务说明：查询图谱动态分类体系、实体类型和关系类型，供录入预览与历史维护页面动态渲染筛选项和编辑选项。
- 关联功能：M7-F1、M8-F1。
- 鉴权：`graph:view`。
- 字段映射：`graphCategoryId -> graph_asset.graph_category_id / knowledge_source.graph_category_id`，`entityTypes -> graph_asset.entity_types`，`relationTypes -> graph_asset.relation_types`。

成功响应 data：`categories`、`entityTypes`、`relationTypes`。

首批分类：`geo-vessel` 地区与船舶、`vessel-crew` 船舶与船员、`vessel-device` 船舶与设备绑定、`device-alarm` 设备与告警、`device-protocol` 设备与协议。

错误码：`ICSS-GRAPH-400-INVALID_QUERY`。

### API-013 updateGraphAssetDraft

- 业务说明：编辑图谱节点和关系草稿。
- 关联功能：M7-F1。
- 鉴权：`graph:edit`。
- 幂等：必填 `X-Idempotency-Key`。
- 事务边界：更新 `graph_asset` 草稿元数据，新增或更新 `graph_revision`，写入 `audit_log`。

请求 Body：`nodeChanges`、`edgeChanges`、`nodes`、`edges`、`graphCategoryId`、`editReason`、`baseRevisionId`。

成功响应 data：`graphId`、`draftVersion`、`status`。

错误码：`ICSS-GRAPH-409-CONFLICT_RELATION`、`ICSS-GRAPH-409-REVISION_LOCKED`、`ICSS-GRAPH-404-ASSET_NOT_FOUND`。

### API-014 publishOrRollbackGraph

- 业务说明：发布或回滚图谱版本。
- 关联功能：M7-F1。
- 鉴权：`graph:publish` 或 `graph:rollback`。
- 幂等：必填 `X-Idempotency-Key`。
- 事务边界：更新 `graph_asset.active_revision_id`、`graph_revision.status`，写入 `audit_log`。

请求 Body：`action=publish|rollback`、`targetVersion`、`comment`。

成功响应 data：`graphId`、`graphStatus`、`activeVersion`。

错误码：`ICSS-GRAPH-409-CONFLICT_RELATION`、`ICSS-GRAPH-404-ASSET_NOT_FOUND`。

### API-015 listMcpCapabilities

- 业务说明：查询 MCP 能力列表、详情、边界、健康状态。
- 关联功能：M9-F1。
- 鉴权：`mcp:view`。
- 分页：统一分页。
- 字段映射：`capabilityId -> mcp_capability.capability_id`，`capabilityStatus -> mcp_capability.status`。

Query：`category`、`enabled/status`、`riskLevel`、`keyword`、`pageNo`、`pageSize`。

成功响应 data.items：`capabilityId`、`capabilityCode`、`capabilityName`、`category`、`riskLevel`、`status`、`lastHealthStatus`、`boundary`。

错误码：`ICSS-AUTH-403-OPERATION_DENIED`。

子路径：`GET /api/v1/mcp/capabilities/{capabilityId}/impact`，用于停用前影响范围查看。

### API-016 updateMcpCapabilityStatus

- 业务说明：启用、停用、标记异常或退役 MCP 能力。
- 关联功能：M9-F1。
- 鉴权：`mcp:update_status`，仅 R-ADMIN。
- 幂等：必填 `X-Idempotency-Key`。
- 事务边界：更新 `mcp_capability`，写入 `mcp_capability_status_log` 和 `audit_log`。

请求 Body：`targetStatus`、`reason`、`impactConfirmed`。

成功响应 data：`capabilityId`、`capabilityStatus`、`changedAt`。

错误码：`ICSS-MCP-404-CAPABILITY_NOT_FOUND`、`ICSS-MCP-409-IMPACT_CONFIRM_REQUIRED`、`ICSS-AUTH-403-OPERATION_DENIED`。

### API-017 updateGraphMcpMapping

- 业务说明：配置图谱关系模式与 MCP 能力的映射条件。
- 关联功能：M9-F1、M10-F1。
- 鉴权：`mcp:update_mapping`。
- 幂等：必填 `X-Idempotency-Key`。
- V0.1 边界：先作为配置契约占位；若数据库未落独立 mapping 表，后端可暂存到 `route_template.mcp_plan` 或配置中心，后续补表需走数据库设计变更。

请求 Body：`graphPattern`、`capabilityIds`、`priority`、`condition`。

成功响应 data：`mappingId`、`status`。

错误码：`ICSS-MCP-409-CAPABILITY_DISABLED`、`ICSS-GRAPH-409-CONFLICT_RELATION`。

### API-018 updateRouteTemplate

- 业务说明：固化、重建、停用或归档诊断路径。
- 关联功能：M10-F1。
- 鉴权：`route:publish` 或 `route:disable`。
- 幂等：必填 `X-Idempotency-Key`。
- 事务边界：更新 `route_template`，按需创建 `route_evaluation`，写入 `audit_log`。

请求 Body：`action=activate|rebuild|disable|archive|approve_new_version`、`caseId`、`questionPattern`、`mcpPlan`、`graphStrategy`、`reason`。

成功响应 data：`routeId`、`routeStatus`、`version`。

错误码：`ICSS-ROUTE-404-NOT_FOUND`、`ICSS-ROUTE-409-SCOPE_CONFLICT`、`ICSS-ROUTE-409-REVIEW_REQUIRED`。

### API-019 submitAnswerFeedback

- 业务说明：提交答案有效性反馈，必要时触发路径复核或重建。
- 关联功能：M10-F1。
- 鉴权：`route:feedback` + 项目权限。
- 幂等：必填 `X-Idempotency-Key`。
- 事务边界：创建 `route_evaluation`，更新 `route_template.failure_count` 或 `status`。

请求 Body：`caseId`、`traceId`、`routeId`、`rating=valid|invalid|partial`、`failureReason`、`comment`。

成功响应 data：`evaluationId`、`rebuildRequired`、`routeStatus`。

错误码：`ICSS-ROUTE-409-FEEDBACK_DUPLICATED`、`ICSS-DIAG-404-CASE_NOT_FOUND`、`ICSS-TRACE-404-NOT_FOUND`。

## 5. 接口与 PM 功能映射

| 功能 | 接口 |
| --- | --- |
| M1-F1 会话建单与实体抽取 | API-001 |
| M1-F2 追问补齐上下文 | API-002 |
| M2-F1 图谱检索与工具编排 | API-003、API-012、API-015 |
| M3-F1 诊断结论与证据链展示 | API-004、API-009 |
| M4-F1 审批执行 | API-005、API-006、API-007，V0.1 暂缓 |
| M5-F1 知识案例沉淀 | API-008，P1 占位 |
| M6-F1 系统执行日志 | API-009 |
| M7-F1 知识图谱查看与维护 | API-012、API-012A、API-013、API-014 |
| M8-F1 多类型知识库录入 | API-010、API-011、API-012A |
| M9-F1 MCP 能力管理 | API-015、API-016、API-017 |
| M10-F1 诊断路径固化与重建 | API-018、API-019 |

## 6. API 与 DB 表映射

| 接口 | 主要表 |
| --- | --- |
| API-001、API-002 | support_session |
| API-003、API-004 | support_session、diagnostic_case、execution_trace |
| API-009 | execution_trace、execution_trace_step、mcp_call_log |
| API-010、API-011 | knowledge_source、knowledge_ingestion_task |
| API-012、API-012A、API-013、API-014 | graph_asset、graph_revision、graph_build_batch、audit_log |
| API-015、API-016 | mcp_capability、mcp_capability_status_log、audit_log |
| API-017 | route_template.mcp_plan 或后续 mapping 表 |
| API-018、API-019 | route_template、route_evaluation、audit_log |

## 7. 联调备注

1. 前端只调用 `/api/v1/*`，不得直接读取 wnx-web / wnx-serve 原始字段。
2. Java Backend 统一处理鉴权、项目过滤、错误码、审计、字段脱敏。
3. Node MCP Server 只暴露 capability，能力边界见 09F。
4. AI Service 返回的诊断路径、图谱结果和推理摘要必须落入 execution_trace 相关结构，便于复盘。
5. V0.1 Mock 必须至少覆盖 09D 中的 10 个场景。
