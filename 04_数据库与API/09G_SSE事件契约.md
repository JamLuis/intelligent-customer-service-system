# SSE 事件契约

## 1. 连接地址

- Method：GET
- URL：`/api/v1/diagnosis/cases/{caseId}/events`
- 鉴权：`Authorization: Bearer <token>` + 项目权限

## 2. 事件格式

```text
event: diagnosis.step
data: {"caseId":"uuid","traceId":"uuid","stepType":"mcp","status":"running","message":"正在查询设备状态"}
```

## 3. 事件类型

| event | 说明 | data 关键字段 |
| --- | --- | --- |
| diagnosis.started | 诊断启动 | caseId、traceId、status |
| diagnosis.step | 阶段进度 | stepType、stepName、status、message |
| diagnosis.mcp_call | MCP 调用状态 | capabilityCode、status、durationMs |
| diagnosis.degraded | 降级提示 | degraded、failureReason |
| diagnosis.completed | 诊断完成 | caseId、traceId、caseStatus |
| diagnosis.failed | 诊断失败 | caseId、traceId、errorCode、message |

## 4. 重连策略

- 前端断线后按浏览器 EventSource 默认策略重连。
- 后端需支持 `Last-Event-ID`，V0.1 可先按 caseId 返回最新状态快照。
