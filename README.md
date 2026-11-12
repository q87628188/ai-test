# rag-doc-async-ingest

风险引擎场景下的 **单条访问事件异步清洗/特征化** 任务引擎（考核演示）：模拟 Logstash 推送一条 event → 自研有界优先队列 → Worker 模拟清洗特征（对应生产 Spark 侧）→ 结果可追溯。

- Java 17+ / Spring Boot 3.2 / Spring Data JPA / H2 **文件模式**
- 自研 JDK 有界优先队列（无 RabbitMQ/Kafka）
- 成功接收 = DB 持久化；反压、幂等、退避重试、优雅停机与启动恢复
- 消费位点：终态（`SUCCESS`/`FAILED`/`DEAD`）= 已结案；未完成状态宕机后可重新加载进内存队列

## 快速启动

```bash
mvn test
mvn spring-boot:run
```

H2 文件目录：`./data/tasks`（已 gitignore）。控制台：http://localhost:8080/h2-console

## 容量公式

```text
queueCapacity = workerThreads × queueFactor   # 默认 4 × 20 = 80
```

可用 `task.engine.queue-capacity` 覆盖。详见 `docs/tradeoffs.md`。

## API

### 提交单条风险事件

```bash
curl -s -X POST localhost:8080/api/tasks -H "Content-Type: application/json" -d "{\"businessKey\":\"risk:evt:e2e-low\",\"userId\":\"u10086\",\"eventTime\":\"2026-11-12T12:00:00Z\",\"deviceId\":\"ios-xxx\",\"clientIp\":\"203.0.113.10\",\"targetSystem\":\"payment-gateway\",\"action\":\"login\",\"priority\":1,\"failProbability\":0}"
curl -s -X POST localhost:8080/api/tasks -H "Content-Type: application/json" -d "{\"businessKey\":\"risk:evt:e2e-high\",\"userId\":\"u10086\",\"eventTime\":\"2026-11-12T12:01:00Z\",\"deviceId\":\"ios-xxx\",\"clientIp\":\"203.0.113.10\",\"targetSystem\":\"auth-service\",\"action\":\"login\",\"priority\":100,\"failProbability\":0}"
```

字段：显式 `businessKey` + 业务内容（谁/何时/设备/IP/系统，可选 `action`）+ `priority`；**无** `windowStart`/`windowEnd`，每次一条 event。

同 `businessKey` 再次提交返回同一 `taskId`（幂等）。内存队列满时新键返回 **429** `QUEUE_FULL`。

### 查询

```bash
curl -s localhost:8080/api/tasks/{taskId}
curl -s localhost:8080/api/tasks/{taskId}/result
curl -s localhost:8080/api/tasks/stats
curl -s localhost:8080/api/metrics
```

成功结果摘要含 `normalized`、`riskTags`、`durationMs`。

## 验收清单

- [x] 高低优先级提交；Worker 模拟处理成功写结果表
- [x] 打满队列反压（将 `task.engine.queue-capacity` 改为 2 后重启，连续 3 个新 businessKey，第 3 个期望 429）
- [x] `failProbability=1.0` 至 `DEAD`；`fatal=true` 直接 `FAILED`
- [x] 统计与指标接口字段齐全
- [x] 停机停收 + 重启恢复未完成任务；已消费（SUCCESS）不重载
- [x] `mvn test` 通过；`docs/tradeoffs.md` / `docs/ai-dialogue.md` 已提供

## 文档

- OpenSpec 变更：`openspec/changes/risk-event-async-ingest/`
- 历史文档入库归档：`openspec/changes/archive/2026-11-11-rag-doc-async-ingest/`
- 权衡：`docs/tradeoffs.md`
- AI 对话：`docs/ai-dialogue.md`
