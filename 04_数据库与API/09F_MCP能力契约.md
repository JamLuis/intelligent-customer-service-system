# MCP 能力契约

## 1. V0.1 首批能力

| capabilityCode | 分类 | 风险等级 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| device.getStatus | device | L0 | enabled | 查询设备在线状态、最近心跳、基础绑定信息 |
| alarm.getRules | alarm | L0 | enabled | 查询告警规则、阈值、启用状态 |
| config.getSnapshot | config | L0 | enabled | 查询项目或设备配置快照 |
| log.searchErrors | log | L0 | enabled | 查询错误日志摘要，不返回原始敏感日志给客服 |
| statistics.rebuild | statistics | L4 | draft | V0.1 只展示能力，不允许真实执行 |

## 2. 统一能力字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| capabilityId | string | 是 | UUID |
| capabilityCode | string | 是 | 唯一编码 |
| capabilityName | string | 是 | 展示名称 |
| category | enum | 是 | device/alarm/config/log/statistics |
| riskLevel | enum | 是 | L0-L5 |
| status | enum | 是 | draft/enabled/disabled/unhealthy/retired |
| inputSchema | object | 是 | JSON Schema |
| outputSchema | object | 是 | JSON Schema |
| boundary | string | 是 | 能力边界说明 |
| lastHealthStatus | enum | 是 | unknown/healthy/unhealthy |

## 3. 停用影响确认

停用 MCP 能力前必须返回：

```json
{
  "capabilityId": "uuid",
  "capabilityCode": "device.getStatus",
  "impactRoutes": [
    {
      "routeId": "uuid",
      "routeName": "设备离线诊断路径",
      "projectId": "P001",
      "issueCategory": "device"
    }
  ],
  "recentCallCount7d": 128
}
```
