# Phase 1 结构拆分验收报告

## 1. 阶段目标

本阶段对应任务：

- RG-01 拆分 Python API 层与领域层
- RG-02 拆分 Python parse / extract / answer 模块
- RG-03 拆分 Java 知识编排阶段服务
- RG-04 抽取统一 DTO 与 schema 契约

阶段原则：不改变主接口路径，不改变当前主链路行为，先建立可继续演进的结构边界。

---

## 2. Python 拆分结果

### 2.1 FastAPI 入口

`ai-service-python/app/main.py` 已收缩为应用装配入口：

```text
create_app()
兼容导出 runtime 中的历史函数
```

### 2.2 路由层

新增：

- `app/api/app.py`
- `app/api/routes/health.py`
- `app/api/routes/llm.py`
- `app/api/routes/diagnosis.py`
- `app/api/routes/chat.py`
- `app/api/routes/knowledge.py`

路由层只负责 HTTP endpoint 注册，不再承载大段抽取、解析、问答逻辑。

### 2.3 领域模块边界

新增：

- `app/preprocess/service.py`
- `app/preprocess/parsers/markdown_parser.py`
- `app/preprocess/parsers/sql_parser.py`
- `app/preprocess/parsers/csv_parser.py`
- `app/extraction/pipeline.py`
- `app/extraction/rule_extractor.py`
- `app/extraction/openie_extractor.py`
- `app/embedding/service.py`
- `app/answer/service.py`
- `app/answer/evidence_pack.py`
- `app/answer/prompting.py`
- `app/answer/fallback.py`
- `app/retrieval/planner.py`

说明：本阶段为结构拆分，`app/services/runtime.py` 暂时承载迁移后的兼容实现。Phase 2 将继续把 runtime 内部实现分批迁移到领域模块内部。

### 2.4 Schema 契约

新增：

- `app/schemas/knowledge.py`
- `app/schemas/chat.py`

用于固定 parse、embed、extract、chat 的关键字段契约。

---

## 3. Java 拆分结果

`KnowledgeIngestionPipeline` 已保留为兼容入口，实际编排迁移到：

- `KnowledgeIngestionOrchestrator`

新增阶段服务：

- `KnowledgeTaskStateService`
- `ParseStageService`
- `EmbeddingStageService`
- `ExtractStageService`
- `GraphBuildStageService`

阶段职责：

| 服务 | 职责 |
| --- | --- |
| `KnowledgeTaskStateService` | 统一任务状态、完成、失败更新 |
| `ParseStageService` | 调用 Python parse，并持久化 block |
| `EmbeddingStageService` | 调用 Python embed，并回写 embedding |
| `ExtractStageService` | 调用 Python extract，持久化 candidate，构建 draft payload |
| `GraphBuildStageService` | 创建 draft graph，并写入 Neo4j draft |
| `KnowledgeIngestionOrchestrator` | 编排阶段顺序与失败处理 |

---

## 4. 契约文档

新增接口与 schema 契约文档：

- `04_数据库与API/09K_PreProcess重构接口与Schema契约.md`

覆盖：

- Parse 契约
- Embed 契约
- Extract 契约
- Candidate Entity / Relation 契约
- Java 阶段服务边界
- Phase 1 验收命令

---

## 5. 验收结果

### 5.1 Python

命令：

```bash
cd ai-service-python
.venv/bin/python -m pytest tests
```

结果：

```text
collected 10 items
10 passed in 0.29s
```

### 5.2 Java

命令：

```bash
cd backend-java
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn test
```

结果：

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

说明：当前默认终端 JDK 是 11，而 backend-java 配置 Java 25；运行 Java 验收时必须显式指定 JDK 25。

### 5.3 静态诊断

VS Code diagnostics 检查结果：

- Python `app`：无错误
- Java `knowledge` 包：无错误

---

## 6. 阶段结论

Phase 1 已完成。

本阶段已经做到：

1. Python API 层从业务逻辑中拆出。
2. Python parse / extract / embed / answer / retrieval 形成领域目录边界。
3. Java 知识摄入从单类顺序执行拆成阶段服务。
4. Python/Java 主行为由 Phase 0 测试基线保护，并已验证通过。
5. Parse / Embed / Extract / Candidate schema 已有契约文档。

下一阶段可以进入 Phase 2：PreProcess 基础能力落地，优先从 RG-05 统一 IR 契约和 RG-06 parser routing 开始。
