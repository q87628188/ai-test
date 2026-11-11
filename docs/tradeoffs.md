# 容量 / 反压 / 不丢失权衡

场景：企业智能问答「文档入库」异步任务（`DOC_INGEST`），IO/混合型，处理用 sleep + 本地计算模拟。

## 1. 容量决策

默认：

```text
queueCapacity = workerThreads × queueFactor
             = 4 × 20
             = 80
```

配置项：`task.engine.worker-threads`、`task.engine.queue-factor`；若 `task.engine.queue-capacity > 0` 则直接覆盖公式结果。

为何 IO 型用较大系数：Worker 多数时间在模拟解析/向量化等待，队列需要缓冲突发上传，避免瞬时反压过早触发。系数可按观测到的水位与平均处理时延再调；本项目用静态配置，便于讲解与复现。

## 2. 反压策略

选型：**对新业务键 fail-fast（HTTP 429 `QUEUE_FULL`）**。

流程：

1. 先查幂等；命中则返回原 `taskId`。
2. 若 `queue.size() >= capacity`，**不写 DB**，直接 429 —— 反压的是新流量。
3. 否则事务落库 `QUEUED`，事务提交后再 `offer` 入内存队列。
4. 若落库成功但 `offer` 因竞态失败：任务仍在 DB，由 `EnqueueCompensator` 补入队 —— **已受理不丢**。

不采用阻塞提交：会拖垮 HTTP 线程，超时后调用方无法判断是否受理。

## 3. 「不丢失」边界

| 说法 | 含义 |
|------|------|
| 成功接收 | `task_status` 行已持久化（H2 文件模式） |
| 内存队列 | 仅调度加速，不是真相源 |
| 崩溃窗口 | 至多「至少一次」执行；`RUNNING` 在启动恢复时改回 `QUEUED` 再调度 |
| 非恰好一次 | 不引入分布式锁/去重中间件；用状态机跳过终态重复执行 |

优雅停机：停收 → drain 内存队列（DB 仍为可调度状态）→ `RUNNING→QUEUED` → 限时停 Worker。重启：`StartupRecovery` 再 offer。

## 4. 重试与队列压力

可重试失败进入 `RETRY_WAIT` + `nextRunAt`，**不占用有界队列槽位**；到期由 `RetryScheduler` 改回 `QUEUED` 再入队。超过 `max-retries` → `DEAD`。
