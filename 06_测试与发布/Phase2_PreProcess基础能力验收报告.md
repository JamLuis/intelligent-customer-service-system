# Phase 2 PreProcess 基础能力验收报告

## 1. 阶段范围

对应任务规划 RG-05 ~ RG-10A：

- RG-05：统一 IR 契约 `IRDoc / IRBlock / IRSpan / IRAsset / ParseArtifact / QualityReport`
- RG-06：parser routing 与按 sourceType / fileName 后缀分发
- RG-07：metadata enrichment，包括 section、span、chunkIndex、summary
- RG-08：paragraph semantic chunk 与 token size 兜底切分
- RG-09：content hash、document hash、重复块标记、quality score / flags
- RG-10A：候选实体/关系增强字段，包括 evidenceRefs、mentionSpans、evidenceSpan、confidenceBreakdown、mergeKey、dedupKey、reviewStatus

## 2. 代码落点

- Python PreProcess 入口：`ai-service-python/app/preprocess/service.py`
- Parser routing：`ai-service-python/app/preprocess/routing.py`
- IR 契约：`ai-service-python/app/preprocess/ir/models.py`
- Artifact 构建：`ai-service-python/app/preprocess/artifact.py`
- 元数据、质检、去重、切分：`ai-service-python/app/preprocess/metadata/`、`quality/`、`chunking/`、`normalize/`
- Parser 实现：`ai-service-python/app/preprocess/parsers/`
- 候选增强：`ai-service-python/app/services/runtime.py`
- Phase 2 测试：`ai-service-python/tests/test_preprocess_phase2.py`

## 3. 验收结果

### Python AI Service

命令：

```bash
cd ai-service-python && .venv/bin/python -m pytest tests
```

结果：

```text
14 passed in 0.31s
```

覆盖点：

- 既有 API baseline snapshot 保持通过。
- 既有抽取 gold set 保持通过。
- routing 可将 `text + .md/.sql/.csv` 分发到对应 parser。
- parse response 包含 `artifact.metadata.documentHash` 与 `artifact.quality`。
- block 包含 `summary`、`qualityScore`、`qualityFlags`、`metadata.chunkIndex`、`metadata.span`。
- 超长文本会被拆成多个 chunk，并带 `metadata.chunkPart`。
- candidate entity 包含 `evidenceRefs`、`mentionSpans`、`confidenceBreakdown`、`mergeKey`、`qualityFlags`、`reviewStatus`。
- candidate relation 包含 `evidenceRefs`、`evidenceSpan`、`confidenceBreakdown`、`dedupKey`、`direction`、`validationFlags`、`reviewStatus`。

## 4. 结论

Phase 2 已完成基础 PreProcess 能力落地，并保持 Phase 0 / Phase 1 的行为基线不回归。当前实现为 Phase 3 的候选治理、人工审核、图谱发布提供了稳定字段基础。

## 5. 后续约束

- Phase 3 不应绕过 `ParseArtifact` 和 enhanced candidate contract。
- 后续字段扩展应优先追加，不删除当前稳定字段。
- LLM 抽取增强必须进入 `confidenceBreakdown`、`evidenceRefs`、`reviewStatus` 契约，不直接写 published graph。
