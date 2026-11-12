# 容量 / 反压 / 不丢失权衡

场景：风险引擎「单条访问事件」异步清洗/特征化（`RISK_EVENT_INGEST`）。生产叙事为 Logstash → MQ → Spark；本作业用进程内有界优先队列 + Worker 模拟替代外部中间件与集群。

执行特征：**CPU/混合**（清洗规则与特征抽取以本地计算为主，短 sleep 模拟 IO）。

## 1. 容量决策

默认：

```text
queueCapacity = workerThreads × queueFactor
             = 4 × 20
             = 80
```

配置项：`task.engine.worker-threads`、`task.engine.queue-factor`；若 `task.engine.queue-capacity > 0` 则直接覆盖公式结果。

系数可按观测水位与平均处理时延再调；本项目用静态配置，便于讲解与复现。

## 2. 反压策略

选型：**对新业务键 fail-fast（HTTP 429 `QUEUE_FULL`）**。

流程：

1. 先查幂等；命中则返回原 `taskId`。
2. 若 `queue.size() >= capacity`，**不写 DB**，直接 429 —— 反压的是新流量。
3. 否则事务落库 `QUEUED`，事务提交后再 `offer` 入内存队列。
4. 若落库成功但 `offer` 因竞态失败：任务仍在 DB，由 `EnqueueCompensator` 补入队 —— **已受理不丢**。

不采用阻塞提交：会拖垮 HTTP 线程，超时后调用方无法判断是否受理。

## 3. 「不丢失」与消费位点

| 说法 | 含义 |
|------|------|
| 成功接收 | `task_status` 行已持久化（H2 文件模式） |
| 内存队列 | 仅调度加速，宕机可丢；不是真相源 |
| 已消费 | 终态 `SUCCESS`（含 `task_result`）或 `FAILED`/`DEAD`；重启不加载 |
| 未消费 | `QUEUED`；到期 `RETRY_WAIT`；崩溃时 `RUNNING`→纠正为 `QUEUED` 后加载 |
| 崩溃语义 | **至少一次**；用显式 `businessKey` 幂等避免重复业务实例 |

不引入独立 offset 表：状态枚举已够表达消费进度。
