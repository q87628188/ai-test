# Spec Delta

## Purpose

定义文档入库异步任务的提交入口：快速返回任务标识、业务键幂等，以及「成功接收」必须先持久化的可靠性边界。

## ADDED Requirements

### Requirement: Scenario-modeled document ingest task
系统 MUST 将任务建模为企业智能问答场景下的「文档入库」异步任务，并明确任务类型、输入输出、执行特征与失败概率假设。

#### Scenario: Task model is described at submit contract
- **GIVEN** 调用方准备提交文档入库任务
- **WHEN** 查阅提交契约或任务模型说明
- **THEN** 可见任务类型为文档解析/切片/向量化入库（模拟）
- **AND** 输入至少包含业务键、文档标识、优先级
- **AND** 输出为可查询的任务状态与入库结果摘要
- **AND** 执行特征标注为 IO/混合型，并给出可配置的失败概率用于模拟

### Requirement: Fast accept with task id
系统 MUST 在接收业务参数后快速返回 `taskId`，不得在提交接口内同步执行完整处理。

#### Scenario: Submit returns immediately with task id
- **WHEN** 调用方提交合法的文档入库任务
- **THEN** 接口在入队受理完成后立即返回 `taskId`
- **AND** 不阻塞等待解析/向量化模拟完成

### Requirement: Accepted means durable
系统 MUST 将「成功接收」定义为：任务记录已持久化到任务状态表，且可在进程重启后被恢复调度；仅写入内存队列不得视为成功接收。

#### Scenario: Accept boundary before enqueue visibility
- **WHEN** 提交接口返回成功与 `taskId`
- **THEN** 该任务在任务状态表中已存在且状态为可调度（如 QUEUED）
- **AND** 随后进程崩溃再启动后，该任务仍可被恢复处理

### Requirement: Business-key idempotency
系统 MUST 对相同业务键的重复提交做幂等：返回同一 `taskId`，或明确拒绝并说明原因；不得创建重复可执行任务。

#### Scenario: Duplicate business key returns same task
- **GIVEN** 业务键 `doc:handbook-v1` 已成功接收
- **WHEN** 再次以同一业务键提交
- **THEN** 系统返回原 `taskId` 或返回明确的幂等拒绝
- **AND** 不会产生第二个可执行任务实例
