# Phase 0 PreProcess 重构基线与抽取评测报告

## 1. 阶段目标

本阶段对应任务：

- RG-00 建立重构基线
- RG-00A 建立实体/关系抽取评测集

目标是在正式拆分 `ai-service-python/app/main.py` 和 Java 编排前，先固定当前主链路行为，并为后续重构建立可量化的抽取质量门禁。

---

## 2. 本阶段完成内容

### 2.1 主接口快照基线

已新增确定性 API 快照测试：

- 文件：`ai-service-python/tests/test_api_baseline_snapshots.py`
- 快照数据：`ai-service-python/tests/fixtures/extraction/api_snapshots.json`

覆盖接口/路径：

| 能力 | 覆盖方式 | 是否依赖外部模型 |
| --- | --- | --- |
| `/knowledge/parse` | Markdown 解析快照 | 否 |
| `/knowledge/extract` | 通用实体/关系抽取快照 | 否 |
| `/knowledge/embed` | 空 blocks 返回元数据 | 否 |
| `/chat/answer` | 无证据兜底回答 | 否 |

说明：embedding 非空路径依赖本地/远端 embedding 模型，本阶段只固定无外部依赖的稳定契约；后续模型配置稳定后再补非空 embedding golden snapshot。

### 2.2 实体/关系抽取 Gold Set

已新增抽取评测集：

- 文件：`ai-service-python/tests/test_extraction_gold_set.py`
- Gold 数据：`ai-service-python/tests/fixtures/extraction/gold_cases.json`

覆盖样本类型：

| 样本 | 覆盖点 |
| --- | --- |
| `generic_device_identifiers` | 对象、编号、数量、组成关系、编号关系、数量关系 |
| `markdown_components` | Markdown 业务文本、枚举组成、用途/取值关系 |
| `sql_ddl_table_columns` | SQL DDL 表、字段、主键、类型、约束、注释抽取 |
| `generic_bad_entity_reject` | 坏实体反例过滤 |

### 2.3 坏实体过滤补强

已在当前抽取逻辑中补充最小坏实体过滤：

- 文件：`ai-service-python/app/main.py`
- 新增常量：`GENERIC_BAD_ENTITY_NAMES`
- 过滤入口：`clean_generic_phrase()`

当前拦截：

- `系统能力`
- `相关信息`
- `其他内容`
- `五个部门` 这类数量前缀泛化短语

这样可以避免“系统能力包含相关信息、其他内容和五个部门”被错误抽成实体。

---

## 3. 评测指标

Gold set 当前执行以下门禁：

| 指标 | P0 阈值 | 当前结果 |
| --- | --- | --- |
| Entity Precision | >= 0.90 | 通过 |
| Entity Recall | = 1.00 | 通过 |
| Relation Precision | >= 0.85 | 通过 |
| Relation Recall | = 1.00 | 通过 |
| Evidence Span Coverage | >= 0.95 | 通过 |
| Bad Entity Reject Rate | >= 0.90 | 通过 |

说明：当前 evidence 粒度为 evidence block，后续 RG-10A 会继续升级到 TextUnit/span 级 evidence grounding。

---

## 4. 验证命令

在 `ai-service-python` 目录执行：

```bash
.venv/bin/python -m pytest tests
```

本次验证结果：

```text
collected 10 items

tests/test_api_baseline_snapshots.py ....
tests/test_extraction_gold_set.py .
tests/test_knowledge_extraction.py .....

10 passed in 0.27s
```

---

## 5. 依赖补充

已在 `ai-service-python/pyproject.toml` 新增 dev 依赖：

```toml
[project.optional-dependencies]
dev = [
  "pytest>=8.0.0"
]
```

本地验证时已执行：

```bash
.venv/bin/python -m pip install -e '.[dev]'
```

---

## 6. 阶段结论

Phase 0 已完成。

当前系统已经具备：

1. 可一键运行的 Python 抽取回归测试。
2. 主接口确定性快照基线。
3. 实体/关系 gold set。
4. 坏实体反例门禁。
5. 后续 Phase 1 重构的质量保护网。

下一阶段可以进入 Phase 1：结构拆分，不改主行为。
