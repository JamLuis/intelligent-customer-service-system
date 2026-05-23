# 测试联调 Agent 任务书

## 1. 角色边界

测试联调 Agent 负责样例数据、接口测试、E2E 测试、权限验证、版本回滚验证和性能 smoke。不负责改业务实现，发现问题输出缺陷清单和复现步骤。

## 2. 必读文件

1. `03_技术方案与架构/07F_真实知识图谱构建与检索详细设计.md` §19、§20、§22
2. `05_开发计划与Agent任务/21_真实知识图谱构建与检索任务包/02_任务总表.md`
3. 接口 Agent 输出的 API 契约
4. 后端、AI、前端 Agent 交接单

## 3. 测试数据集

### KG-QA-001 样例数据集

建议新增目录：

```text
06_测试与发布/kg-fixtures/
  structured/
    device_alarm_rule.json
    vessel_device.csv
    protocol_fields.ini
    region_vessel.md
  unstructured/
    device_alarm_manual.md
    protocol_description.docx
    alarm_rule_sheet.xlsx
    ops_error_log.log
    pdf_sample.pdf
  expected/
    expected_entities.json
    expected_relations.json
    expected_paths.json
```

必须覆盖：

| 分类 | 样例 |
| --- | --- |
| geo-vessel | 地区与船舶归属 |
| vessel-device | 船舶与设备绑定 |
| device-alarm | 设备与告警规则 |
| device-protocol | 设备与协议字段 |
| ops-log | 运维日志错误模式 |

## 4. 接口与集成测试

### KG-QA-002 API 与集成测试

最小链路：

```text
POST /knowledge/sources
  -> action=parse
  -> GET /blocks
  -> action=extract
  -> GET /candidates
  -> action=build
  -> GET /graphs/assets/{graphId}
  -> action=publish
  -> GET /graphs/entities/{entityId}/neighbors
  -> POST /graphs/search/diagnosis
```

DoD：

1. 每一步都有断言。
2. sourceId/blockId/candidateId/graphId/revisionId 能串联。
3. Neo4j 中能查到发布节点和关系。
4. pgvector 查询能返回 source block。
5. 诊断检索返回 graphPaths 和 evidence。

## 5. 权限、版本、回滚测试

### KG-QA-003 权限、版本、回滚测试

必须验证：

| 场景 | 预期 |
| --- | --- |
| P001 用户查 P002 图谱 | 403 或空结果，不泄露详情 |
| draft 图谱参与诊断检索 | 不允许命中 |
| reviewing 候选发布 | 若存在冲突必须 409 |
| publish 后 active revision 切换 | 新诊断只命中新版本 |
| rollback 后 active revision 恢复 | 新诊断只命中回滚后的版本 |
| evidenceRefs 缺失的关系发布 | 禁止发布 |

## 6. 性能 smoke

### KG-QA-004 性能 smoke

目标参考 07F §20：

| 项 | 目标 |
| --- | --- |
| 1000 节点以内写图 | P95 < 10s |
| 3 跳图查询 | P95 < 800ms |
| vector top20 查询 | P95 < 500ms |
| 诊断检索 | P95 < 3s |

性能测试只做 smoke，不要求压测生产规模。

## 7. 验证命令建议

```bash
./scripts/verify-infra.sh
npm run build --workspace frontend
mvn -f backend-java/pom.xml -DskipTests package
python3 -m py_compile ai-service-python/app/main.py
npm run build --workspace smart-support-mcp-server-node
```

如果一键启动用于 E2E：

```bash
./scripts/start-all.sh restart
./scripts/start-all.sh status
```

## 8. 缺陷报告格式

| 字段 | 说明 |
| --- | --- |
| 缺陷 ID | KG-BUG-001 |
| 关联任务 | KG-BE-007 / KG-FE-003 等 |
| 严重级别 | P0/P1/P2 |
| 复现步骤 | curl 或页面路径 |
| 实际结果 | 当前表现 |
| 预期结果 | 依据文档 |
| 证据 | 日志、截图、响应 |
| 建议责任 Agent | DB/API/BE/AI/FE |

## 9. 验收通过标准

1. AC-KG-001 ~ AC-KG-010 至少 P0 场景全部通过。
2. 无 P0 缺陷。
3. P1 缺陷有明确规避或延期说明。
4. 所有测试命令结果记录在交接单。
5. 状态总表和变更记录已更新。
