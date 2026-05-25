# MD 知识抽取评测与拟合报告

## 评测范围

本轮验证 Markdown 文档从解析、候选抽取、候选落库、图谱草稿生成到 sourceId 绑定预览的完整链路。评测方式采用人工金标、Python 直接抽取、系统端到端入图三路对比。

## 金标样本

| 样本 | 用途 | 结构 | 金标实体 | 金标关系 |
| --- | --- | --- | ---: | ---: |
| finance | 拟合样本 | 标题 + 评测对象 + 模型选择 + 运行策略 | 9 | 6 |
| ops | holdout 样本 | 标题 + 导入流程 + 质量要求 + 性能要求 | 11 | 5 |

## 发现的问题

1. 端到端重复导入同一 Markdown 时，`knowledge_block.block_id` 发生主键冲突。根因是 `blockId` 只由块内容生成，没有包含 `sourceId`。
2. 仅依赖 LLM 不稳定。direct LLM 在 finance 样本上召回很好，但耗时约 53 秒；系统端在模型配置降级或不可用时会退化成规则抽取。
3. 规则基线缺少 Markdown 常见说明句式，尤其是“X 包含/覆盖 A、B、C”和“X 适合/用于 Y”，导致实体关系为空或很少。

## 拟合改动

1. `blockId` 改为 source-scoped，避免相同内容跨 source 重导入撞主键。
2. 新增 Markdown 通用规则抽取：
   - `X 包含/覆盖 A、B、C` -> `X HAS_COMPONENT A/B/C`
   - `X 适合/用于 Y` -> `X HAS_VALUE Y`
3. 新增可重复评测脚本 `scripts/md_kg_eval.py`，支持 direct/system/both 模式、sourceId 绑定断言、召回率与耗时统计。
4. 新增回归测试覆盖 source-scoped blockId、枚举组成关系和用途取值关系。

## 评测结果

| 样本 | 路径 | Blocks | 实体 | 语义实体 | 关系 | 实体召回 | 关系召回 | sourceId 图谱数 | 总耗时 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| finance | system fitted | 7 | 24 | 12 | 27 | 100% | 100% | 1 | 7.855s |
| ops | direct + LLM | 7 | 30 | 18 | 31 | 100% | 80% | - | extract 53.472s |
| ops | system fitted | 7 | 24 | 12 | 26 | 81.82% | 80% | 1 | 7.519s |

## 结论

当前 MD 链路已达到 V0.3.1 小文档可用标准：系统端不依赖 LLM 也能稳定产出结构证据图和核心语义关系；启用 LLM 可提升直接抽取召回，但耗时和噪声都更高，应保留为 bounded reviewing 候选，而不是同步强依赖。

下一阶段建议把中大文档改为异步任务：前台先返回结构图与规则候选，后台按 top-N 高价值块执行 LLM OpenIE，并把 embedding 回填拆成独立任务。