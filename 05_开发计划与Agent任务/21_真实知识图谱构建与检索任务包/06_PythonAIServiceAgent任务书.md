# Python AI Service Agent 任务书

## 1. 角色边界

Python AI Service Agent 负责文档解析、文本块生成、候选实体/关系抽取、embedding、实体归一、冲突检测和检索计划。Python 只能返回候选和计划，不允许直接写 PostgreSQL/Neo4j 的发布图谱。

## 2. 必读文件

1. `03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md` §10、§11、§13
2. `ai-service-python/app/main.py`
3. 接口 Agent 输出的 AI 内部接口契约
4. `05_开发计划与Agent任务/21_真实知识图谱构建与检索任务包/02_任务总表.md`

## 3. 推荐包结构

```text
ai-service-python/app/
  parsers/
    base.py
    text_parser.py
    table_parser.py
    pdf_parser.py
    docx_parser.py
    xlsx_parser.py
    image_ocr_parser.py
  extractors/
    rule_extractor.py
    llm_extractor.py
    entity_normalizer.py
    relation_validator.py
    conflict_detector.py
  embeddings/
    embedding_client.py
  graph_builder/
    candidate_builder.py
    graph_plan_builder.py
  retrieval/
    query_understanding.py
    graph_retrieval_planner.py
```

## 4. 任务拆分

### KG-AI-001 Parser registry 与统一 block schema

- 目标：建立统一 parser 入口。
- 支持类型：text、md、log、ini、json、csv。
- 输出统一 blocks：
  - blockId
  - sourceId
  - blockType
  - sectionPath
  - pageNo
  - rawText
  - normalizedText
  - metadata
- DoD：
  - 每种类型至少一个单元测试样例。
  - 非 UTF-8 编码、空文件、超长文件有错误返回。

### KG-AI-002 Office/PDF 基础解析器

- 目标：支持 docx/xlsx/pdf 基础解析。
- docx：标题、段落、表格。
- xlsx：sheet、表头、行列。
- pdf：优先 Docling/Unstructured，缺失依赖时降级为可解释错误。
- DoD：
  - 能输出 pageNo/sheetName/sectionPath。
  - 表格保留 row/col 信息。
  - 解析失败返回 degraded/failed 原因。

### KG-AI-003 Rule extractor 与 LLM extractor adapter

- 目标：从 blocks 生成 candidate entities/relations。
- Rule extractor：设备号、告警码、协议名、接口路径、表名、字段名、KV 配置。
- LLM extractor：自然语言段落和复杂表格说明。
- 输出必须符合 07F §11.2 JSON schema。
- DoD：
  - LLM 输出非法 JSON 时最多重试 2 次。
  - 没配置 LLM 时可以用规则抽取继续跑通主链路。
  - candidate 带 confidence 和 evidenceBlockIds。

### KG-AI-004 Entity normalizer 与 conflict detector

- 目标：识别别名、重复实体、冲突关系。
- 示例（仅成型）：同一实体的多个别名/写法可被归一到一个 candidate，具体名称、别名表、`uniqueKey` 字段均来自调用方 taxonomy。
- 冲突示例（仅成型）：同一个实体被设置为与两个不同目标同时存在同一条业务上不允许复多的关系。
- DoD：
  - 输出 accepted/reviewing/conflict 建议状态。
  - 冲突必须带 reason_code 和候选对象 ID。

### KG-AI-005 Retrieval planner 与 query understanding

- 目标：为诊断检索生成图谱查询计划。
- 输入：questionText、context、taxonomy。
- 输出：候选实体、graphCategoryIds、relationTypes、maxDepth、vector query text。
- DoD：
  - 能根据传入的 taxonomy/entityTypes 识别上下文中的实体标识、范围限定、时间窗口；**不能硬编码任何业务名称**。
  - 无法识别实体时给出 vector fallback plan。
  - 不返回 Cypher，由 Java 后端执行参数化查询。

## 5. 内部接口建议

| 方法 | 路径 | 输入 | 输出 |
| --- | --- | --- | --- |
| POST | `/knowledge/parse` | source metadata + rawText/objectRef | blocks |
| POST | `/knowledge/extract` | blocks + taxonomy | candidateEntities/candidateRelations |
| POST | `/knowledge/normalize` | candidates + alias context | normalized candidates/conflicts |
| POST | `/knowledge/build-plan` | accepted candidates | graph draft plan |
| POST | `/graphs/retrieve-plan` | question + context + taxonomy | retrieval plan |

## 6. 置信度规则

按照 07F §11.3 执行：

| 来源 | 默认初始置信度 |
| --- | --- |
| 结构化字段明确匹配 | 0.90 |
| 表格行列映射 | 0.85 |
| LLM 文本抽取 | 0.70 |
| OCR 文本抽取 | 0.60 |
| Cross Link 关系 | 0.65 |

自动 accepted/reviewing/rejected 阈值不得由 Python 独自改动，需与 07F 保持一致。

## 7. 禁止事项

1. 不要直接连接 Neo4j 写发布图谱。
2. 不要把向量相似度结果当作真实关系。
3. 不要让 LLM 输出自然语言混入 JSON。
4. 不要丢失 evidenceBlockIds。
5. 不要把 system/developer prompt 暴露给文档内容覆盖。

## 8. 验证命令

```bash
python3 -m py_compile ai-service-python/app/main.py
```

如新增 pytest，补充：

```bash
python3 -m pytest ai-service-python/tests
```

## 9. 交接给后端

必须提供：

1. 每个内部接口请求/响应样例。
2. block schema 字段说明。
3. candidate schema 字段说明。
4. 哪些 sourceType 已支持，哪些返回 unsupported。
5. LLM/embedding provider 是否可配置，默认如何降级。

## 9. V0.3.1 增量任务

### KG-AI-006 Evidence 加权 与 检索关键词抽取

**输出**：`ai-service-python/app/extractors/llm_extractor.py`、`ai-service-python/app/retrieval/graph_retrieval_planner.py`。参考 07F §11.2 / §13.4.5。

- `/knowledge/extract` 响应 candidates[*].evidence 从纯数组改为对象数组 `[{blockId, weight, sourceType}]`。
- 默认权重表：`table_cell=1.0`、`paragraph=0.85`、`table_caption=0.7`、`ocr=0.4`、`code_comment=0.5`、`title=0.9`、`list_item=0.75`、`json_field=0.95`、`csv_cell=0.95`、`kv_pair=0.9`。
- LLM 输出不定 sourceType 统一为 `paragraph`；weight 越界则奋 clamp 到 [0,1]。
- `/graphs/retrieve-plan` 响应额外输出 `keywords: string[]` 与 `vectorQueryText: string`，以供 Java 侧 Hybrid Retrieval。无法抽取关键词时 → `keywords=simple_tokenize(questionText)`，不允许返空。
- 响应增 `embeddingModel` `embeddingVersion` echo，仅告知上游当前使用哪个型号。
- DoD：单元测试覆盖 10 种 sourceType 的默认权重、weight clamp、keywords fallback。

### KG-AI-007 Canonical Resolver 服务端辅助

**输出**：`ai-service-python/app/extractors/entity_normalizer.py` (新增 `canonical_resolver.py`)。参考 07F §10A.4。

- `/knowledge/normalize` 响应每个 normalized 项增 `scoreBreakdown{uniqueKey, alias, regex, embedding, codeGraphRef, llmVerify}`。
- embedding-only 高似不生合并建议，仅输出 `crossLinkHint=true` 交给 Java 侧表决。
- 入参接收上游 `frozen` 倒准入名单，这些项跳过规范化 → 输出 `skipped=true, reason="frozen"`。
- DoD：单元测试覆盖：(1) UniqueKey 命中 → 生合并建议；(2) 只有 embedding 高似 → 仅 hint；(3) frozen 项 → skipped。
