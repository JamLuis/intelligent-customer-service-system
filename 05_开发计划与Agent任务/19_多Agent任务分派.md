# 多 Agent 任务分派模板

<!-- markdownlint-disable MD060 -->

## 1. 分派原则

1. 一个 Agent 只负责一个明确模块或一组强相关任务。
2. 不允许两个 Agent 在无交接的情况下同时修改同一逻辑区域。
3. 共享规则、共享类型、数据库、接口契约变更，必须先由主控 Agent 确认。

## 2. Agent 分派表

| Agent | 任务编号 | 负责范围 | 输入文档 | 输出结果 | 禁止修改范围 | 前置依赖 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Agent-A | DEV-001 |  |  |  |  |  | 未开始 / 进行中 / 已完成 |

真实知识图谱构建与检索专项任务已拆分到：`05_开发计划与Agent任务/21_真实知识图谱构建与检索任务包/`。

| Agent | 任务编号 | 负责范围 | 输入文档 | 输出结果 | 禁止修改范围 | 前置依赖 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 数据库 Agent | KG-DB-001~003 | KG migration、taxonomy seed、pgvector 索引 | 07F、03_数据库Agent任务书 | SQL、seed、init 引用、验证记录 | Java/Python/前端代码 | 无 | 未开始 |
| 接口 Agent | KG-API-001~002 | KG API 契约、错误码、Mock 场景 | 07F、04_接口Agent任务书 | API 文档与错误码 | Controller/SQL/页面代码 | KG-DB 草案 | 未开始 |
| 后端 Java Agent | KG-BE-001~009 | Spring Boot KG API、任务状态机、Neo4j、诊断检索 | 07F、05_后端JavaAgent任务书、DB/API 交接 | backend-java 真实 KG 链路 | Python parser、前端 UI | KG-DB、KG-API | 未开始 |
| Python AI Service Agent | KG-AI-001~005 | Parser、Extractor、Normalizer、Conflict、Retrieval Plan | 07F、06_PythonAIServiceAgent任务书 | ai-service-python 解析抽取能力 | Java 发布图谱、Neo4j 写入 | KG-API 内部契约 | 未开始 |
| 前端 Agent | KG-FE-001~005 | 录入、候选预览、历史维护、证据抽屉、诊断证据 | 07F、07_前端Agent任务书、API 交接 | frontend 真实 KG 页面 | API/DB 字段定义 | KG-API、KG-BE 部分完成 | 未开始 |
| 测试 Agent | KG-QA-001~004 | 样例数据、接口/E2E/权限/性能 smoke | 07F、08_测试联调Agent任务书、各 Agent 交接 | 测试报告、缺陷清单 | 业务实现代码 | KG-BE/KG-AI/KG-FE 完成 | 未开始 |

## 3. 合并顺序

1. 先完成共享数据结构与接口契约
2. 再完成后端能力
3. 再完成前端页面与交互
4. 最后完成联调、测试和回归

## 4. 冲突处理

| 冲突类型 | 处理方式 | 责任人 |
| --- | --- | --- |
| 共享接口变更 | 先更新 API 文档和决策记录，再执行开发 | 主控 Agent |
| 共享数据库变更 | 先冻结表结构，更新数据库设计，再分派实现 | 主控 Agent |
| 同文件区域冲突 | 由主控 Agent 重新切分任务 | 项目负责人 |
