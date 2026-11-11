# task-observability Specification

## Purpose

定义按任务标识查询状态与结果、按状态统计积压，以及运行指标暴露与优雅停机后的可恢复、可追溯行为。

## Requirements

### Requirement: Query status and result by task id
系统 MUST 支持按 `taskId` 查询状态（至少包含排队中、处理中、成功、失败），失败时包含失败原因与重试次数；完成后 MUST 可获取结果。

#### Scenario: Query in-flight task
- **WHEN** 调用方用已接受的 `taskId` 查询
- **THEN** 返回当前状态
- **AND** 若失败则包含失败原因与重试次数

#### Scenario: Query completed result
- **GIVEN** 任务已成功完成
- **WHEN** 调用方查询结果
- **THEN** 返回结果摘要且与该 `taskId` 关联

### Requirement: Status statistics
系统 MUST 支持按状态统计（至少积压、处理中、失败）。

#### Scenario: Backlog and failure counts
- **WHEN** 调用统计接口
- **THEN** 返回排队积压数、处理中数量与失败数量

### Requirement: Runtime metrics exposure
系统 MUST 暴露可观测指标：当前队列长度与容量、活跃线程数、成功/失败计数、重试计数、平均排队时间与平均处理时间。

#### Scenario: Metrics endpoint reports queue and latency
- **WHEN** 调用指标接口或等价观测出口
- **THEN** 可见队列长度、容量、活跃线程
- **AND** 可见成功/失败/重试计数与平均排队/处理时延

### Requirement: Graceful shutdown and recovery
收到停机信号后，系统 MUST 停止接收新任务，将内存队列中未处理任务安全回写/保持持久化一致性，线程池在时限内收敛；重启后 MUST 能恢复待处理任务并继续可追溯。

#### Scenario: Shutdown stops accept and preserves work
- **WHEN** 系统收到停机信号
- **THEN** 新提交被拒绝或返回不可用
- **AND** 内存中未处理任务被安全回写或已与持久化状态一致
- **AND** 线程池在配置时限内结束

#### Scenario: Restart recovers queued work
- **GIVEN** 停机前存在已接收但未完成的任务
- **WHEN** 进程重新启动
- **THEN** 这些任务可被重新调度
- **AND** 仍可通过原 `taskId` 查询状态与最终结果
