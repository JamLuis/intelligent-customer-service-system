# 数据库与 API 设计启动输入

## 1. 输入来源

| 编号 | 来源文件 | 用途 |
| --- | --- | --- |
| IN-001 | 00_输入与总控/07_Gate冻结评审清单.md | Gate 临时冻结结论、P0 范围、准入约束 |
| IN-002 | 01_需求与业务分析/04_需求规格说明书.md | 业务对象、业务规则、异常分支 |
| IN-003 | 01_需求与业务分析/04B_功能模块拆解清单.md | 二级功能、用户操作、系统行为、验收标准 |
| IN-004 | 01_需求与业务分析/04C_权限矩阵与状态枚举.md | 角色权限、字段权限、状态流转 |
| IN-005 | 01_需求与业务分析/04D_产品交付衔接清单.md | 页面、接口、字段、UAT、优先级 |
| IN-006 | 03_技术方案与架构/07A_系统架构总体设计.md | 架构边界、服务拆分、技术选型 |
| IN-007 | 03_技术方案与架构/07E_统一工程知识图谱项目框架.md | 图谱、知识源、跨源关联、入图任务框架 |

## 2. V0.1 设计边界

| 范围 | 结论 |
| --- | --- |
| 设计目标 | 支撑可解释问答闭环、基础知识录入、MCP 能力列表、执行日志、路径反馈 |
| 真实 wnx 接入 | 先按 Mock + Adapter 设计，真实接口后续按 capability 替换 |
| 生产写操作 | V0.1 禁止真实执行生产写操作，只保留建议、审批草稿和审计记录 |
| 登录权限 | V0.1 先独立 JWT + 项目级 RBAC，预留 wnx-web SSO 映射字段 |
| 图谱写入 | 允许候选入图、复核、发布、版本回滚，不做单节点精细回滚 |
| 路径固化 | 按项目 + 问题类型生效，禁止全局默认复用 |
| 审计留存 | ExecutionTrace 在线留存 180 天，超过后归档摘要 |

## 3. 数据库设计优先级

### 3.1 P0 必须落表对象

| 优先级 | 对象 | 建议表/集合 | 设计重点 |
| --- | --- | --- | --- |
| P0 | SupportSession | support_session | 问答会话、上下文、状态、项目权限 |
| P0 | DiagnosticCase | diagnostic_case | 诊断案例、结论、置信度、风险等级 |
| P0 | ExecutionTrace | execution_trace、execution_trace_step、mcp_call_log | 图谱路径、MCP 调用、推理步骤、降级原因、字段权限 |
| P0 | KnowledgeSource | knowledge_source、knowledge_ingestion_task | 文件/文本来源、解析状态、抽取状态、入图状态、对象存储引用 |
| P0 | GraphAsset | graph_asset、graph_build_batch、graph_revision | 图谱版本、构建批次、复核状态、回滚能力 |
| P0 | McpCapability | mcp_capability、mcp_capability_status_log | 能力边界、风险等级、启停状态、健康状态 |
| P0 | RouteTemplate | route_template、route_evaluation | 路径模板、项目 + 问题类型粒度、反馈、重建状态 |
| P0 | AuditLog | audit_log | 用户操作、权限敏感操作、数据导出、启停能力 |

### 3.2 P1/P2 暂缓对象

| 对象 | 暂缓原因 | V0.1 保留方式 |
| --- | --- | --- |
| ApprovalOrder | 审批流归属未定 | 只保留模型和状态，不接真实 Flowable |
| ExecutionTask | V0.1 禁止真实生产写操作 | 不设计执行落地表，只保留建议动作与审批草稿 |
| WorkflowCallback | 审批系统后置 | 预留接口，不进入 V0.1 必须实现范围 |
| AdvancedGraphReview | 双人审核或复杂审批未定 | 低置信度/冲突先进入 reviewing 状态 |

## 4. API 设计优先级

### 4.1 P0 API 分组

| 分组 | API 范围 | 设计重点 |
| --- | --- | --- |
| 问答会话 API | 创建会话、补充上下文、发起诊断、查询诊断结果 | 支持 SSE 阶段进度和 Mock 诊断 |
| 执行日志 API | 查询 ExecutionTrace、查询步骤、查询 MCP 调用摘要 | 按角色返回摘要/详细/原始字段 |
| 知识录入 API | 上传知识源、查询解析状态、重跑解析、提交入图 | 支持文件限制和状态流 |
| 图谱查询 API | 查询节点、关系、子图、来源、版本 | 先做查询与版本展示，编辑发布进入 P0/P1 边界 |
| MCP 能力 API | 查询能力列表、查询能力详情、启停能力、查看影响路径 | 停用前必须展示影响范围 |
| 路径反馈 API | 提交答案反馈、查询路径模板、固化/重建路径 | 按项目 + 问题类型约束 |
| 审计 API | 查询操作审计、导出审计摘要 | 字段脱敏和导出权限 |

### 4.2 Mock / Adapter 约束

| 约束 | 说明 |
| --- | --- |
| API 返回结构 | 前端不得依赖真实 wnx 字段，必须依赖本系统统一 DTO |
| MCP 能力 | 每个 capability 必须有 inputSchema、outputSchema、riskLevel、enabled、healthStatus |
| Mock 数据 | Mock 必须覆盖成功、无答案、MCP 失败降级、权限不足、路径重建五类场景 |
| 替换策略 | 真实 wnx 接口接入时只替换 Adapter，不改变前端 API 和诊断核心 DTO |

## 5. 数据权限与字段脱敏输入

| 数据类型 | 客服 | 运维 | 研发审批人 | 管理员 | 审计 |
| --- | --- | --- | --- | --- | --- |
| 诊断答案 | 可见 | 可见 | 可见 | 可见 | 可见 |
| 证据摘要 | 可见 | 可见 | 可见 | 可见 | 可见 |
| 原始 MCP 返回 | 不可见 | 条件可见 | 条件可见 | 可见 | 可见但只读 |
| 图谱节点关系 | 可见基础信息 | 可见详细 | 可见详细 | 可编辑/发布 | 只读可导出 |
| 知识源原文件 | 不可见 | 授权可见 | 授权可见 | 可见/删除/归档 | 只读可审计 |
| 路径模板 | 可反馈 | 可建议固化 | 可审核 | 可发布/停用/回滚 | 只读可导出 |

## 6. 状态流设计输入

| 对象 | 状态来源 | 数据库设计要求 |
| --- | --- | --- |
| SupportSession | 04C 4.1 | 状态字段必须可查询，状态变更需要记录更新时间 |
| DiagnosticCase | 04C 4.2 | 结论生成、低置信度、归档需可追溯 |
| ExecutionTrace | 04C 4.6 | recording、completed、partial、failed 必须可表达 |
| GraphAsset | 04C 4.7 | draft、reviewing、published、deprecated、rolled_back 必须可表达 |
| KnowledgeSource | 04C 4.8 | uploaded、parsing、extracted、graph_ready、published、failed 必须可表达 |
| McpCapability | 04C 4.9 | draft、enabled、disabled、unhealthy、retired 必须可表达 |
| RouteTemplate | 04C 4.10 | candidate、active、reviewing、disabled、archived 必须可表达 |

## 7. 错误码设计输入

| 错误域 | 建议前缀 | 示例 |
| --- | --- | --- |
| 权限错误 | ICSS-AUTH | ICSS-AUTH-403-PROJECT_DENIED |
| 会话诊断 | ICSS-DIAG | ICSS-DIAG-409-CONTEXT_REQUIRED |
| 执行轨迹 | ICSS-TRACE | ICSS-TRACE-404-NOT_FOUND |
| 知识录入 | ICSS-KNOW | ICSS-KNOW-413-FILE_TOO_LARGE |
| 图谱治理 | ICSS-GRAPH | ICSS-GRAPH-409-CONFLICT_RELATION |
| MCP 能力 | ICSS-MCP | ICSS-MCP-409-CAPABILITY_DISABLED |
| 路径治理 | ICSS-ROUTE | ICSS-ROUTE-409-REVIEW_REQUIRED |
| 审计导出 | ICSS-AUDIT | ICSS-AUDIT-403-EXPORT_DENIED |

## 8. 下一步设计任务

| 顺序 | 任务 | 输出文件 | 验收标准 |
| --- | --- | --- | --- |
| 1 | 数据库 ER 与表结构设计 | 04_数据库与API/08_数据库设计.md | P0 对象、字段、索引、状态流、保留策略完整 |
| 2 | API 契约设计 | 04_数据库与API/09_API文档.md | P0 API、参数、响应、错误码、鉴权、Mock 规则完整 |
| 3 | MCP capability manifest 细化 | mcp-server-node/src/tools/manifest.ts 或设计文档 | 四类首批能力具备 schema、riskLevel、healthStatus |
| 4 | 前端 Mock 原型输入 | 02_产品与交互 | 页面字段、状态、空/错/加载态与 Mock 数据齐备 |
| 5 | 后端 Mock 闭环任务拆解 | 05_开发计划与Agent任务 | 可按模块派发给前端、后端、AI Service、MCP Server |
