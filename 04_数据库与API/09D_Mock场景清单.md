# V0.1 Mock 场景清单

## 1. 目的

本文件用于前端、后端、AI Service、MCP Server 在真实 wnx 接口未完全确定前进行联调。Mock 响应必须遵守 09_API 文档和 09B 接口规范。

## 2. 必须覆盖场景

| 场景编号 | 场景 | 关联接口 | Mock 结果 |
| --- | --- | --- | --- |
| MOCK-001 | 成功问答诊断 | API-001、API-003、API-004、API-009 | 创建会话、生成 concluded 案例、completed 执行轨迹 |
| MOCK-002 | 上下文不足 | API-001、API-003 | session.status=waiting_context，返回 missingFields |
| MOCK-003 | MCP 失败降级 | API-003、API-009 | trace.status=partial，degraded=true，mcpCalls 含 timeout |
| MOCK-004 | 知识源上传成功 | API-010、API-011 | source.status=uploaded/parsing/extracted/graph_ready 流转 |
| MOCK-005 | 文件过大 | API-010 | 返回 ICSS-KNOW-413-FILE_TOO_LARGE |
| MOCK-006 | 图谱查询成功 | API-012 | 返回 nodes、edges、sourceRefs、confidence |
| MOCK-007 | MCP 停用影响确认 | API-016 | 返回 impactRoutes 和 recentCallCount7d |
| MOCK-008 | 答案无效触发重建 | API-019 | evaluation.rebuildRequired=true，routeStatus=reviewing |
| MOCK-009 | 项目越权 | 任意项目接口 | 返回 ICSS-AUTH-403-PROJECT_DENIED |
| MOCK-010 | 字段无权限 | API-009 | R-CS 不返回 rawResponseRef |

## 3. Mock 数据约束

1. 所有 ID 使用 UUID 字符串。
2. 所有时间使用 ISO-8601。
3. 所有状态枚举必须与 04C、08、09 保持一致。
4. 真实 wnx 接入后，Mock 字段结构不得改变，只允许 Adapter 替换数据来源。
