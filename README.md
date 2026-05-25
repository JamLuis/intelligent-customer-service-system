# Intelligent Customer Service System

面向 wnx-web 与 wnx-serve 的统一工程知识图谱与智能诊断项目骨架。

本项目定位不是 FAQ 机器人，也不是单一 Code Graph，而是“Unified Engineering Knowledge Graph + Diagnosis Agent + MCP 实时工具 + 人工审批执行”的组合系统。核心目标是把代码、文档、协议、配置、数据库、工单、运维日志、接口和实时系统状态统一关联，让诊断结论可以追溯到真实工程证据。

## 系统特色：真实关系图谱，不是传统向量相似度问答

本系统的知识库不是传统的“把非结构化文件切成 chunk，生成 embedding 后存入向量库，再纯靠点积 / 余弦相似度判断是否相关”的方案。向量检索在本项目中只作为语义召回和证据补充手段，不作为最终关系判断依据。

非结构化文件进入系统后，用户无需预先选择业务分类；系统会先解析为可追溯的文本块、表格块、OCR 块或配置块，再抽取平台中立的候选实体和候选关系，经过实体归一、别名合并、关系类型校验、置信度评分、冲突检测、自动归类建议和人工复核，最后写入 Neo4j 形成真实可查询的工程知识图谱。每个实体和关系都必须保留 source、block、页码 / 段落、版本、置信度和发布状态，确保诊断时可以查询关系路径，也可以回溯证据来源。

因此，本系统的核心能力是“可验证、可维护、可回滚、可关系检索”的工程知识图谱，而不是只基于 embedding 相似度的文本召回。

## 当前已落地内容

- 项目级文档骨架与 PM 初版需求文档
- 多阶段推进说明、风险清单、Agent 主任务书、中断恢复指南
- 可继续开发的 monorepo 最小工程骨架
- Java 主后台 Mock API、Vue3 用户诊断与后台管理系统、Python AI Service Mock 边界、Node MCP 工具调用边界
- 知识图谱已拆分为录入预览与历史维护，并支持动态图谱分类、实体类型和关系类型管理
- 真实知识图谱构建与检索详细设计已落盘，明确结构化文本/非结构化文件入图、Neo4j 关系存储、pgvector 证据召回、复核发布和版本回滚
- 真实知识图谱构建与检索专项任务包已落盘，可直接分派数据库、接口、Java 后端、Python AI Service、前端和测试 Agent 开工
- 针对 wnx-web 与 wnx-serve 的集成目标说明
- 统一工程知识图谱项目框架：[03_技术方案与架构/07E_统一工程知识图谱项目框架.md](03_技术方案与架构/07E_统一工程知识图谱项目框架.md)
- 真实知识图谱构建与检索设计：[03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md](03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md)
- 真实知识图谱专项任务包：[05_开发计划与Agent任务/21_真实知识图谱构建与检索任务包/00_README_任务包总览.md](05_开发计划与Agent任务/21_真实知识图谱构建与检索任务包/00_README_任务包总览.md)

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
2. 以 [03_技术方案与架构/07E_统一工程知识图谱项目框架.md](03_技术方案与架构/07E_统一工程知识图谱项目框架.md) 为架构主线，并按 [03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md](03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md) 实施真实入图、关系检索和图谱质量门禁
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

Java 后端默认使用本机 Maven 公共仓库配置，避免内网 Nexus 不通时启动长时间等待；如需复用公司内网 settings，可设置：

```bash
export MAVEN_SETTINGS=/Users/lucas/Work/CompanyProject/app-ship-alarm/settings.xml
```

如果内网 Maven 仓库不可用，启动脚本会默认回退到 Maven 公共仓库继续打包；如需禁止回退，可设置 `MAVEN_SETTINGS_ALLOW_FALLBACK=0`。

开放信息抽取默认走 OpenAI-compatible LLM 接口，可接云端大模型，也可接本地 vLLM / Ollama / LM Studio 等兼容服务：

```bash
export KNOWLEDGE_LLM_EXTRACT_ENABLED=true
export LLM_API_BASE_URL=http://127.0.0.1:11434/v1
export LLM_MODEL=qwen2.5:1.5b
```

`./scripts/start-all.sh start` 会默认启动本地 Ollama 服务并确保 `OLLAMA_MODEL` 存在，默认模型为 `qwen2.5:1.5b`。可通过环境变量调整：

```bash
export OLLAMA_ENABLED=1
export OLLAMA_PORT=11434
export OLLAMA_MODEL=qwen2.5:1.5b
```

后台模型配置页支持扫描本机模型：打开 `/admin/knowledge/model-config` 后点击“扫描本机模型”，系统会自动识别本机 Ollama、llama.cpp 和 LM Studio 暴露的模型。用户只需要选择模型并点击“一键使用选中模型”，系统会自动填入服务地址、模型名、运行时、Token 上限等参数，并完成保存、验证和启用。

模型配置页已支持“自由选择 + 自动检查 + 运行时启动”：切换本地模型时会自动触发连接检查；当 Ollama 或 llama.cpp 显示“未启动”时，可直接点击“启动”按钮在配置页拉起对应本地服务（LM Studio 仍需手动从桌面应用启动）。

```bash
./scripts/start-all.sh start
```

如果要单独启动 Gemma GGUF 模型，不和默认 Ollama `11434` 混用，推荐直接用 llama.cpp 启动 OpenAI-compatible 服务。该脚本默认读取：

```text
/Users/lucas/Work/Personal/llama.cpp-kleidiai/models/gemma-4-E4B-it-Q4_0.gguf
```

启动后会在 `11435` 暴露 OpenAI-compatible API，模型名为 `gemma-4-E4B-it-Q4_0`，可直接填到模型配置页的本地模型 Profile：

```bash
brew install llama.cpp

./scripts/gemma-llamacpp.sh start
./scripts/gemma-llamacpp.sh status
./scripts/gemma-llamacpp.sh smoke
./scripts/gemma-llamacpp.sh stop
```

如果已经有自行编译的 llama.cpp，可指定 server 路径：

```bash
export LLAMA_SERVER_BIN=/path/to/llama-server
./scripts/gemma-llamacpp.sh start
```

也可以继续使用 Ollama 包装 GGUF，但它会先把 GGUF 注册成 Ollama model：

```bash
./scripts/gemma-ollama.sh start
./scripts/gemma-ollama.sh status
./scripts/gemma-ollama.sh smoke
./scripts/gemma-ollama.sh stop
```

对应配置：

```bash
export LLM_API_BASE_URL=http://127.0.0.1:11435/v1
export LLM_MODEL=gemma-4-E4B-it-Q4_0
```

如需改模型文件、端口或模型名，可设置：

```bash
export GEMMA_MODEL_PATH=/path/to/model.gguf
export GEMMA_LLAMA_PORT=11435
export GEMMA_LLAMA_MODEL=gemma-4-E4B-it-Q4_0
```

如果 `./scripts/gemma-llamacpp.sh start` 或 `./scripts/gemma-ollama.sh recreate` 提示 GGUF 文件过小，或报 `tensor ... offset+size exceeds file size`，说明本地 GGUF 大概率是未完整下载或已损坏，需要重新下载后再启动。

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
| `/admin/knowledge/ingest` | 知识录入与预览 | 上传结构化文本/非结构化文件，系统自动解析、留证据并生成候选图谱；文本直连支持 txt/md/csv/json/ini/log/sql/ddl |
| `/admin/knowledge/graphs` | 历史知识图谱维护 | 按动态图谱分类、实体类型、关系类型筛选和维护历史图谱 |
| `/admin/knowledge/model-config` | 模型配置 | 对话问答与知识抽取模型独立配置，本地/云端参数互不覆盖 |
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

当前交付的是“项目初始化 + PM 初版 + 统一工程知识图谱架构框架 + 真实知识图谱构建与检索设计 + 用户诊断界面 + 后台管理界面 + 知识录入预览 + 历史知识图谱动态分类维护 + 可运行前后端与 AI/MCP Mock 边界”。
生产环境鉴权、审批流引擎、真实图数据库读写实现、向量检索实现、真实文档解析实现、跨源关联实现、业务系统真实接入仍需后续阶段按 07F 继续编码落地。
