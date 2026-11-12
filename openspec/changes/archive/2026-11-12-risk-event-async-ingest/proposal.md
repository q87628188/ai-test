# Proposal

## Why

考核要求基于真实项目经验建模异步任务全链路。当前工程按「文档入库」叙事实现，与简历中的风险引擎数据采集（Logstash 推送 → 消息队列 → Spark 清洗聚合）不一致。需要把场景切换为「单条风险访问事件异步清洗/特征化」，并明确宕机后如何区分已消费与未消费、未消费如何重新加载进内存队列。

## What Changes

- **BREAKING**：提交契约从文档入库（`docId`/`fileName`）改为单条风险事件业务内容（谁、何时、设备、IP、目标系统等）；任务类型改为 `RISK_EVENT_INGEST`。
- 每次提交只接受 **一条 event**（非数组）；接口仅含调用方显式 `businessKey` + 业务内容 + 调度字段（`priority` 等）；**不接受** `windowStart`/`windowEnd`。
- 保留并文档化可靠性模型：H2 为真相源，内存有界优先队列可丢；「成功接收」= 状态表持久化；「已消费」= 终态（尤其 `SUCCESS` + 结果行）；未完成状态重启后重新加载进内存队列。
- Worker 模拟改为事件清洗/特征抽取（sleep/本地计算），不对接真实 Logstash/Kafka/Spark。
- 更新 README、权衡文档与 AI 对话留存，使场景与考核叙事一致。
- 复用既有引擎能力：有界优先队列、反压、幂等、退避重试、优雅停机、指标；不以引入外部消息中间件为前提。

## Capabilities

### New Capabilities

- （无）不新增能力边界；在既有四条能力上改场景与恢复语义。

### Modified Capabilities

- `task-submission`: 场景与提交契约改为单条风险事件；显式 `businessKey` 幂等；禁止窗口边界字段与事件数组。
- `task-execution`: Worker 模拟改为风险事件清洗/特征化；失败分类与重试语义保持，结果摘要字段更换。
- `task-observability`: 强化宕机/重启恢复：可区分已消费与未消费；未消费（含崩溃时 `RUNNING`）必须重新加载进内存队列；已消费不得重复调度。

## Impact

- API DTO、`SubmitCommand`、payload/result JSON、模拟器类名与文档叙事。
- 启动恢复 / 优雅停机逻辑以规格钉死「消费位点」语义（至少一次 + 幂等），代码路径大体已存在，按需对齐测试与文案。
- 依赖与表结构原则不变：Java 17+、Spring、H2 文件模式、状态表与结果表分离、自研 JDK 队列。
