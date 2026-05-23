# 真实知识图谱构建与检索任务包总览

## 1. 任务包定位

本目录用于指导后续 Agent 根据 `03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md` 开始具体开发。

本任务包只覆盖“真实知识图谱构建与检索”功能，不覆盖生产写操作、审批执行闭环、真实 wnx 系统 Adapter 替换。

## 2. 核心目标

将当前 Mock 图谱能力升级为真实可用链路：

```text
结构化文本 / 非结构化文件
  -> 解析为 knowledge_block
  -> 抽取 candidate entity / candidate relation
  -> 规范化、冲突检测、复核
  -> 写入 PostgreSQL 元数据与 pgvector
  -> 写入 Neo4j 草稿图谱
  -> 发布为 active revision
  -> 支持管理查询、路径查询、诊断检索
  -> 前端可查看证据、编辑草稿、发布回滚
```

## 3. 关键原则

1. 不允许继续只做 Mock JSON 图谱。
2. 不允许只切 chunk 后 embedding，再靠点积或余弦相似度判断关系。
3. 向量召回只能作为证据补充和候选召回，不是最终关系判断。
4. LLM 输出只能生成候选，不能直接写入 published 图谱。
5. Java Backend 是发布图谱和写 Neo4j 的唯一入口。
6. Neo4j 中的实体和关系必须绑定 `sourceRefs`、`evidenceRefs`、`revisionId`、`status`、`confidence`。
7. 诊断检索只能使用 `published + activeRevision` 的图谱。
8. 所有 Agent 必须保留现有 Mock API 的兼容边界，采用渐进替换。

## 4. 文件清单

| 文件 | 用途 | 主要读者 |
| --- | --- | --- |
| `00_README_任务包总览.md` | 本任务包入口、原则和执行顺序 | 所有 Agent |
| `01_阶段里程碑与依赖关系.md` | 阶段推进、合并顺序、依赖图 | 主控 Agent、技术负责人 |
| `02_任务总表.md` | 所有任务 ID、输入、输出、依赖、DoD | 所有 Agent |
| `03_数据库Agent任务书.md` | PostgreSQL/pgvector/seed/migration 任务 | 数据库 Agent |
| `04_接口Agent任务书.md` | API 契约补充、错误码、Mock 场景 | 接口 Agent |
| `05_后端JavaAgent任务书.md` | Java Service/Repository/Neo4j/任务状态机 | 后端 Agent |
| `06_PythonAIServiceAgent任务书.md` | Parser、Extractor、Normalizer、Embedding | AI Service Agent |
| `07_前端Agent任务书.md` | 录入、候选预览、图谱维护、证据抽屉 | 前端 Agent |
| `08_测试联调Agent任务书.md` | 数据样例、接口测试、E2E、权限和回归 | 测试 Agent |
| `09_交接与验收清单.md` | 开工检查、交接格式、最终验收 | 所有 Agent |

## 5. 推荐执行顺序

1. 数据库 Agent：先完成 migration、taxonomy seed、pgvector 索引。
2. 接口 Agent：补齐 API 文档、错误码、请求/响应示例。
3. Java 后端 Agent：完成真实数据读写骨架、任务状态机、Neo4j Repository。
4. Python AI Service Agent：实现解析、抽取、归一、冲突检测、embedding 返回。
5. Java 后端 Agent：接入 Python 返回，保存 block/candidate，写 Neo4j draft/publish。
6. 前端 Agent：把页面从 Mock 图谱切到真实 API，补齐候选预览、证据、版本。
7. 测试 Agent：按 source -> block -> candidate -> graph -> query 全链路验证。
8. 主控 Agent：合并、解决契约冲突、更新总控文档。

## 6. 必读输入

1. `03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md`
2. `03_技术方案与架构/07E_统一工程知识图谱项目框架.md`
3. `04_数据库与API/08_数据库设计.md`
4. `04_数据库与API/09_API文档.md`
5. `05_开发计划与Agent任务/21_真实知识图谱构建与检索任务包/02_任务总表.md`

## 7. 每个 Agent 完成后必须回写

1. 修改了哪些文件。
2. 完成了哪些任务 ID。
3. 哪些任务仍阻塞。
4. 是否改变 API / DB / 状态枚举。
5. 运行了哪些验证命令。
6. 需要下一个 Agent 注意什么。

## 8. V0.3.1 加固提示（随 07F V0.3.1 上线同步升级）

所有 Agent 动工前必须额外确认下面 5 条不可越红线，详细条款及代码示例见《07F §8 / §10A.4 / §12.1 / §13》：

1. **Neo4j 仅限两种商用 Label**：`:KnowledgeEntity` + `:RELATION`，`entityType` / `relationType` 走属性；平台保护实体仅 `:SourceBlock/:Document/:Section`。
2. **归一 / 合并必面 6 信号加权**（0.40 UniqueKey + 0.20 Alias + 0.15 Regex + 0.10 Embedding + 0.10 CodeGraphRef + 0.05 LLMVerify）且阈值 ≥0.85 、含任一强信号（w1/w2/w3）才可用，embedding-only 仅产 `cross_link_hint`。
3. **诊断 / 邻居 / 路径 Cypher 必须携 Traversal Budget**（maxNodes/maxEdges/maxFanOutPerNode/maxDepthHardCap/timeoutMs）且超限返 `truncated=true`。
4. **检索走 Hybrid Retrieval**（0.45 Vector + 0.35 BM25 + 0.20 GraphBoost 可选 +0.05 Recency），向量路必须过滤 `embedding_model,embedding_version`。
5. **实体/关系 status 枚举加 `frozen`**，published ↔ frozen 仅能通过 `graph:freeze`/`graph:unfreeze` API，frozen 节点拒被 auto-merge/auto-normalize。

不同角色的 V0.3.1 增量任务见 `02_任务总表.md` §2A（KG-DB-004/005、KG-API-004、KG-BE-011~015、KG-AI-006/007、KG-FE-005、KG-QA-005）。
