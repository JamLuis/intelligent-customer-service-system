# PreProcess 重构接口与 Schema 契约

## 1. 文档目标

本文档对应 Phase 1 / RG-04 与 Phase 2 / RG-05 ~ RG-10A，用于固定 Python AI Service 与 Java Backend 之间的关键契约，避免后续 PreProcess、抽取、治理能力重构时字段继续发散。

适用接口：

- `/knowledge/parse`
- `/knowledge/embed`
- `/knowledge/extract`
- `/knowledge/normalize`
- `/chat/answer`
- `/graphs/retrieve-plan`

---

## 2. Python 模块边界

Phase 1 后 Python AI Service 模块边界如下：

```text
app/main.py
  只负责 create_app() 与兼容导出

app/api/routes/
  health.py
  llm.py
  diagnosis.py
  chat.py
  knowledge.py

app/preprocess/
  service.py
  routing.py
  artifact.py
  ir/models.py
  metadata/builder.py
  normalize/structure_detector.py
  chunking/paragraph_semantic.py
  chunking/token_size.py
  quality/checker.py
  quality/dedup.py
  parsers/
    markdown_parser.py
    sql_parser.py
    csv_parser.py
    text_parser.py

app/extraction/
  pipeline.py
  rule_extractor.py
  openie_extractor.py

app/embedding/
  service.py

app/answer/
  service.py
  evidence_pack.py
  prompting.py
  fallback.py

app/retrieval/
  planner.py

app/schemas/
  knowledge.py
  chat.py
```

说明：Phase 2 后，PreProcess 解析链已由 `routing -> parser -> IRDoc -> artifact` 承载。`app/services/runtime.py` 仍保留部分兼容函数和抽取实现，后续继续迁出 OpenIE、规则抽取、answer fallback 等细节。

---

## 3. Parse 契约

### 3.1 Request

```json
{
  "sourceId": "source-001",
  "sourceType": "text|md|sql|csv|json",
  "fileName": "baseline.md",
  "graphCategoryId": "uncategorized",
  "rawText": "# title"
}
```

### 3.2 Response

```json
{
  "sourceId": "source-001",
  "blocks": [
    {
      "blockId": "uuid",
      "sourceId": "source-001",
      "graphCategoryId": "uncategorized",
      "blockType": "title|paragraph|kv|csv|code|json",
      "sectionPath": "基线文档",
      "pageNo": 1,
      "rowNo": null,
      "colNo": null,
      "rawText": "# 基线文档",
      "normalizedText": "基线文档",
      "summary": "基线文档",
      "contentHash": "sha256",
      "metadata": {
        "parser": "markdown",
        "sourceType": "md",
        "chunkIndex": 1,
        "sectionPath": "基线文档",
        "span": {"lineNo": 1, "pageNo": 1},
        "lineNo": 1,
        "level": 1,
        "markdownRole": "heading"
      },
      "qualityScore": 1.0,
      "qualityFlags": []
    }
  ],
  "artifact": {
    "sourceType": "md",
    "parser": "markdown",
    "metadata": {"documentHash": "sha256", "blockCount": 1},
    "quality": {"score": 1.0, "flags": []}
  },
  "degraded": false
}
```

### 3.3 稳定性要求

- `blockId` 必须 source-scoped，同一内容不同 source 不能冲突。
- `contentHash` 当前跟随 block material 生成，后续 RG-09 会升级为文档级与 chunk 级去重键。
- `metadata.parser` 必须存在。
- `artifact.metadata.documentHash` 表示文档级内容哈希。
- `metadata.chunkIndex`、`metadata.span`、`summary`、`qualityScore`、`qualityFlags` 为 Phase 2 后稳定字段。
- 超长块会通过 `paragraph_semantic -> token_size` 兜底拆分，拆分块包含 `metadata.chunkPart`。

---

## 4. Embed 契约

### 4.1 Request

```json
{
  "blocks": [
    {"blockId": "block-001", "text": "normalized text"}
  ],
  "llmConfig": {}
}
```

### 4.2 Response

```json
{
  "embeddings": [
    {"blockId": "block-001", "vector": [0.01, 0.02]}
  ],
  "embeddingModel": "mlx-community/bge-m3-mlx-4bit",
  "embeddingVersion": "v1",
  "embeddingDim": 1024
}
```

### 4.3 稳定性要求

- 空 `blocks` 必须返回空 `embeddings`，且不调用外部模型。
- 非空 embedding 依赖模型配置，后续单独建立模型快照测试。

---

## 5. Extract 契约

### 5.1 Request

```json
{
  "sourceId": "source-001",
  "fileName": "device.txt",
  "graphCategoryId": "uncategorized",
  "blocks": [],
  "taxonomy": {},
  "enableLlmExtract": false,
  "llmConfig": {}
}
```

### 5.2 Candidate Entity

```json
{
  "tempId": "e1",
  "blockId": "block-001",
  "graphCategoryId": "uncategorized",
  "entityType": "ExtractedObject",
  "rawName": "塔吊A21212",
  "canonicalName": "塔吊A21212",
  "uniqueKey": {"name": "塔吊A21212"},
  "properties": {},
  "evidence": [
    {"blockId": "block-001", "weight": 0.85, "sourceType": "paragraph"}
  ],
  "evidenceRefs": [
    {"blockId": "block-001", "weight": 0.85, "sourceType": "paragraph"}
  ],
  "evidenceBlockIds": ["block-001"],
  "mentionSpans": [
    {"sourceId": "source-001", "blockId": "block-001", "start": 0, "end": 8, "lineNo": 1, "pageNo": 1}
  ],
  "confidence": 0.84,
  "confidenceBreakdown": {"extractor": 0.35, "taxonomy": 0.2, "span": 0.2, "context": 0.09, "total": 0.84},
  "extractor": "rule|llm",
  "extractorVersion": "v1",
  "mergeKey": "ExtractedObject:塔吊a21212",
  "qualityFlags": [],
  "reviewStatus": "accepted|reviewing",
  "status": "accepted|reviewing"
}
```

### 5.3 Candidate Relation

```json
{
  "sourceTempId": "e1",
  "targetTempId": "e2",
  "graphCategoryId": "uncategorized",
  "relationType": "HAS_COMPONENT",
  "rawPredicate": "HAS_COMPONENT",
  "direction": "forward",
  "properties": {},
  "evidence": [
    {"blockId": "block-001", "weight": 0.85, "sourceType": "paragraph"}
  ],
  "evidenceRefs": [
    {"blockId": "block-001", "weight": 0.85, "sourceType": "paragraph"}
  ],
  "evidenceBlockIds": ["block-001"],
  "evidenceSpan": {"sourceId": "source-001", "blockId": "block-001", "start": 0, "end": 16, "lineNo": 1, "pageNo": 1},
  "confidence": 0.84,
  "confidenceBreakdown": {"entity": 0.25, "trigger": 0.25, "typePair": 0.2, "span": 0.15, "direction": 0.1, "total": 0.84},
  "extractor": "rule|llm",
  "extractorVersion": "v1",
  "dedupKey": "e1:HAS_COMPONENT:e2",
  "validationFlags": [],
  "reviewStatus": "accepted|reviewing",
  "status": "accepted|reviewing"
}
```

### 5.4 Response

```json
{
  "candidateEntities": [],
  "candidateRelations": [],
  "degraded": false,
  "llmExtractedBlocks": 0,
  "llmExtractBlockBudget": 12
}
```

### 5.5 稳定性要求

- 所有候选必须受 taxonomy 约束。
- LLM OpenIE 输出必须经过 JSON、taxonomy、实体引用校验。
- 每个候选必须有 `evidence` 或 `evidenceBlockIds`。
- 每个候选必须有 `evidenceRefs`，实体应有 `mentionSpans`，关系应有 `evidenceSpan`。
- `confidenceBreakdown` 用于解释候选进入 accepted/reviewing 的依据。
- `mergeKey` / `dedupKey` 为 Phase 3 合并治理做准备。
- `status=accepted` 只代表候选层接受，不代表 published graph。

---

## 6. Java 阶段服务边界

Phase 1 后 Java 知识摄入服务边界如下：

```text
KnowledgeIngestionPipeline
  兼容旧入口，只调用 Orchestrator

KnowledgeIngestionOrchestrator
  负责编排阶段顺序与失败处理

KnowledgeTaskStateService
  统一任务状态、完成、失败更新

ParseStageService
  调用 Python parse 并持久化 block

EmbeddingStageService
  调用 Python embed 并回写 embedding

ExtractStageService
  调用 Python extract，持久化 candidate，构建 draft node/edge payload

GraphBuildStageService
  创建 draft graph 并写入 Neo4j draft
```

### 6.1 阶段状态要求

- parse 阶段失败：parse / extract / graph_build 均标记 failed。
- extract 阶段失败：extract / graph_build 标记 failed，parse 已完成结果保留。
- graph_build 阶段失败：graph_build 标记 failed，candidate 结果保留。

当前 Phase 1 保持原有失败处理行为，后续 Phase 2/3 再细化阶段级失败恢复。

---

## 7. Phase 1 验收基线

Python：

```bash
cd ai-service-python
.venv/bin/python -m pytest tests
```

当前结果：

```text
10 passed
```

Java：

```bash
cd backend-java
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn test
```

当前结果：

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

说明：默认终端 JDK 为 11，需显式使用 Java 25 执行 Maven。

---

## 8. 后续演进

Phase 2 将继续把 `app/services/runtime.py` 中的实现迁入领域模块：

- parser routing
- IRDoc / IRBlock
- structure detect
- chunking
- dedup / quality
- extraction evidence grounding

Phase 1 的边界目标是先让路由、领域入口、Java 阶段服务边界稳定，不改变主链路行为。
