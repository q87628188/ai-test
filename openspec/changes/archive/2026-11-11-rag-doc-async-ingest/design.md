# Design

## Context

- 考核约束见考核题：Java 17+、Spring、H2 文件持久化、状态表与结果表分离、禁止队列中间件、自研 JDK 并发队列、任务可模拟、需 git 与 AI 对话留存。见 proposal.md - Why。
- 仓库当前为空壳（仅 OpenSpec / Cursor 配置），本设计按 greenfield 落地。
- 简历匹配场景比选：
  - **入选：企业智能问答系统 · 文档解析/切片/向量化入库** — 异步链路完整（上传后后台处理）、IO/混合型、失败可建模（解析失败/超时）、优先级自然（紧急制度文档 vs 批量资料）、与 AI 应用方向强相关。
  - 备选未入选：电商数据分析 Agent 以同步/流式交互为主；数字藏品订单更偏交易中间件；SIM 数据同步虽异步但与 AI 考核叙事弱。

### 场景建模（需求锚定）

| 维度 | 定义 |
|------|------|
| 任务类型 | `DOC_INGEST`：模拟 MinerU 解析 → 切片 → 向量化入库 |
| 输入 | `businessKey`（如 `doc:{docId}:{version}`）、`docId`、`fileName`、`priority`、可选 `failProbability` |
| 输出 | 结果摘要：模拟 chunk 数、向量条数、耗时；失败则原因与重试次数 |
| 执行特征 | **IO/混合型**（解析与向量化以等待/本地计算模拟）；线程池按 IO 友好配置 |
| 失败概率 | 可配置默认如 10%–20% 注入可重试失败；另支持强制不可恢复失败标记 |

## Goals / Non-Goals

**Goals:**

- 交付「接收 → 有界优先队列 → Worker → 结果可追溯」闭环，满足入队不丢失与反压/重试/幂等边界可讲解。
- 技术选型与表结构可直接指导实现与测试。
- 文档化容量、反压、「不丢失」权衡，便于面试深挖。

**Non-Goals:**

- 不真实对接 MinerU / Embedding / ES / PgVector。
- 不引入分布式协调、多实例抢占（单机进程内正确性优先）。
- 不实现完整 RAG 问答链路，仅入库任务引擎。

## Decisions

### D1: 可靠性模型 =「DB 为真相，内存队列为加速调度」

- **选择**：提交时先写入任务状态表（`QUEUED`）并提交事务，再尝试入内存优先队列；崩溃恢复时从 DB 扫描可调度状态重建内存队列。
- **「成功接收」边界**：DB 事务成功即对调用方返回 `taskId`；若随后内存入队因反压失败，任务保持 `QUEUED`/`ACCEPTED`，由恢复扫描或短暂重试入队，**不得丢任务**。
- **备选**：纯内存队列 — 无法满足「进入队列后不丢失」。先入内存再异步落库 — 崩溃窗口丢任务，不采用。

### D2: 有界优先队列实现

- **选择**：`PriorityBlockingQueue` + 信号量/`AtomicInteger` 做有界容量；或 `Lock` + `Condition` + 优先堆自定义。推荐 **有界包装的 `PriorityBlockingQueue`**：比较器按 `priority` 降序、同优先级按 `enqueueSeq` 升序。
- **容量公式（静态可配置）**：`queueCapacity = workerThreads × queueFactor`（默认 factor=20，可配置覆盖）。文档说明也可改为按水位观测后手工调参。
- **反压策略（至少一种，默认采用）**：**快速失败拒绝** — 内存队列满时提交路径返回 `429/QUEUE_FULL`（若 DB 已写入，则标记待入队并由后台 `enqueueRetry` 消化，或对「仅内存满」的重复提交返回已受理的 `taskId`）。对外语义：新流量被反压；已受理任务不丢。
- **备选**：阻塞提交 — 易拖垮 HTTP 线程；调用方超时后难界定是否受理。本方案以失败拒绝 + 持久化受理为主。

### D3: 线程池与并发模型

- **选择**：独立 `ThreadPoolExecutor`（核心/最大线程可配，拒绝策略与队列解耦：任务不走 executor 自带无界队列，而是自研有界优先队列 + 固定数量 Worker 循环 `take`）。
- **原因**：IO/混合型用有限 Worker 拉取，避免 `CallerRuns` 与 HTTP 线程耦合；优先级由自研队列保证，而非 `ThreadPoolExecutor` 默认 FIFO。

### D4: 幂等

- **选择**：`business_key` 唯一约束；冲突时查询已有行返回同一 `taskId`（幂等成功）。若已终态失败且允许重做，可作为后续扩展（本期默认不自动重建）。
- **备选**：直接拒绝重复 — 也可满足题面，但同键返回同 ID 更利于前端轮询。

### D5: 失败分类与重试

| 类型 | 示例（模拟） | 行为 |
|------|--------------|------|
| 可重试业务失败 | 随机失败、模拟解析超时 | 指数退避：`delay = baseMs * 2^attempt`，上限封顶；`attempt < maxRetries` |
| 可恢复系统异常 | 短暂模拟 DB 抖动 | 同上 |
| 不可恢复 | 非法任务载荷、明确 `fatal=true` | 直接 `FAILED`，不重试 |

- **退避不占槽位**：任务出队后处理；需重试时写回 DB 为 `RETRY_WAIT`，用调度线程/延迟队列在到期后重新入有界队列，避免 sleep 占着 Worker 或占满有界队列。
- **死信**：超过 `maxRetries` → `DEAD`/`FAILED`，原因入库；可选内存死信计数指标。

### D6: 持久化与表设计（H2 文件模式）

- **任务状态表 `task_status`**：`id`、`business_key`(UK)、`priority`、`status`、`retry_count`、`next_run_at`、`fail_reason`、`enqueued_at`、`started_at`、`finished_at`、`payload_json`、版本号/乐观锁可选。
- **结果表 `task_result`**：`task_id`(UK/FK)、`result_json`、`created_at`。分离便于结果清理与状态高频更新。
- **技术**：Spring Boot 3 + Spring Data JPA + H2 `jdbc:h2:file:./data/tasks;AUTO_SERVER=TRUE`（或项目约定路径）；`schema.sql`/`ddl` 随项目启动执行。

### D7: API 表面（示意）

- `POST /api/tasks` 提交 → `taskId`
- `GET /api/tasks/{id}` 状态
- `GET /api/tasks/{id}/result` 结果
- `GET /api/tasks/stats` 按状态统计
- `GET /api/metrics` 队列/线程/计数/时延
- 优雅停机挂钩 Spring `@PreDestroy` / 生命周期与 JVM shutdown hook

### D8: 分层

- `api`：Controller / DTO
- `application`：提交、查询、停机编排
- `domain`：任务状态机、优先级、重试策略
- `infrastructure`：JPA、自研队列、Worker、Metrics 收集

## Risks / Trade-offs

- [单机内存队列] → 多实例不共享；题面允许且禁止中间件，接受单机边界，文档写明。
- [DB 先写再入队的短暂「已持久化但未在内存」窗口] → 启动恢复扫描 + 周期性 enqueue 补偿；指标暴露 `pending_enqueue`。
- [「不丢失」≠「恰好一次执行」] → 崩溃可能造成至少一次；用 `business_key` 与状态机（仅 `QUEUED/RETRY_WAIT` 可调度，`RUNNING` 超时回收）控制重复执行窗口；文档明确权衡。
- [反压拒绝新流量 vs 用户体验] → 返回明确错误码；已受理任务继续消化。
- [PriorityBlockingQueue 无界原生行为] → 必须外层容量门闩，测试验证超限不增长。

## Migration Plan

1. 初始化 Spring Boot 工程与 H2 文件库目录。
2. 落地表结构与提交/查询 API。
3. 落地有界优先队列、Worker、重试与指标。
4. 补充优雅停机与恢复测试。
5. 撰写 `docs/tradeoffs.md`（容量/反压/不丢失），保留 AI 对话与分步 git commit。
6. 回滚：演示项目无生产数据迁移；删除 H2 文件即可重置。

## Open Questions

- 同业务键在 `FAILED/DEAD` 后是否允许「重新入库」生成新任务：默认否；若产品需要可后续加显式 `force` 参数（不改变本期验收主路径）。
