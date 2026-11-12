# Tasks

## 1. 提交契约切换

- [x] 1.1 将任务类型与请求 DTO/`SubmitCommand` 改为单条风险事件字段（`businessKey`、`priority`、`userId`、`eventTime`、`deviceId`、`clientIp`、`targetSystem`、可选 `action`/`failProbability`/`fatal`），移除 `docId`/`fileName` 与窗口边界字段；用编译与校验注解确认必填项
- [x] 1.2 更新提交 API 测试：合法单条 event 快速返回 `taskId`、同 `businessKey` 幂等、非法缺字段被拒；`mvn -Dtest=TaskSubmitApiTest test` 通过

## 2. 处理与结果模拟

- [x] 2.1 将文档入库模拟器替换为风险事件清洗/特征化模拟（CPU/混合，支持失败注入），结果 JSON 改为事件处理摘要；相关单测通过
- [x] 2.2 更新成功/失败/重试集成测试断言新结果字段与终态行为；相关测试通过

## 3. 消费位点与重启恢复

- [x] 3.1 核对并补强 `StartupRecovery`：`RUNNING`→可调度、到期 `RETRY_WAIT`→可调度、全部 `QUEUED` 重新加载内存队列；`SUCCESS`/`FAILED`/`DEAD` 不加载；补充或更新恢复集成测试验证「已消费可区分、未消费可重载」
- [x] 3.2 核对优雅停机与恢复联测仍满足停收、持久化一致、重启可调度；相关测试通过

## 4. 文档与总验收

- [x] 4.1 更新 README、`docs/tradeoffs.md`、`docs/ai-dialogue.md`：场景改为风险事件采集映射、消费位点/at-least-once 说明、示例 curl 使用新字段
- [x] 4.2 运行 `mvn test` 全绿，并用示例 curl 提交一条 event 后查询状态/结果可通

## Workflow follow-up

- 实现完成后用 `/opsx-apply`（或 openspec apply）按本清单推进。
- 验收通过后归档 change，并将 delta sync 进主 specs。
