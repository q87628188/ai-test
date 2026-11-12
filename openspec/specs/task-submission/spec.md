# task-submission Specification

## Purpose

定义文档入库异步任务的提交入口：快速返回任务标识、业务键幂等，以及「成功接收」必须先持久化的可靠性边界。

## Requirements

### Requirement: Scenario-modeled document ingest task
系统 MUST 将任务建模为风险引擎数据采集场景下的「单条访问事件」异步清洗/特征化任务，并明确任务类型、输入输出、执行特征与失败概率假设。

#### Scenario: Task model is described at submit contract
- **GIVEN** 调用方准备提交风险事件任务
- **WHEN** 查阅提交契约或任务模型说明
- **THEN** 可见任务类型为单条风险访问事件的清洗/特征化（模拟，对应生产上 Logstash→队列→Spark 链路中的处理侧）
- **AND** 输入包含调用方显式业务键、优先级，以及单条事件业务内容：谁（userId）、时间（eventTime）、设备（deviceId）、IP（clientIp）、目标系统（targetSystem）；可选操作类型（action）
- **AND** 输入 MUST NOT 包含 windowStart/windowEnd，且 MUST NOT 以事件数组一次提交多条
- **AND** 输出为可查询的任务状态与事件处理结果摘要
- **AND** 执行特征标注为 CPU/混合型，并给出可配置的失败概率用于模拟

### Requirement: Fast accept with task id
系统 MUST 在接收业务参数后快速返回 `taskId`，不得在提交接口内同步执行完整处理。

#### Scenario: Submit returns immediately with task id
- **WHEN** 调用方提交合法的单条风险事件任务
- **THEN** 接口在入队受理完成后立即返回 `taskId`
- **AND** 不阻塞等待清洗/特征化模拟完成

### Requirement: Accepted means durable
系统 MUST 将「成功接收」定义为：任务记录已持久化到任务状态表，且可在进程重启后被恢复调度；仅写入内存队列不得视为成功接收。

#### Scenario: Accept boundary before enqueue visibility
- **WHEN** 提交接口返回成功与 `taskId`
- **THEN** 该任务在任务状态表中已存在且状态为可调度（如 QUEUED）
- **AND** 随后进程崩溃再启动后，该任务仍可被恢复处理

### Requirement: Business-key idempotency
系统 MUST 对调用方显式提供的相同业务键的重复提交做幂等：返回同一 `taskId`，或明确拒绝并说明原因；不得创建重复可执行任务。业务键 MUST NOT 由服务端根据事件字段自动派生作为唯一策略。

#### Scenario: Duplicate business key returns same task
- **GIVEN** 业务键 `risk:evt:01JBDEMO` 已成功接收
- **WHEN** 再次以同一业务键提交
- **THEN** 系统返回原 `taskId` 或返回明确的幂等拒绝
- **AND** 不会产生第二个可执行任务实例
