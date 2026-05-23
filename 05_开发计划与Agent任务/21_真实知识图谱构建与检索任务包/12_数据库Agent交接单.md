# 数据库 Agent 交接单

## 1. 交接信息

- 交出 Agent：Copilot 数据库执行
- 接手 Agent：接口 Agent、后端 Java Agent、测试 Agent
- 交接日期：2026-05-23
- 对应任务编号：KG-DB-001、KG-DB-002、KG-DB-003

## 2. 当前完成情况

### 已完成

1. 新增真实 KG schema 文件：`04_数据库与API/08A_建表SQL/real_kg_schema.sql`。
2. 新增 taxonomy seed 文件：`04_数据库与API/08A_建表SQL/graph_taxonomy_seed.sql`。
3. 更新 `infra/postgres/init.sql`，将真实 KG schema 和 seed 纳入初始化。
4. 更新 `scripts/verify-infra.sh`，增加真实 KG 表、taxonomy seed 和 pgvector 索引检查。
5. 修复已有 Docker volume 中旧表不自动补列的问题：
   - `knowledge_source.sql` 增加 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS graph_category_id/graph_category_name/object_key/raw_text`。
   - `graph_asset.sql` 增加 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS graph_category_id/graph_category_name/entity_types/relation_types/classification_path/active_revision_id`。

### 未完成

1. 未实现 Java Repository 或 API。
2. 未实现 Python Parser/Extractor。
3. 未实现前端真实 API 接入。
4. 未新增正式测试用例文件，仅完成基础设施验证。

## 3. 新增表

| 表名 | 用途 |
| --- | --- |
| graph_category | 图谱分类，如地区-船舶、船舶-设备、设备-告警 |
| graph_entity_type | 实体类型本体，如 Device、Vessel、Protocol |
| graph_relation_type | 关系类型本体，如 INSTALLED_ON、BOUND_TO、USES_PROTOCOL |
| knowledge_block | 文件解析块，包含文本块、表格块、OCR 块和 pgvector embedding |
| graph_candidate_entity | 候选实体 |
| graph_candidate_relation | 候选关系 |
| graph_review_task | 低置信度、冲突、重复、安全等复核任务 |
| graph_query_log | 管理查询、诊断检索、向量召回和路径查询日志 |

## 4. taxonomy seed 结果

`./scripts/verify-infra.sh` 验证结果：

| 项 | 数量 |
| --- | --- |
| 真实 KG 新增表 | 8 |
| graph_category enabled | 8 |
| graph_entity_type enabled | 28 |
| graph_relation_type enabled | 22 |
| pgvector HNSW 索引 | idx_knowledge_block_embedding_hnsw |

## 5. 向量维度

- `knowledge_block.embedding` 当前定义为 `vector(1536)`。
- 该维度用于 OpenAI-like / 通用 embedding provider 的默认占位。
- 后续如确定使用 1024 维或其他模型，必须在数据库迁移、AI embedding client 和 API 文档中同步修改。
- 向量召回只作为证据补充，不作为最终关系判断。

## 6. 下游注意事项

1. 接口 Agent 可以基于新表补齐 KG-001~KG-015 API 契约。
2. 后端 Java Agent 必须从 `graph_category`、`graph_entity_type`、`graph_relation_type` 读取 taxonomy，不得写死枚举。
3. 后端保存 block 时必须填 `tenant_id`、`project_id`、`source_id`、`content_hash`。
4. 候选实体/关系状态统一使用：`candidate`、`accepted`、`reviewing`、`rejected`、`conflict`、`merged`。
5. review task 状态统一使用：`pending`、`approved`、`rejected`、`merged`、`canceled`。
6. `knowledge_source.source_type` 已扩展支持 `md/log/ini/json/csv/jpeg`。
7. 旧 Docker volume 中已有表时，基础表 SQL 会自动补列，不需要清空 volume。

## 7. 已验证命令

```bash
git diff --check
./scripts/verify-infra.sh
```

验证结论：通过。脚本完成后已自动停止 PostgreSQL、Neo4j、Redis 容器。
