# PreProcess 与自进化图谱重构任务规划

## 1. 文档目标

本文档用于将《自进化知识图谱重构分析与落地建议》中的建议转成可执行任务。

本文档关注：

- 重构范围
- 分阶段任务
- 每阶段产物
- 依赖关系
- 验收标准
- Agent 派发建议

适用范围：

- ai-service-python
- backend-java
- frontend
- 04_数据库与API
- 06_测试与发布

---

## 2. 重构总目标

本轮重构的总目标不是功能堆叠，而是完成以下四件事：

1. 把 Python AI Service 从单文件单体演进为可扩展流水线。
2. 把当前 parse/extract/graph build 主链路演进为可追溯的 PreProcess + Candidate 治理链。
3. 把 Java 后端从“顺序执行器”演进为“阶段编排器”。
4. 为后续 Cluster、Pattern Mining、Auto Taxonomy、Planner、Feedback 预留稳定挂载点。

目标完成后，系统链路应演进为：

```text
source
↓
preprocess artifact
↓
text unit / chunk
↓
candidate entity/relation
↓
review / merge / publish
↓
graph / retrieval / planner
↓
feedback
```

---

## 3. 任务分期总览

| 阶段 | 编号 | 目标 | 结果 |
| --- | --- | --- | --- |
| Phase 0 | RG-00 | 基线冻结与回归保护 | 当前主链路可回归 |
| Phase 1 | RG-01 ~ RG-04 | Python / Java 结构拆分 | 不改行为先拆边界 |
| Phase 2 | RG-05 ~ RG-10A | PreProcess 与稳定抽取基础能力落地 | 形成可追溯 artifact 与可信候选 |
| Phase 3 | RG-11 ~ RG-15 | Candidate 治理与图谱发布补强 | 形成治理闭环 |
| Phase 4 | RG-16 ~ RG-19 | 前端治理能力与任务可视化 | 形成管理与审核入口 |
| Phase 5 | RG-20 ~ RG-24 | Planner / Feedback 预埋与升级 | 为自进化闭环打底 |

建议执行策略：

- Phase 0 到 Phase 2 为本轮主交付范围
- Phase 3 为紧随其后的治理补强
- Phase 4、Phase 5 可并行预研，但不建议阻塞主链路重构

---

## 4. Phase 0：基线冻结与回归保护

### RG-00 建立重构基线

- 目标：在重构前固定当前 parse / embed / extract / graph build 主链路行为。
- 涉及模块：ai-service-python、backend-java、06_测试与发布。
- 主要产物：
  - 当前主接口样例请求与响应快照
  - 主链路 smoke 用例
  - 文档样本集与 SQL/Markdown/CSV 样本集
- 建议动作：
  - 固定 `/knowledge/parse`、`/knowledge/embed`、`/knowledge/extract`、`/chat/answer` 请求样本
  - 固定 Java 侧知识录入到 draft graph 的回归用例
- 验收标准：
  - 重构前后，现有样本在关键字段上保持兼容
  - 至少能覆盖 markdown、sql、csv 三种输入
  - 实体/关系抽取必须具备 gold set，包含正例、反例、边界样本
  - 每个候选实体/关系必须可追踪到 evidence block 或 span

### RG-00A 建立实体/关系抽取评测集

- 目标：先定义“抽得准”的判断标准，避免后续重构只验证接口不验证抽取质量。
- 涉及模块：ai-service-python、06_测试与发布。
- 主要产物：
  - `tests/fixtures/extraction/` 样本目录
  - SQL DDL、Markdown、枚举、key-value、坏实体反例、关系方向反例样本
  - gold entity / gold relation 标注文件
  - extraction regression test
- 建议动作：
  - 为每个样本标注期望实体、关系、类型、方向、证据文本
  - 明确 bad entity 列表，例如“相关信息”“五个部门”“系统能力”等泛词
  - 明确关系方向反例，例如“管理/被管理”“依赖/被依赖”“调用/被调用”
- 验收标准：
  - Entity Precision P0 不低于 0.90
  - Relation Precision P0 不低于 0.85
  - Evidence Span Coverage P0 不低于 0.95
  - Bad Entity Reject Rate P0 不低于 0.90
  - 测试可在本地一键运行

---

## 5. Phase 1：结构拆分，不改主行为

### RG-01 拆分 Python API 层与领域层

- 目标：把 `ai-service-python/app/main.py` 的 HTTP 路由与业务逻辑分离。
- 涉及模块：ai-service-python。
- 主要产物：
  - `app/api/routes/health.py`
  - `app/api/routes/knowledge.py`
  - `app/api/routes/chat.py`
  - `app/api/routes/diagnosis.py`
  - `app/schemas/*`
- 验收标准：
  - 现有 API 路径不变
  - 主行为不变
  - `main.py` 只保留应用装配和路由注册

### RG-02 拆分 Python parse / extract / answer 模块

- 目标：把主逻辑拆入 preprocess、extraction、answer、embedding、retrieval 目录。
- 涉及模块：ai-service-python。
- 主要产物：
  - `app/preprocess/parsers/*`
  - `app/extraction/*`
  - `app/embedding/service.py`
  - `app/answer/*`
  - `app/retrieval/planner.py`
- 验收标准：
  - 原有函数迁移后行为兼容
  - 每个模块可单独单测
  - `main.py` 不再包含大段规则抽取与文本处理逻辑

### RG-03 拆分 Java 知识编排阶段服务

- 目标：把 `KnowledgeIngestionPipeline` 改为总编排器。
- 涉及模块：backend-java。
- 主要产物：
  - `KnowledgeIngestionOrchestrator`
  - `ParseStageService`
  - `EmbeddingStageService`
  - `ExtractStageService`
  - `GraphBuildStageService`
  - `KnowledgeTaskStateService`
- 验收标准：
  - 知识录入主流程仍可跑通
  - 阶段职责从单类中拆出
  - 每个阶段具备独立异常信息与状态更新

### RG-04 抽取统一 DTO 与 schema 契约

- 目标：避免 Python/Java 两边在字段上继续发散。
- 涉及模块：ai-service-python、backend-java、04_数据库与API。
- 主要产物：
  - parse response schema
  - embedding response schema
  - extract response schema
  - preprocess artifact 草案结构
- 验收标准：
  - 关键对象字段定义固定
  - 文档契约与实现字段一致

---

## 6. Phase 2：PreProcess 基础能力落地

### RG-05 定义统一 IR 契约

- 目标：建立 parser 的统一输出层。
- 涉及模块：ai-service-python、04_数据库与API。
- 主要产物：
  - `IRDoc`
  - `IRBlock`
  - `IRSpan`
  - `IRAsset`
  - `ParseArtifact`
- 验收标准：
  - markdown、sql、csv parser 都输出统一 IR
  - 后续 chunking 不再直接依赖原 parser 私有结构

### RG-06 实现 parser routing

- 目标：将当前 `infer_source_type()` + if/else 分发升级为可配置路由。
- 涉及模块：ai-service-python。
- 主要产物：
  - `preprocess/routing.py`
  - parser registry
  - sourceType 到 parser 的映射配置
- 验收标准：
  - markdown、sql、csv 已接入路由层
  - 新增 parser 无需再改主入口 if/else

### RG-07 建立 structure detect 与 metadata build

- 目标：为 chunk 和 candidate 提供可追溯 sectionPath / parser / span / source 信息。
- 涉及模块：ai-service-python、backend-java、04_数据库与API。
- 主要产物：
  - `normalize/structure_detector.py`
  - `metadata/builder.py`
  - block metadata 扩展字段
- 验收标准：
  - 解析结果包含 sectionPath、chunkIndex、parser、confidence 等元数据
  - Java 入库后可查询这些字段

### RG-08 实现 paragraph semantic chunking 与兜底切块

- 目标：用“默认优先语义切块 + 兜底 token/字符切块”替代当前简单 block 逻辑。
- 涉及模块：ai-service-python。
- 主要产物：
  - `chunking/paragraph_semantic.py`
  - `chunking/token_size.py`
  - `TextUnit` 输出结构
- 验收标准：
  - 长文本不再只按粗粒度 block 输出
  - chunk 可保留 sectionPath 与 span
  - 超长块有明确兜底策略

### RG-09 实现 content hash 与重复检测

- 目标：避免重复文档和重复 chunk 进入候选层。
- 涉及模块：ai-service-python、backend-java、04_数据库与API。
- 主要产物：
  - `quality/dedup.py`
  - `content_hash` 字段
  - 去重策略文档
- 验收标准：
  - 文档级重复可识别
  - chunk 级重复可标记
  - 重复数据不会重复进入 extract 主链路

### RG-10 实现 summary build 与 quality check

- 目标：为 chunk 提供摘要和质量门禁。
- 涉及模块：ai-service-python、backend-java、frontend。
- 主要产物：
  - `summary` 字段
  - `quality_score` / `quality_flags`
  - 低质量数据标记逻辑
- 验收标准：
  - chunk 具备摘要字段
  - 空块、噪声块、超长块、低覆盖块可标记
  - 管理端可查看质量状态

### RG-10A 建立实体/关系稳定抽取核心链路

- 目标：把当前 rule-first + LLM OpenIE 抽取升级为可解释、可测试、可治理的抽取子流水线。
- 涉及模块：ai-service-python、backend-java、04_数据库与API、06_测试与发布。
- 主要产物：
  - `extraction/models.py`
  - `extraction/candidate_generator.py`
  - `extraction/evidence_grounder.py`
  - `extraction/validator.py`
  - `extraction/confidence.py`
  - `extraction/merger.py`
  - 候选实体/关系增强字段契约
- 设计要求：
  - 所有候选必须受 taxonomy 约束，不允许 LLM 自由创造类型和关系
  - 所有候选必须绑定 sourceId、blockId、TextUnit、span 或 parser 结构来源
  - SQL DDL、表格、代码、日志等结构化来源必须优先走 parser-aware extractor
  - LLM OpenIE 只做补充召回，不能直接决定 published graph
  - 没有 evidence span 的候选不能自动 accepted
- 候选实体必备字段：
  - `entityType`
  - `rawName`
  - `canonicalName`
  - `mentionSpans`
  - `evidenceRefs`
  - `extractor`
  - `extractorVersion`
  - `confidenceBreakdown`
  - `mergeKey`
  - `qualityFlags`
  - `reviewStatus`
- 候选关系必备字段：
  - `relationType`
  - `sourceTempId`
  - `targetTempId`
  - `rawPredicate`
  - `direction`
  - `evidenceSpan`
  - `extractor`
  - `confidenceBreakdown`
  - `dedupKey`
  - `validationFlags`
  - `reviewStatus`
- 验收标准：
  - 当前 SQL DDL 抽取逻辑迁入 parser-aware extractor 后行为兼容
  - 当前枚举、key-value、generic facts 规则迁入 rule extractor 后行为兼容
  - LLM OpenIE 输出必须经过 JSON schema、taxonomy、span grounding、confidence gating 校验
  - 同名实体和重复关系经过 merge/dedup 后不会重复膨胀
  - RG-00A 抽取评测集通过设定阈值

---

## 7. Phase 3：Candidate 治理与图谱发布补强

### RG-11 实现 entity / relation merge 机制

- 目标：避免多 chunk 并发抽取时同名实体重复膨胀。
- 涉及模块：ai-service-python、backend-java。
- 主要产物：
  - entity merge key
  - relation dedup key
  - merge 规则文档
- 验收标准：
  - 同名同类型实体可归并
  - 同 source-target-relation 的关系不会重复爆炸

### RG-12 扩展 candidate 审核状态机

- 目标：把 candidate 从“暂存结果”升级为“治理对象”。
- 涉及模块：backend-java、frontend、04_数据库与API。
- 主要产物：
  - candidate review status 扩展
  - approve / reject / merge / revise 操作契约
- 验收标准：
  - candidate 具备完整审核状态流转
  - 审核动作可回写数据库与审计记录

### RG-13 建立 draft graph 到 publish graph 闭环

- 目标：明确图谱正式发布门禁。
- 涉及模块：backend-java、frontend、Neo4j 相关模块。
- 主要产物：
  - `GraphPublishStageService`
  - publish API
  - graph revision 状态约束
- 验收标准：
  - draft、reviewing、published 状态清晰
  - 非审核通过数据不会直接进入正式图谱

### RG-14 建立 taxonomy proposal 输入面

- 目标：为后续 Auto Taxonomy 预留可落库入口。
- 涉及模块：backend-java、04_数据库与API、frontend。
- 主要产物：
  - taxonomy proposal 表/契约草案
  - proposal 管理接口草案
- 验收标准：
  - candidate 与 pattern 可沉淀为 proposal 输入
  - 前后端字段对齐

### RG-15 建立 pattern proposal 输入面

- 目标：让枚举、后缀、关系触发词等稳定模式可沉淀。
- 涉及模块：ai-service-python、backend-java、04_数据库与API。
- 主要产物：
  - pattern candidate 结构
  - pattern 提案入库接口草案
- 验收标准：
  - pattern 可从抽取链沉淀
  - 后续可接审核与 taxonomy 进化

---

## 8. Phase 4：前端治理能力与任务可视化

### RG-16 增加 preprocess 结果查看页

- 目标：可视化 parse artifact、chunk、metadata、quality。
- 涉及模块：frontend、backend-java。
- 验收标准：
  - 能按 source 查看 preprocess 产物
  - 能查看 sectionPath、summary、quality flags

### RG-17 增加 candidate 审核与合并页

- 目标：承接 approve / reject / merge / revise。
- 涉及模块：frontend、backend-java。
- 验收标准：
  - 候选实体与关系可审核
  - 合并动作可执行并可回显

### RG-18 增加任务链路追踪页

- 目标：展示 parse、embed、extract、graph build、publish 各阶段状态。
- 涉及模块：frontend、backend-java。
- 验收标准：
  - 能定位失败阶段、错误信息、重试次数
  - 能展示输入输出引用

### RG-19 增加 taxonomy / pattern proposal 管理页

- 目标：为后续治理层提供 UI 入口。
- 涉及模块：frontend、backend-java。
- 验收标准：
  - proposal 可查询、筛选、审核
  - 可关联来源 evidence 与 candidate

---

## 9. Phase 5：Planner / Feedback 预埋与升级

### RG-20 升级 retrieval plan 为证据路由器

- 目标：把当前 retrieval plan 从静态逻辑升级为可解释证据分流。
- 涉及模块：ai-service-python、backend-java。
- 验收标准：
  - graph/vector/bm25/mcp 路由逻辑可解释
  - 输出具备 route reason

### RG-21 接入 MCP 证据统一抽象

- 目标：让实时工具结果进入统一 evidence pack。
- 涉及模块：mcp-server-node、backend-java、ai-service-python。
- 验收标准：
  - MCP 结果可作为证据源参与 answer
  - evidence pack 可区分 source trust 与 freshness

### RG-22 建立 feedback event 契约

- 目标：让用户反馈、审核反馈、路径反馈可统一落库。
- 涉及模块：backend-java、frontend、04_数据库与API。
- 验收标准：
  - feedback 对象结构固定
  - 可沉淀到 evaluation store

### RG-23 建立 evaluation store 与离线样本输出

- 目标：为后续 rerank 与 Planner 优化准备数据。
- 涉及模块：backend-java、ai-service-python、06_测试与发布。
- 验收标准：
  - feedback / answer / route 可导出为评测样本
  - 有基本评测口径文档

### RG-24 预留 LangGraph Planner 接入点

- 目标：先建立接口边界，不强制本轮完成完整 Planner。
- 涉及模块：ai-service-python、backend-java。
- 验收标准：
  - 问答主链可切换到 planner service 边界
  - 保留 fallback 路径

---

## 10. 推荐执行顺序

推荐严格按以下顺序推进：

1. RG-00
2. RG-00A
3. RG-01
4. RG-02
5. RG-03
6. RG-04
7. RG-05
8. RG-06
9. RG-07
10. RG-08
11. RG-09
12. RG-10
13. RG-10A
14. RG-11
15. RG-12
16. RG-13
17. RG-16
18. RG-18
19. RG-14
20. RG-15
21. RG-17
22. RG-19
23. RG-20
24. RG-21
25. RG-22
26. RG-23
27. RG-24

原因：

- 先拆结构，避免新增能力继续堆在旧结构上
- 先补抽取评测，再补 preprocess 和抽取链路
- 先把任务链路可视化，再做更重的 taxonomy / planner 升级

---

## 11. Agent 派发建议

建议按专业面拆 Agent 任务，而不是按目录平均拆分。

### A 类：Python PreProcess Agent

负责：

- RG-00A
- RG-01
- RG-02
- RG-05
- RG-06
- RG-07
- RG-08
- RG-09
- RG-10
- RG-10A

### B 类：Java 编排与状态机 Agent

负责：

- RG-03
- RG-04
- RG-12
- RG-13
- RG-22
- RG-23

### C 类：图谱治理 Agent

负责：

- RG-11
- RG-14
- RG-15
- RG-19

### D 类：前端治理工作台 Agent

负责：

- RG-16
- RG-17
- RG-18
- RG-19

### E 类：Planner / Evidence Agent

负责：

- RG-20
- RG-21
- RG-24

---

## 12. 本轮建议交付边界

如果本轮只允许做一轮较大但可控的重构，建议交付边界控制在：

- RG-00 ~ RG-10A
- RG-16
- RG-18

即：

```text
先把结构拆开
+
先把 preprocess 基础打牢
+
先把任务与产物看得见
```

这样交付后，系统虽然还没有完整 taxonomy / feedback / planner 闭环，但已经从“原型抽取系统”升级为“可治理的知识生产线底座”。

---

## 13. 验收总标准

本轮重构完成后，应至少满足以下标准：

1. Python AI Service 不再由单个 `main.py` 承载主业务逻辑。
2. parse 输出不再是简单 block 列表，而是包含 metadata、summary、quality、hash 的 preprocess artifact。
3. 实体/关系抽取具备 gold set，且 Entity Precision、Relation Precision、Evidence Span Coverage 达到 P0 阈值。
4. 每个候选实体/关系都具备 evidence、confidenceBreakdown、qualityFlags 和 reviewStatus。
5. Java 侧知识编排可按阶段定位失败与重试。
6. candidate 具备更完整的治理状态，而不是只做一次性落库。
7. 前端可查看 preprocess 结果与任务阶段状态。
8. 主链路兼容当前 markdown、sql、csv 输入，不破坏现有知识录入流程。

---

## 14. 结论

本任务规划的核心不是把所有高级能力一次性做完，而是优先完成“让系统具备继续长大的结构”。

先把结构长对，再把能力补齐。

后续如果进入正式派单阶段，建议基于本文档继续拆成：

- 周级任务
- Agent 任务书
- 验收清单
- 风险登记

并同步回写项目状态总表、变更记录、风险与阻塞清单。
