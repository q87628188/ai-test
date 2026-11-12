# 关键 AI 对话摘录

本文件保留与本考核实现相关的关键对话要点，满足「使用 AI 作答并留存对话」的提交要求。

## 1. 初版：文档入库场景

- 输入：Java/AI 应用考核题 + 简历匹配。
- 初选场景：企业智能问答「文档解析/切片/向量化入库」。
- 产出并归档：`openspec/changes/archive/2026-11-11-rag-doc-async-ingest/`。

## 2. 场景切换：风险引擎数据采集

- 用户指出应贴合风险引擎：Logstash 采集 → 消息队列 → Spark 清洗聚合。
- 约束：考核禁止外部 MQ；接口每次只上传一条 event（谁/时间/设备/IP/系统）；显式 `businessKey`；不含 `windowStart`/`windowEnd`。
- 要求：宕机丢内存队列后，重启能区分已消费/未消费，未消费重新加载进队列。
- 产出：`openspec/changes/risk-event-async-ingest/`（proposal / design / specs / tasks）。

## 3. 关键设计决策

- 生产组件映射到进程内引擎；不接真实 Logstash/Kafka/Spark。
- DB 为真相，内存有界优先队列为调度；消费位点用任务状态表达。
- at-least-once + `businessKey` 幂等；`RUNNING` 恢复为可调度。
- 任务类型 `RISK_EVENT_INGEST`；结果摘要 `normalized` / `riskTags` / `durationMs`。

## 4. 执行

- 用户选择在已有引擎上换皮实现（非绿场重做文档入库）。
- 本会话内联落地契约、模拟器、恢复测试与文档更新。
