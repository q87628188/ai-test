# Design

## Context

- 动机与范围见 `proposal.md` - Why / What Changes。
- 现有工程已具备：H2 真相源、有界优先队列、Worker、退避重试、`StartupRecovery`、优雅停机与指标；场景与 API 仍为 `DOC_INGEST`。
- 考核约束不变：禁止外部 MQ；任务可模拟；状态表与结果表分离。

## Goals / Non-Goals

**Goals:**

- 将场景与契约切到「单条风险访问事件」异步清洗/特征化，叙事对齐 Logstash→队列→Spark，实现上仍为进程内引擎。
- 钉死消费语义：用持久化状态区分已消费/未消费；宕机丢内存后重启可恢复未完成工作。
- 尽量复用现有模块，以契约与模拟器替换为主。

**Non-Goals:**

- 不接入真实 Logstash / Kafka / Spark。
- 不做跨事件窗口攒批、不在 API 暴露 `windowStart`/`windowEnd`。
- 不追求 exactly-once；接受 at-least-once + `businessKey` 幂等。
- 不重构队列/线程池算法（除非测试证明语义缺口）。

## Decisions

### D1: 生产叙事映射

| 生产组件 | 本作业对应 |
|----------|------------|
| Logstash 推送一条日志 | `POST /api/tasks` 提交一条 event |
| 外部消息队列 | 自研有界优先内存队列 + H2 持久化 |
| Spark 清洗聚合 | Worker 内 sleep/本地计算模拟清洗与特征摘要 |

**备选**：接口收数组再窗聚合 — 拒绝（用户已定单条 event；窗缓冲超出考核必要复杂度）。

### D2: 提交契约

- 调用方显式传 `businessKey`（UK 幂等），服务端不派生。
- 业务内容字段：`userId`、`eventTime`、`deviceId`、`clientIp`、`targetSystem`，可选 `action`。
- 调度/演示字段：`priority`；可选 `failProbability`、`fatal`。
- **禁止**：`windowStart`/`windowEnd`；event 数组。
- **BREAKING** 相对当前 `docId`/`fileName` 契约。

### D3: 可靠性与「消费位点」

- **成功接收**：事务写入 `task_status` 为 `QUEUED` 后返回 `taskId`；内存 `offer` 失败不得否定已接收（已有补偿入队路径保留）。
- **已消费**：`SUCCESS`（且 `task_result` 同行事务写入）；终态 `FAILED`/`DEAD` 视为已结案，重启不加载。
- **未消费/未完成**：`QUEUED`；`RETRY_WAIT`（到期转 `QUEUED`）；崩溃时遗留的 `RUNNING` → 纠正为 `QUEUED` 后加载。
- **启动恢复**：`StartupRecovery` 扫描上述未完成集合，按优先级 `offer` 回内存队列；已终态跳过。
- **语义**：at-least-once；同 `businessKey` 幂等防止重复业务实例。

**备选**：用独立 offset 表 — 拒绝（状态枚举已够用，少一层）。

### D4: 处理与结果

- 任务类型：`RISK_EVENT_INGEST`；执行特征标注为 CPU/混合（清洗规则 + 短 IO 写结果）。
- 模拟器按事件字段做规范化/脏数据判定与特征摘要（可用 `failProbability`/`fatal` 注入）。
- 结果摘要示例字段：`normalized`、`riskTags` 或等价、`durationMs`；原 event 留在 `payload_json` 可追溯。

### D5: 容量与反压（沿用）

- `queueCapacity = workerThreads × queueFactor`（可配置覆盖）。
- 内存满：快速失败（如 429）；已落库任务不丢，由补偿/恢复入队。

## Risks / Trade-offs

- [RUNNING 崩溃重复执行] → 文档声明 at-least-once；结果按 `task_id` 唯一；幂等键防重复任务行。
- [场景换皮遗漏测试/文档] → tasks 明确改 API 测试、README、tradeoffs、ai-dialogue。
- [与归档 DOC 叙事并存困惑] → README 以风险事件为主叙事；归档 change 保留历史即可。

## Migration Plan

1. 更新 DTO/模拟器/枚举与 API 测试。
2. 对齐 `StartupRecovery` 场景测试（已消费不加载、未消费加载、RUNNING 纠正）。
3. 更新文档后 `mvn test`；本地重启演练一条未完成任务。
4. 回滚：恢复旧 DTO/模拟器提交（无生产数据迁移）。
