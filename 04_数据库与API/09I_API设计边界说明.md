# API 设计边界说明

## 1. 本阶段只定义契约

本阶段只输出接口路径、参数、响应、错误码、权限、分页、Mock 和字段映射，不写 Controller、Service、Mapper，不修改数据库表结构。

## 2. V0.1 不做真实生产写操作

以下接口不进入 V0.1 实现范围：

- executeApprovedAction
- 真实 statistics.rebuild 执行
- 任何直接修改 wnx-serve 生产配置、告警规则、设备绑定的数据写接口

## 3. Mock + Adapter 原则

- 前端只面向本系统 `/api/v1/*` 契约。
- Java Backend 负责统一 DTO、鉴权、审计。
- Node MCP Server 负责 capability 适配。
- 真实 wnx 接口接入后，不改变前端契约。

## 4. 后续变更规则

接口字段、错误码、状态枚举、权限标识变更，必须同步更新：

1. 09_API文档.md
2. 09B_接口规范.md
3. 09C_错误码登记表.md
4. 09E_接口字段映射表.md
5. 00_输入与总控/05_变更记录.md
