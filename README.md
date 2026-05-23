# Intelligent Customer Service System

面向 wnx-web 与 wnx-serve 的统一工程知识图谱与智能诊断项目骨架。

本项目定位不是 FAQ 机器人，也不是单一 Code Graph，而是“Unified Engineering Knowledge Graph + Diagnosis Agent + MCP 实时工具 + 人工审批执行”的组合系统。核心目标是把代码、文档、协议、配置、数据库、工单、运维日志、接口和实时系统状态统一关联，让诊断结论可以追溯到真实工程证据。

## 当前已落地内容

- 项目级文档骨架与 PM 初版需求文档
- 多阶段推进说明、风险清单、Agent 主任务书、中断恢复指南
- 可继续开发的 monorepo 最小工程骨架
- Java 主后台 Mock API、Vue3 用户诊断与后台管理系统、Python AI Service Mock 边界、Node MCP 工具调用边界
- 针对 wnx-web 与 wnx-serve 的集成目标说明
- 统一工程知识图谱项目框架：[03_技术方案与架构/07E_统一工程知识图谱项目框架.md](03_技术方案与架构/07E_统一工程知识图谱项目框架.md)

## 目录说明

- 00_输入与总控：总控、状态、风险、变更、恢复
- 01_需求与业务分析：PM 阶段产物
- 02_产品与交互：后续 UI/UX 阶段产物
- 03_技术方案与架构：后续架构阶段产物
- 04_数据库与API：后续 DB / API 阶段产物
- 05_开发计划与Agent任务：任务拆分与执行顺序
- frontend：Vue3 + Element Plus 前端正式边界，包含用户诊断聊天入口与后台管理
- backend-java：Java Spring Boot 主后台，承载 auth、user、project、device、diagnosis、ticket、approval、audit
- ai-service-python：Python AI Service，承载 document_parser、entity_extractor、graph_builder、rag、diagnosis_agent
- mcp-server-node：Node MCP 工具层，承载 device、alarm、config、log、statistics 工具
- infra：本地基础设施，包含 PostgreSQL + pgvector、Neo4j、Redis

## 代码层框架

```text
Vue3 frontend
	-> Java Spring Boot backend
	-> Python AI Service
	-> Neo4j / PostgreSQL + pgvector / LLM API

Java Spring Boot backend
	-> Node MCP Server
	-> wnx-web / wnx-serve / device systems / log systems
```

```text
intelligent-customer-service-system/
	frontend/
	backend-java/
		auth/
		user/
		project/
		device/
		diagnosis/
		ticket/
		approval/
		audit/
	ai-service-python/
		document_parser/
		entity_extractor/
		graph_builder/
		rag/
		diagnosis_agent/
	mcp-server-node/
		src/tools/device/
		src/tools/alarm/
		src/tools/config/
		src/tools/log/
		src/tools/statistics/
	infra/
		docker-compose.yml
		postgres/
		neo4j/
		redis/
```

## 推荐推进顺序

1. 先确认 00_输入与总控 与 01_需求与业务分析 中的待确认项
2. 以 [03_技术方案与架构/07E_统一工程知识图谱项目框架.md](03_技术方案与架构/07E_统一工程知识图谱项目框架.md) 为架构主线，补齐多源入图、跨源关联和图谱质量门禁
3. 完成 04_数据库与API，包括 Neo4j 节点关系、PostgreSQL 元数据、pgvector、MCP 契约
4. 补齐 wnx-web 与 wnx-serve 的接口清单、日志入口、权限复用和鉴权方案
5. 再进入前后端、图谱构建任务、Diagnosis Agent 与 MCP 工具实现

## 本地启动

Java 主后台要求 JDK 25。若本机仍是 Java 8 / 11，需要先切换 `JAVA_HOME` 后再运行 Maven。

### 环境要求

- JDK 25
- Maven 3.9+
- Node.js 22+ / npm 10+
- Python 3.11+
- Docker Desktop 或 Docker Engine + Compose

### 安装依赖

```bash
npm install
python3 -m venv ai-service-python/.venv
ai-service-python/.venv/bin/python -m pip install -e ai-service-python
```

Java 后端可使用仓库默认 Maven 配置；如需复用公司内网 settings，可设置：

```bash
export MAVEN_SETTINGS=/Users/lucas/Work/CompanyProject/app-ship-alarm/settings.xml
```

```bash
./scripts/start-all.sh start
```

`start` 会等待 Java 主后台、Python AI Service、Node MCP Server 和前端 Vite 都完成 HTTP ready 后再返回；如果服务在超时时间内未就绪，会提示对应日志路径。

查看状态或停止：

```bash
./scripts/start-all.sh status
./scripts/start-all.sh stop
```

`status` 会同时显示进程 PID 和 HTTP ready 状态，避免只看到 PID 存在但端口尚未监听。

只验证数据库、Neo4j 和 Redis，不启动应用服务，验证完成后自动关闭容器：

```bash
./scripts/verify-infra.sh
```

服务默认端口：Java 主后台 `8088`，Python AI Service `8100`，Node MCP Server `3202`。
数据库和缓存全部由 Docker 容器化拉起，不依赖本机安装 PostgreSQL、Redis 或 Neo4j：

| 组件 | Docker 镜像 | 默认端口 | 说明 |
| --- | --- | --- | --- |
| PostgreSQL + pgvector | `pgvector/pgvector:pg16` | `5432` | 主业务库、元数据、向量扩展 |
| Neo4j | `neo4j:5-community` | `7474` / `7687` | 统一工程知识图谱 |
| Redis | `redis:7-alpine` | `6379` | 缓存、限流、短期状态 |

容器配置在 [infra/docker-compose.yml](infra/docker-compose.yml)，初始化 SQL 在 [infra/postgres/init.sql](infra/postgres/init.sql)。
当前 `frontend` 已生成 Vue3 + Vite + TypeScript 管理系统前端；脚本会自动检测 `frontend/package.json`，存在时一并启动前端，默认端口 `5173`。

前端主要路由：

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/chat` | 用户诊断 | 提供专家模式与引导式客服模式，只保留聊天、执行过程、可能结果和可信度 |
| `/admin/knowledge` | 知识库与图谱 | 支持结构化文本、非结构化文件录入，并通过关系图维护实体与关系 |
| `/admin/tickets` | 问题工单 | 追溯用户问题、调用链和诊断结果 |
| `/admin/mcp-tools` | MCP 工具库 | 查看 MCP 接口能力、启停状态、调用状态和影响范围 |

## 构建与验证

```bash
npm run build --workspace frontend
npm run build --workspace smart-support-mcp-server-node
python3 -m py_compile ai-service-python/app/main.py
mvn -f backend-java/pom.xml -DskipTests package
```

已验证的 V0.1 主链路：

```text
Vue3 frontend
	-> Java Spring Boot /api/v1
	-> Python AI Service /diagnosis/run
	-> Node MCP Server /tools/{name}/invoke
```

当前 smoke 已覆盖：创建问答会话、发起诊断、查询执行轨迹、返回 Python AI 的 RAG/Agent reasoning steps、返回 Node MCP 的 `device.getStatus` 调用证据。

## Git 提交说明

本仓库已在 `.gitignore` 中排除依赖目录、构建产物、本地运行态、虚拟环境、Docker 数据目录和 CodeGraph 本地索引。建议首个提交日志：

```text
feat: initialize intelligent customer service system

- add product, architecture, database, API, and agent planning docs
- add Vue3 frontend workbench and Java Spring Boot mock backend
- add Python AI service and Node MCP tool mock boundaries
- add Docker infrastructure scripts for PostgreSQL, Neo4j, and Redis
```

## 当前阶段边界

当前交付的是“项目初始化 + PM 初版 + 统一工程知识图谱架构框架 + 用户诊断界面 + 后台管理界面 + 可视化知识图谱维护 + 可运行前后端与 AI/MCP Mock 边界”。
生产环境鉴权、审批流引擎、真实图数据库读写、向量检索、真实文档解析、跨源关联、业务系统真实接入仍需后续阶段继续落盘。
