# MD 知识抽取评测链路与性能设计

## 目标

验证 Markdown 知识入图链路是否能稳定产出可追溯实体关系，并用人工金标、直接 AI 抽取、系统端到端入图三方对比来迭代抽取策略。

## 链路设计

```text
Markdown 文档
  -> parse: 标题/段落/列表/表格切块，保留 rawText、normalizedText、sectionPath、lineNo
  -> evidence graph: Document / Section / SourceBlock / HAS_SECTION / IN_SECTION / HAS_EVIDENCE
  -> rule extraction: 键值、数量、编号、枚举、SQL/DDL 等确定性事实
  -> bounded LLM OpenIE: 仅选有限高价值正文块，限制块数与单块字符数
  -> candidate persistence: graph_candidate_entity / graph_candidate_relation
  -> graph draft: graph_asset / graph_revision，预览按 sourceId 绑定
  -> evaluation: 与 gold entities / gold relations 对比，输出召回、噪声、耗时、证据覆盖
```

## 性能边界

| 文档规模 | 目标行为 | 前台目标耗时 | LLM 策略 |
| --- | --- | ---: | --- |
| 小文档 <= 30 blocks | 同步完成 parse/extract/graph_build | <= 30s | 最多 12 个正文块，每块 900 字符 |
| 中文档 31-200 blocks | 先出结构图和规则候选，LLM 可降级 | <= 90s | 后续应改异步 top-N 高价值块 |
| 大文档 > 200 blocks | 前台不等待完整 LLM | <= 10s 可见结构图 | 必须异步批处理 |

## 评测方法

1. 人工准备 gold Markdown 与期望实体/关系。
2. 直接调用 Python `build_blocks + extract_candidates` 得到 direct AI 结果。
3. 通过 `/api/v1/knowledge/sources` 跑系统完整链路，得到 blocks、candidates、graph_asset。
4. 对比 direct 与 system 的 blocks、实体、关系、耗时、sourceId 绑定。
5. 按差距调整切块、规则抽取、LLM prompt、LLM 预算和过滤策略。
6. 用 holdout Markdown 再跑，避免只拟合单一样本。

## 验收阈值

| 指标 | 最低要求 |
| --- | ---: |
| 实体召回率 | >= 50%（当前通用本体阶段），目标 >= 70% |
| 关系召回率 | >= 30%（当前 OpenIE 阶段），目标 >= 60% |
| 证据覆盖率 | 100% 候选必须能追溯 block |
| sourceId 图谱绑定 | 当前 sourceId 查询结果必须等于 1 张图 |
| 空结果率 | 0 |

## 当前合理性判断

当前实现已具备 parse、结构证据图、规则/LLM 抽取、候选落库、图谱预览能力，但同步执行仍只适合小文档。下一阶段应把 parse/extract/embedding/graph_build 改成任务队列或调度器异步执行，并把 embedding 回填作为独立任务，避免阻塞实体关系抽取。