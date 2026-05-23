# 归档目录说明

本目录存放任务包内**已完成且不再追加更新**的交接单，仅作为历史记录，禁止再被新的 Agent 任务引用。

| 文件 | 归档原因 | 已被替代 |
| --- | --- | --- |
| 12_数据库Agent交接单.md | KG 表结构与受保护本体已通过 T-023/T-024 在 `infra/postgres/init.sql`、`08A_建表SQL/real_kg_schema.sql`、`graph_protected_taxonomy.sql` 完整落盘并通过 `scripts/verify-infra.sh` 校验 | `04_数据库与API/08A_建表SQL/` 与 `03_技术方案与架构/07F` §1A、§9 |
| 13_接口Agent交接单.md | KG-001~009 与 KG-016~021（KG-ADMIN 类目/实体/关系注册）已统一进入 `09_API文档.md` / `09J_KG-ADMIN_API.md` | `04_数据库与API/09_API文档.md`、`09J_KG-ADMIN_API.md` |

后续若需要修改对应能力，请直接更新被替代的现行文档，并把变更登记到 `00_输入与总控/05_变更记录.md`。
