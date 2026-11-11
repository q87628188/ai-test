# 关键 AI 对话摘录

本文件保留与本考核实现相关的关键对话要点，满足「使用 AI 作答并留存对话」的提交要求。

## 1. 需求与场景匹配（OpenSpec propose）

- 输入：Java/AI 应用考核题（并发、有界队列、反压、入队不丢失、重试幂等、优雅停机）+ 简历 PDF。
- AI 结论：从简历中选取「企业智能问答系统 · 文档解析/切片/向量化入库」作为异步任务场景，优于电商 Agent（偏交互）与数字藏品（偏中间件）。
- 产出：`openspec/changes/rag-doc-async-ingest/` 下 proposal / design / specs / tasks。

## 2. 实现计划（writing-plans）

- 用户要求以 OpenSpec 产物为基准，把 `tasks.md` 拆成可落地开发任务。
- AI 产出：`docs/superpowers/plans/2026-11-11-rag-doc-async-ingest.md`（9 个 Task，TDD 步骤与文件路径）。

## 3. 执行方式

- 用户选择 Subagent-Driven；Task 1–2 经子代理推进后触及 usage limit。
- 后续按用户选择改为**本会话内联执行** Plan Task 3–9，保留分步 git commit。

## 4. 关键设计决策（对话中落地）

- DB 为真相，内存有界优先队列为调度。
- 提交：`TransactionTemplate` 先提交再 `offer`，避免 Worker 读到未提交行。
- 反压：内存满时新键 429；已落库任务由补偿器入队。
- 重试：`RETRY_WAIT` 不占槽；到期再入队；超额 `DEAD`。
