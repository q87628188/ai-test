# Verify Report — risk-event-async-ingest

**Date:** 2026-11-12  
**Schema note:** Current project uses `spec-driven`（无内置 `/opsx-verify` artifact）。本报告为对照 proposal / design / specs / tasks 的手工验收。

## Verdict

**PASS** — 风险事件换皮实现与 OpenSpec change 一致；`openspec validate --strict` 通过；`mvn test` 全绿；此前 curl 冒烟（8081）提交单条 event → `SUCCESS` + `normalized`/`riskTags` 结果可查。

## Checks

| # | Check | Result | Evidence |
|---|--------|--------|----------|
| 1 | OpenSpec 结构校验 | PASS | `openspec validate risk-event-async-ingest --strict` → valid, 0 issues |
| 2 | Tasks 完成度 | PASS | `tasks.md` 8/8 `[x]`；`openspec list` status=`complete` |
| 3 | Spec: task-submission | PASS | 单条 event 字段（userId/eventTime/deviceId/clientIp/targetSystem）；无 window*；显式 businessKey 幂等；DB 先落库 |
| 4 | Spec: task-execution | PASS | `RiskEventSimulator`；结果 `normalized`/`riskTags`/`durationMs`；fatal→FAILED；retry→DEAD |
| 5 | Spec: task-observability | PASS | 查询/统计/指标保留；`StartupRecoveryTest` 区分已消费/未消费；优雅停机联测通过 |
| 6 | Spec: priority-task-queue（未改 delta） | PASS | 既有有界优先队列与 429 反压仍生效 |
| 7 | 自动化测试 | PASS | `mvn test` **23/23** green（清空 `./data` 后复跑） |
| 8 | 运行时冒烟 | PASS | 历史：POST 单条 event → taskId；status=`SUCCESS`；result 含 riskTags |

## Gaps / notes

- **主 specs 尚未 sync**：delta 仍在 `openspec/changes/risk-event-async-ingest/specs/`；归档前需 `/opsx-archive` 并选择 Sync now，否则 `openspec/specs/` 仍为文档入库叙事。
- **Git**：风险事件改动此前 commit/push 被中断，工作区仍有已暂存未提交变更；PR 前需先 commit + push。
- Formal `/opsx-verify` slash command **未安装**；本报告为等价手工流程。

## Next

1. Commit + push 风险事件改动，再创建/更新 PR（若仍要选项 2）。
2. `/opsx-archive`（推荐 Sync now）把 delta 合入主 specs。
