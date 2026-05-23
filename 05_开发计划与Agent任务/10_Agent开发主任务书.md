# Intelligent Customer Service System Agent 开发主任务书

## 1. 使用说明

本任务书用于指导后续 Agent 按固定顺序执行开发，不得跳过前置文档检查。

## 2. 开发顺序总览

当前状态：DEV-001 已完成；DEV-002 已完成 V0.1 P0 数据库设计与 API 契约；Docker 基础设施已验证通过并关闭；DEV-004 Java 主后台 Mock API 已完成；DEV-003 前端联调工作台已完成；DEV-005 Python AI Service Mock 边界已完成；DEV-006 Node MCP 工具调用边界已完成。下一步进入真实 wnx-web / wnx-serve capability 盘点，或把 Python AI Service 的 Mock 图谱/RAG 替换为 Neo4j、pgvector 和真实文档解析任务。

| 顺序 | 任务编号 | 任务名称 | 前置条件 | 输入文档 | 输出结果 | 完成定义 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | DEV-001 | 完成系统架构设计 | PM 文档已完成 | 04、04B、04C、04D、05 | 07、07A、07B、07C、07D | 确认技术选型、系统边界、集成方案与风险 |
| 2 | DEV-002 | 完成数据库与 API 设计 | 架构文档完成 | 07 系列文档 | 08、09 | 形成业务对象落库方案与接口契约 |
| 3 | DEV-004 | 实现 Java 主后台 | API 与权限契约初版完成；Docker 基础设施验证通过 | 07A、07B、09 | backend-java | 已完成：统一响应、错误处理、内存 Mock Service、P0/P1 占位接口、关键 smoke 通过 |
| 4 | DEV-005 | 实现 Python AI Service | 图谱与 AI 契约初版完成 | 07E、07B、09 | ai-service-python | 已完成：诊断推理、知识入图、图谱查询 Mock 边界可调用，并已接入 Java 后端 |
| 5 | DEV-006 | 实现 MCP 工具层 | 目标系统接口清单完成或使用 Mock capability | 07B、09 | mcp-server-node | 已完成：工具 manifest 与 invoke 边界可调用，并已接入 Java 后端 |
| 6 | DEV-003 | 实现前端工作台骨架 | 后端 Mock API 可用 | 04D、09 | frontend | 已完成：Vue3 + Vite + TS + Element Plus 联调工作台，覆盖问答、结果、执行日志、知识录入、MCP 能力、路径反馈 |
| 7 | DEV-007 | 实现审批与执行闭环 | 审批链路明确 | 04C、07B、09 | backend-java、frontend、mcp-server-node | 审批流、执行任务、审计链跑通 |
| 8 | DEV-008 | 完成联调与验证 | 前后端与工具层可运行 | 全量文档与代码 | 06_测试与发布 | 完成关键用例联调、风险回归与发布准备 |

## 3. 任务明细

### 3.1 DEV-001

- 任务名称：完成系统架构设计
- 目标：明确 Graph RAG、向量检索、MCP 工具层、Agent 编排层、审批执行层与 wnx-web/wnx-serve 的边界。
- 前置条件：PM 文档完整。
- 依赖模块：无。
- 输入文档：00_输入与总控、01_需求与业务分析。
- 输出代码位置：无。
- 需要更新的文档：03_技术方案与架构 全部文档。
- 完成定义：技术选型、部署方式、权限链路、集成方式、风险与回流问题均已落盘。
- 风险点：接口清单缺失、审批流归属未定。

### 3.2 DEV-002

- 任务名称：完成数据库与 API 设计
- 目标：把会话、案例、审批、执行、知识等核心对象转成数据库和接口契约。
- 前置条件：DEV-001 完成。
- 依赖模块：系统架构设计。
- 输入文档：07 系列文档。
- 输出代码位置：无。
- 需要更新的文档：08_数据库设计.md、08A_建表SQL、09_API文档.md、09B_接口规范.md、09C_错误码登记表.md、09D~09I API 辅助契约。
- 完成定义：字段、状态、索引、接口入参出参、错误码和权限要求明确。
- 当前结论：已完成 V0.1 P0 设计，真实 wnx 接入后只替换 Adapter / capability，不改变前端 API 契约。
- 风险点：真实系统接口与日志能力尚未盘点完成。

### 3.3 DEV-003

- 任务名称：实现前端工作台骨架
- 目标：把问答工作台、结果页、审批中心、知识中心做成可联调 UI。
- 前置条件：DEV-002 完成。
- 依赖模块：前端路由、接口 SDK、权限路由。
- 输入文档：04D、09。
- 输出代码位置：frontend。
- 需要更新的文档：状态总表、变更记录。
- 完成定义：页面可运行、可接 mock 数据、状态和权限有基础兜底。
- 当前结论：已完成 Vue3 联调工作台，`npm run build --workspace frontend` 已通过；当前为工程联调页面，不作为最终用户体验定稿。
- 风险点：是否嵌入现有菜单尚未确定。

### 3.4 DEV-004

- 任务名称：实现 Java 主后台
- 目标：实现 auth、user、project、device、diagnosis、ticket、approval、audit 主后台模块。
- 前置条件：DEV-002 完成。
- 依赖模块：接口契约、权限模型、AI Service 契约、MCP 工具协议。
- 输入文档：07A、07B、09。
- 输出代码位置：backend-java。
- 需要更新的文档：状态总表、决策记录。
- 完成定义：提供主后台基础 API、鉴权审计骨架、AI Service 和 MCP Server 调用边界。
- 当前结论：已完成 V0.1 Mock API，覆盖 support session、diagnosis、trace、knowledge、graph、mcp、route、approval/execution 占位；Maven package 与关键 API smoke 已通过。
- 风险点：权限复用、审批归属、旧系统 SDK 兼容性尚未最终确定。

### 3.5 DEV-005

- 任务名称：实现 Python AI Service
- 目标：实现文档解析、实体抽取、图谱构建、RAG 与 Diagnosis Agent 服务边界。
- 前置条件：DEV-002 完成且图谱与 AI 契约初版可用。
- 依赖模块：Neo4j、PostgreSQL + pgvector、LLM API、Source Registry。
- 输入文档：07E、07B、09。
- 输出代码位置：ai-service-python。
- 需要更新的文档：状态总表、风险清单、API 文档。
- 完成定义：AI Service 提供健康检查、能力清单、文档入图任务和诊断推理接口骨架。
- 当前结论：已完成 `/diagnosis/run`、`/knowledge/ingest`、`/graphs/query` Mock 边界；Java 后端优先调用 AI Service，失败时回退本地 Mock。已处理 JDK HTTP Client 对 Uvicorn 的 h2c upgrade 兼容问题，Java client 固定 HTTP/1.1。
- 风险点：LLM 供应商、文档解析质量和图谱复核规则尚未最终确定。

### 3.6 DEV-006

- 任务名称：实现 MCP 工具层
- 目标：将 wnx-web 与 wnx-serve 的查询能力封装为安全工具。
- 前置条件：DEV-002 完成且真实接口清单可用。
- 依赖模块：接口契约、鉴权方案。
- 输入文档：07B、09。
- 输出代码位置：mcp-server-node。
- 需要更新的文档：状态总表、风险清单、API 文档。
- 完成定义：工具调用受白名单、权限、审计约束，可返回结构化证据。
- 当前结论：已完成 `/manifest`、`/tools`、`/tools/{name}/invoke` Mock 工具边界；Java 后端可同步工具能力并调用 `device.getStatus` 形成 trace 证据。
- 风险点：目标系统接口稳定性与权限模式可能不统一。

### 3.7 DEV-007

- 任务名称：实现审批与执行闭环
- 目标：在审批链路明确后实现审批单、执行任务、风险控制和审计链路。
- 前置条件：DEV-003 至 DEV-006 完成。
- 依赖模块：审批系统、权限、MCP 工具、审计日志。
- 输入文档：04C、07B、09。
- 输出代码位置：backend-java、frontend、mcp-server-node。
- 需要更新的文档：状态总表、风险清单、变更记录。
- 完成定义：审批流、执行任务、审计链跑通。
- 风险点：审批流归属、真实生产写操作边界尚未最终确定。

### 3.8 DEV-008

- 任务名称：完成联调与验证
- 目标：验证关键业务用例与安全边界。
- 前置条件：DEV-003 至 DEV-007 完成。
- 依赖模块：前端、Agent 服务、MCP 工具、权限与审计。
- 输入文档：全部设计文档与测试用例。
- 输出代码位置：全项目。
- 需要更新的文档：06_测试与发布、状态总表、变更记录。
- 完成定义：关键用例通过，风险项已验证或显式登记。
- 风险点：真实数据环境差异、跨系统权限和网络访问问题。

## 4. 回写要求

每完成一个任务，必须更新：

1. 项目状态总表。
2. 决策记录或变更记录。
3. 风险与阻塞清单。
