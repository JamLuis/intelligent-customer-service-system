# 示例本体配置（不会被 init 自动加载）

本目录存放**示例**本体配置 SQL，仅供用户参考。  
本系统是通用知识图谱平台，**默认不内置任何业务领域本体**；下列示例展示一个可能的航运/设备/告警租户配置如何通过 SQL 注册。

| 文件 | 说明 | 适用场景 |
| --- | --- | --- |
| `graph_taxonomy_example_marine.sql` | 8 个分类 + 28 个实体类型 + 22 个关系类型的航运/设备/告警示例 | 仅参考；生产环境请通过 KG-ADMIN-* 管理 API 注册本租户的本体 |

## 使用方式

不推荐生产使用。如需快速体验：

```bash
docker compose -f infra/docker-compose.yml exec -T postgres \
  psql -U smart_support -d smart_support \
  -f /schema/08A/examples/graph_taxonomy_example_marine.sql
```

执行后，可通过 `GET /api/v1/graphs/taxonomy` 看到该示例本体；如需清理，请逐条 `DELETE` 或 `UPDATE status='disabled'`，不要重启容器，否则 `init.sql` 不会自动清除示例数据。

## 重要提醒

- 不要把任何示例数据当作系统内置或推荐本体。
- 平台开发与测试应**始终基于空 taxonomy**起步，验证“租户可注册任意本体”。
- 系统强制注册的“保护类型”仅 `SourceBlock` / `Document` / `Section` 与 `HAS_EVIDENCE` / `HAS_SECTION` / `IN_SECTION`，由 `graph_protected_taxonomy.sql` 维护，禁止删除。
