# Spec Delta

## Purpose

定义不依赖外部队列中间件的有界优先内存队列行为：可配置容量、优先级调度，以及超限时的明确反压策略。

## ADDED Requirements

### Requirement: Self-implemented bounded in-memory queue
系统 MUST 基于 JDK 并发原语自行实现内存队列，且 MUST NOT 引入 RabbitMQ、Kafka 等队列中间件。

#### Scenario: Queue without middleware
- **WHEN** 系统运行并接收任务入队
- **THEN** 入队与出队由进程内自研队列完成
- **AND** 部署依赖中不包含外部消息队列中间件

### Requirement: Configurable capacity with decision basis
系统 MUST 提供可配置的有界队列容量，并文档化容量决策依据（例如线程池大小 × 队列系数，或基于水位/消费速率的配置说明）。

#### Scenario: Capacity is configurable
- **WHEN** 运维调整队列容量配置并重启或热加载生效
- **THEN** 内存队列最大长度采用新容量上限
- **AND** 项目文档说明该容量如何由线程池与队列系数推导

### Requirement: Priority-aware scheduling
系统 MUST 支持任务优先级；在容量允许时，更高优先级任务 MUST 先于更低优先级任务被 Worker 取得（同优先级可按入队顺序）。

#### Scenario: Higher priority dequeued first
- **GIVEN** 队列中已有低优先级任务
- **WHEN** 再入队一条更高优先级任务且容量未满
- **THEN** Worker 下一次取得的是更高优先级任务

### Requirement: Backpressure when full
当内存队列达到容量上限时，系统 MUST 执行至少一种明确反压策略（例如拒绝新提交并返回可识别错误，或阻塞提交方有限时间后失败），且不得无限增长内存。

#### Scenario: Reject or fail-fast under pressure
- **GIVEN** 内存队列已满
- **WHEN** 新任务在已持久化受理路径上尝试入内存队列
- **THEN** 系统按配置的反压策略拒绝或限时失败
- **AND** 内存队列长度不超过配置上限
