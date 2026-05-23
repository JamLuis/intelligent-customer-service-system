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

## 8. V0.3.1 增量任务：KG-FE-005 Frozen / Budget / Hybrid 适配

**输出**：`GraphEditor.vue`、`EvidenceDrawer.vue`、`AdminGraphMaintenanceView.vue`、新增 `BudgetBanner.vue`。参考 07F §12.1 / §13.2A / §13.4。

### 8.1 Frozen 状态可视化

- 节点/关系在 `status==='frozen'` 时叠加锁形徽标（el-icon-lock），节点边框改为仑青色 (#909399) 虚线。
- 右键菜单/Action 条增 "Unfreeze"项，仅在用户拥有 `graph:unfreeze` 权限时可点击；发起 `POST /api/v1/graphs/entities/{id}/actions {action:'unfreeze'}`。
- 发布 / 回滚 / 自动归一 / 手工合并 底层跳过 frozen 项；前端遇 `ICSS-KG-409-FROZEN_NODE` toast 提示“冻结节点拒绝该操作”。

### 8.2 Budget 使用提示

- `BudgetBanner.vue` 接收 `budgetUsage{visitedNodes,visitedEdges,truncated}` props；`truncated===true` 时顶部显示黄色横幅：“本次检索遇到预算天花板（访问 {visitedNodes} 节点 / {visitedEdges} 边），结果可能不完整。调高预算 粗选 / 精选 / 调试”。
- 诊断检索页与路径查询页顶部插入。

### 8.3 Hybrid 证据标识

- `EvidenceDrawer.vue` 中 `sourceEvidence` 与 `vectorEvidence` 列表每项右侧增 荅 `el-tag` 显示 `sourceType`（paragraph / table_cell ...）与 `weight` 百分化文本。
- `hybridScore{vector,bm25,graph,recency}` 以进度条型小卡片呈现于抽屉顶部，点击可查看权重来源。

### 8.4 Embedding 版本不匹配

- 接收后端 `ICSS-KG-422-EMBEDDING_VERSION_MISMATCH` 时弹 `el-notification.error` “查询使用的 embedding 模型与库不一致，请重新导入或联系管理员”；同时在高级选项展示节点上报的 `embeddingModel/embeddingVersion`。

### DoD

- 5 个 UI 状态被 Playwright/手动验证：frozen 锁形、truncated 横幅、weight 标识、hybridScore 抽屉、embedding 版本不匹配 toast。
