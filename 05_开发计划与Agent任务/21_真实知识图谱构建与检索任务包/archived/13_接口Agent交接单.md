# 接口 Agent 交接单

## 1. 交接信息

- 交出 Agent：Copilot 接口执行
- 接手 Agent：后端 Java Agent、前端 Agent、测试 Agent
- 交接日期：2026-05-23
- 对应任务编号：KG-API-001、KG-API-002

## 2. 当前完成情况

### 已完成

1. 新增真实知识图谱 API 补充契约：`04_数据库与API/09J_真实知识图谱API补充契约.md`。
2. 更新主 API 文档 `04_数据库与API/09_API文档.md`，加入 09J 引用和真实 KG 增量接口说明。
3. 更新错误码登记表 `04_数据库与API/09C_错误码登记表.md`，新增 KG 领域错误码。
4. 错误码格式已与项目规则保持一致：`ICSS-{DOMAIN}-{HTTP_STATUS}-{REASON}`。

### 未完成

1. 未实现 Java Controller/Service。
2. 未实现前端 API client。
3. 未实现 Mock 场景代码。
4. 未新增自动化接口测试。

## 3. 新增 API

| 编号 | 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- | --- |
| KG-001 | GET | `/api/v1/graphs/taxonomy` | graph:view | 图谱分类、本体、关系类型配置 |
| KG-002 | POST | `/api/v1/knowledge/sources` | knowledge:upload | 创建知识源，必须带 graphCategoryId |
| KG-003 | GET | `/api/v1/knowledge/sources/{sourceId}/blocks` | knowledge:view | 查看解析块 |
| KG-004 | GET | `/api/v1/knowledge/sources/{sourceId}/candidates` | knowledge:view | 查看候选实体/关系 |
| KG-005 | POST | `/api/v1/knowledge/sources/{sourceId}/actions` | knowledge:retry / graph:publish | parse/extract/build/publish/retry |
| KG-006 | GET | `/api/v1/graphs/assets` | graph:view | 分类、实体、关系筛选图谱资产 |
| KG-007 | GET | `/api/v1/graphs/assets/{graphId}` | graph:view | 图谱详情，包含 nodes/edges/sourceRefs/revisions |
| KG-008 | PATCH | `/api/v1/graphs/assets/{graphId}/draft` | graph:edit | 保存草稿节点关系 |
| KG-009 | POST | `/api/v1/graphs/assets/{graphId}/versions/actions` | graph:publish / graph:rollback | publish/rollback/deprecate |
| KG-010 | POST | `/api/v1/graphs/search/diagnosis` | graph:view | 诊断检索子图 |
| KG-011 | GET | `/api/v1/graphs/entities/{entityId}/neighbors` | graph:view | 实体邻居 |
| KG-012 | GET | `/api/v1/graphs/paths` | graph:view | source-target 路径查询 |
| KG-013 | GET | `/api/v1/graphs/evidence` | graph:view | 证据块溯源 |
| KG-014 | GET | `/api/v1/graphs/review-tasks` | graph:review | 低置信度/冲突复核任务 |
| KG-015 | PATCH | `/api/v1/graphs/review-tasks/{taskId}` | graph:review | 复核通过/驳回/合并 |

## 4. 新增错误码

新增 KG 领域错误码覆盖：

1. 图谱分类无效。
2. sourceType 不支持。
3. 文件格式或大小非法。
4. 图谱检索参数非法。
5. 图谱查看/发布权限不足。
6. source/graph/review task 不存在。
7. 未处理冲突禁止发布。
8. revision 并发冲突。
9. parse/extract/schema 校验失败。
10. Neo4j 写入失败。
11. pgvector 查询失败。
12. Python AI Service 不可用。

## 5. 下游注意事项

1. 后端 Java Agent 必须优先按 09J 的字段定义 DTO，不得继续扩展旧 Mock JSON。
2. 前端 Agent 的 taxonomy、source、candidate、review、evidence、diagnosis search API client 应全部引用 09J。
3. `KG-010 searchDiagnosisGraph` 返回的 `vectorEvidence` 只能展示为语义证据，不得当成关系路径。
4. `KG-008 updateGraphDraft` 只保存草稿，不发布。
5. `KG-009 runGraphVersionAction` 发布前必须检查未处理 review/conflict。
6. 诊断检索只能使用 `published + activeRevision` 图谱。

## 6. 已验证命令

```bash
git diff --check
```

验证结论：Markdown 补丁无空白错误。尚未做 API 实现测试。
