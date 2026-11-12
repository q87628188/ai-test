# Spec Delta: task-observability

## ADDED Requirements

### Requirement: Durable consumption cursor via task status
系统 MUST 以持久化任务状态作为消费进度依据，使得进程宕机导致内存队列丢失后，仍能区分哪些任务已消费完成、哪些尚未消费完成。已成功消费 MUST 对应成功终态且结果可查；已结案失败 MUST 视为不再自动加载。未消费完成 MUST 至少覆盖排队中、退避等待中，以及宕机时处于处理中的任务。

#### Scenario: Distinguish consumed after crash
- **GIVEN** 宕机前部分任务已成功并写入结果，部分仍为排队或处理中
- **WHEN** 进程重启并完成恢复扫描
- **THEN** 已成功任务保持成功终态且结果仍可按原 `taskId` 查询
- **AND** 未完成任务可被识别为待恢复（非成功终态）

#### Scenario: Terminal failures are not reloaded as new work
- **GIVEN** 任务已处于失败或死信终态
- **WHEN** 进程重启执行恢复
- **THEN** 该任务 MUST NOT 被重新加载进内存队列自动执行

## MODIFIED Requirements

### Requirement: Graceful shutdown and recovery
收到停机信号后，系统 MUST 停止接收新任务，将内存队列中未处理任务安全回写/保持持久化一致性，线程池在时限内收敛；因宕机丢失内存队列后，重启 MUST 将未消费完成的任务重新加载进内存队列并继续可追溯，MUST NOT 将已成功消费的任务再次调度。

#### Scenario: Shutdown stops accept and preserves work
- **WHEN** 系统收到停机信号
- **THEN** 新提交被拒绝或返回不可用
- **AND** 内存中未处理任务被安全回写或已与持久化状态一致
- **AND** 线程池在配置时限内结束

#### Scenario: Restart recovers queued work
- **GIVEN** 停机或宕机前存在已接收但未完成的任务
- **WHEN** 进程重新启动
- **THEN** 这些未完成任务被重新加载进内存队列并可被调度
- **AND** 仍可通过原 `taskId` 查询状态与最终结果

#### Scenario: In-flight running work is treated as unconsumed
- **GIVEN** 宕机时存在状态为处理中的任务
- **WHEN** 进程重新启动并完成恢复
- **THEN** 这些任务被纠正为可调度状态并加载进内存队列
- **AND** 不会因内存队列丢失而永久消失
