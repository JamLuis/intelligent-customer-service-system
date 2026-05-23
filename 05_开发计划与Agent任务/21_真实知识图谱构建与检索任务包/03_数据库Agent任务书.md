# 数据库 Agent 任务书

## 1. 角色边界

数据库 Agent 只负责 PostgreSQL、pgvector、初始化 SQL、seed 数据和数据库验证脚本，不写 Java/Python/前端业务代码。

## 2. 必读文件

1. `03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md` §7、§8、§9、§13.4
2. `04_数据库与API/08_数据库设计.md`
3. `04_数据库与API/08A_建表SQL/knowledge_source.sql`
4. `04_数据库与API/08A_建表SQL/graph_asset.sql`
5. `infra/postgres/init.sql`

## 3. 输出位置

| 类型 | 路径 |
| --- | --- |
| 新增 SQL | `04_数据库与API/08A_建表SQL/` |
| 平台保护类型 seed | `04_数据库与API/08A_建表SQL/graph_protected_taxonomy.sql` |
| 示例本体包（不自动加载） | `04_数据库与API/08A_建表SQL/examples/` |
| init 引用 | `infra/postgres/init.sql` |
| 验证说明 | `06_测试与发布/` 或任务交接中记录 |

## 4. 任务拆分

### KG-DB-001 新增真实 KG migration

- 目标：新增真实 KG 需要的元数据表。
- 需要新增表：
  - `graph_category`
  - `graph_entity_type`
  - `graph_relation_type`
  - `knowledge_block`
  - `graph_candidate_entity`
  - `graph_candidate_relation`
  - `graph_review_task`
  - `graph_query_log`
- 需要检查并扩展已有表：
  - `knowledge_source`：确认 `graph_category_id`、`graph_category_name`、`source_hash`、`object_key` 或同等字段。
  - `graph_asset`：确认 `graph_category_id`、`entity_types`、`relation_types`、`classification_path`、`active_revision_id`。
  - `graph_build_batch` / `graph_revision`：确认能记录 batch、revision、active 状态。
- DoD：
  - SQL 文件可被 PostgreSQL 16 执行。
  - 所有表包含 `tenant_id`、`project_id` 或明确说明为何不需要。
  - 所有 JSONB 字段有默认值或允许空的解释。
  - candidate/review/query 表有必要索引。

### KG-DB-002 平台保护类型 seed（不内置业务本体）

- 目标：把 07F §1A、§7 规定的"平台保护类型"落成初始数据，**不再 seed 任何业务领域本体**。
- 必须包含的实体类型（仅 3 个保护类型，作用域 `tenant_id='__platform__'`, `project_id='*'`）：
  - `SourceBlock`、`Document`、`Section`
- 必须包含的关系类型（仅 3 个保护关系）：
  - `HAS_EVIDENCE`（from=`*`, to=`SourceBlock`）
  - `HAS_SECTION`（from=`Document`, to=`Section`）
  - `IN_SECTION`（from=`SourceBlock`, to=`Section`）
- 禁止在该 seed 中插入任何 `graph_category` 数据；分类、业务实体类型、业务关系类型一律由租户通过 KG-016/019/020 接口运行时注册。
- DoD：
  - seed 可重复执行，ON CONFLICT 更新但不重复；
  - 启动后调用 `GET /api/v1/graphs/taxonomy` 必须返回 `categories=[]`、`entityTypes=[3 个保护类型]`、`relationTypes=[3 个保护关系]`；
  - `04_数据库与API/08A_建表SQL/examples/` 下保留示例本体包（如 `graph_taxonomy_example_marine.sql`），但 `infra/postgres/init.sql` 不得加载；
  - 任何对保护类型的修改/删除尝试在数据库或后端必须可被识别为受保护项（`ICSS-KG-403-PROTECTED_TYPE`）。

### KG-DB-003 pgvector block 索引

- 目标：支持 `knowledge_block.embedding` 语义召回。
- 要求：
  - 确认 `vector` 扩展在 init 中创建。
  - `embedding` 维度先按配置占位，如 1024/1536 必须在文档中说明。
  - 建立 HNSW 或 IVFFLAT 索引，若本地版本不支持则降级并记录。
- DoD：
  - 向量查询 SQL 可以执行。
  - `tenant_id + project_id + graphCategoryId` 过滤不丢失。

## 5. 约束

1. 不要删除已有 15 张 P0 表。
2. 不要把 Neo4j 节点关系完整塞入 PostgreSQL JSON 作为唯一存储。
3. 不要让 seed 依赖 Java 程序启动。
4. 所有新增 SQL 必须被 `infra/postgres/init.sql` 引用或在交接单中说明执行方式。

## 6. 验证命令建议

```bash
./scripts/verify-infra.sh
```

如需要单独验证 SQL，可使用 Docker PostgreSQL 容器执行 `psql`，但不得提交本地数据库数据目录。

## 7. 交接给下游

交接给接口/后端 Agent 时必须说明：

1. 新增表名和字段是否与 07F 有偏差。
2. 平台保护类型 seed 实际加载的实体/关系。
3. 向量维度是多少。
4. 哪些字段暂时 nullable。
5. 已运行的 SQL 验证结果。
6. 提醒下游：业务分类/实体/关系**全部需要租户通过 KG-016/019/020 在运行时注册**，禁止前端、后端、AI 服务任何地方硬编码业务本体名称。
