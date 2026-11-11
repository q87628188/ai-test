# Spec Delta

## Purpose

定义 Worker 从队列取任务、模拟耗时处理、失败分类、限次指数退避重试与死信终态，保证重试不会无限抬升队列压力。

## ADDED Requirements

### Requirement: Worker pull and simulated processing
系统 MUST 由 Worker 线程从优先队列拉取任务，并用 sleep 或本地计算模拟文档解析/向量化耗时，不得依赖真实外部模型服务即可完成演示。

#### Scenario: Simulated ingest completes successfully
- **GIVEN** 队列中存在待处理文档入库任务
- **WHEN** Worker 拉取并执行模拟处理且未触发失败注入
- **THEN** 任务最终变为成功状态
- **AND** 结果表中可查询到入库结果摘要

### Requirement: Classified failure handling
系统 MUST 区分可重试业务失败与系统异常（可恢复/不可恢复），并对不可恢复失败直接进入失败终态而不进入无限重试。

#### Scenario: Unrecoverable failure fails fast
- **WHEN** 处理过程判定为不可恢复系统异常
- **THEN** 任务进入失败终态并记录原因
- **AND** 不再安排后续重试

#### Scenario: Recoverable failure is retryable
- **WHEN** 处理过程触发可重试业务失败或可恢复系统异常
- **THEN** 系统按重试策略安排下一次执行
- **AND** 状态可查询到失败原因与当前重试次数

### Requirement: Limited exponential backoff retry
系统 MUST 以「最大重试次数 + 指数退避」执行重试；达到上限后 MUST 进入死信或失败终态并记录原因。

#### Scenario: Exhausted retries go to dead letter or failed
- **GIVEN** 任务已达最大重试次数仍失败
- **WHEN** 最后一次失败被记录
- **THEN** 任务进入死信或失败终态
- **AND** 失败原因与重试次数可查询

### Requirement: Retries must not unboundedly pressure the queue
重试调度 MUST NOT 因无限重投而导致内存队列无界膨胀；退避等待中的任务不得长期占用有界队列槽位。

#### Scenario: Backoff wait outside bounded queue slot
- **GIVEN** 任务需要退避后重试
- **WHEN** 进入退避等待
- **THEN** 该任务不持续占用有界内存队列的有效容量槽位
- **AND** 到期后才重新进入可调度路径
