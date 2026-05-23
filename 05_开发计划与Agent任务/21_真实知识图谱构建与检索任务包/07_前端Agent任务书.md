# 前端 Agent 任务书

## 1. 角色边界

前端 Agent 负责 Vue3 管理端页面、组件、API client 和交互状态。不得自定义业务字段、不得写死图谱分类/实体/关系类型、不得直接访问 Neo4j 或拼 Cypher。

## 2. 必读文件

1. `03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md` §17
2. 接口 Agent 输出的 KG API 契约
3. `frontend/src/api.ts`
4. `frontend/src/views/admin/AdminKnowledgeIngestView.vue`
5. `frontend/src/views/admin/AdminGraphMaintenanceView.vue`
6. `frontend/src/components/GraphEditor.vue`

## 3. 页面范围

| 页面 | 路由 | 任务 |
| --- | --- | --- |
| 知识录入与预览 | `/admin/knowledge/ingest` | 分类选择、结构化文本/文件上传、任务状态、候选预览、草稿图预览 |
| 历史图谱维护 | `/admin/knowledge/graphs` | 分类/实体/关系筛选、版本切换、关系图编辑、证据抽屉、发布/回滚 |
| 复核任务 | 可先合并在维护页 | 展示低置信度/冲突候选，支持通过/驳回/合并 |

## 4. 任务拆分

### KG-FE-001 API client 扩展

- 输出：`frontend/src/api.ts`。
- 需新增方法：
  - `getGraphTaxonomy`
  - `createKnowledgeSource`
  - `getKnowledgeSourceBlocks`
  - `getKnowledgeSourceCandidates`
  - `runKnowledgeSourceAction`
  - `getGraphAssets`
  - `getGraphAssetDetail`
  - `updateGraphDraft`
  - `runGraphVersionAction`
  - `searchDiagnosisGraph`
  - `getGraphNeighbors`
  - `getGraphPaths`
  - `getGraphEvidence`
  - `getGraphReviewTasks`
  - `updateGraphReviewTask`
- DoD：TypeScript 类型齐全，headers 与 runtime projectId/token 兼容现有模式。

### KG-FE-002 录入页任务时间线与候选预览

- 输出：`AdminKnowledgeIngestView.vue` 及必要组件。
- 组件建议：
  - `GraphCategorySelector`
  - `KnowledgeSourceForm`
  - `IngestionTaskTimeline`
  - `CandidateReviewTable`
- DoD：
  - 选择 graphCategoryId 后才能提交。
  - source 创建后显示 parse/extract/build/publish 状态。
  - blocks 和 candidates 能展示。
  - 低置信度/冲突有明确状态标签。

### KG-FE-003 历史图谱维护版本与证据抽屉

- 输出：`AdminGraphMaintenanceView.vue`、`EvidenceDrawer.vue`。
- DoD：
  - 支持 graphCategoryId/entityType/relationType/status 筛选。
  - 支持 revision 切换。
  - 点击节点/关系能查看 source/block/page/section/rawText 摘要。
  - 发布/回滚按钮只在权限允许和状态允许时显示。

### KG-FE-004 GraphEditor 真实数据适配

- 输出：`GraphEditor.vue`。
- DoD：
  - 节点颜色/图标按 entityType。
  - 边样式按 relationType/status/confidence。
  - draft/reviewing/published/deprecated/rolled_back 有不同标签。
  - 删除/新增/重建关系只保存草稿，不直接发布。

## 5. UI 状态要求

| 状态 | 页面表现 |
| --- | --- |
| parsing/extracting/building | 时间线 loading |
| graph_ready | 可以预览草稿图 |
| reviewing | 显示复核任务数量 |
| published | 显示 active revision |
| failed | 显示失败原因和 retry 入口 |
| conflict | 候选表中高亮，禁止发布 |

## 6. 禁止事项

1. 不要写死 taxonomy 下拉选项。
2. 不要把 `mock-token` 以外的新鉴权方式写死。
3. 不要让前端直接传 Neo4j Cypher。
4. 不要在页面隐藏 confidence/sourceRefs/evidenceRefs。
5. 不要在保存草稿后自动发布。

## 7. 验证命令

```bash
npm run build --workspace frontend
```

如启动验证：

```bash
./scripts/start-all.sh start
```

## 8. 交接给测试

必须说明：

1. 哪些页面已接真实 API。
2. 哪些按钮由于后端未完成暂时禁用。
3. 每个页面的主要空状态、加载态、失败态。
4. 如何构造一条从录入到预览的前端测试路径。
