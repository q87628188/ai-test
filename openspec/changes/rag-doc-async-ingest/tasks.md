# Tasks

## 1. Project scaffolding

- [x] 1.1 初始化 Spring Boot 3 / Java 17+ 工程（Maven 或 Gradle），加入 Spring Web、Spring Data JPA、H2、校验与测试依赖，并验证空应用可启动
- [x] 1.2 配置 H2 文件模式与 `schema.sql`（或等价 DDL 自动建表），创建空库文件目录，验证重启后库文件仍存在
- [x] 1.3 建立分层包结构 `api/application/domain/infrastructure`，提交初始 README 启动说明并验证文档中的启动命令可用

## 2. Persistence and submission

- [x] 2.1 实现 `task_status` 与 `task_result` 实体/映射及仓库，验证表结构与唯一约束（`business_key`）符合 design
- [x] 2.2 实现提交用例：校验入参、持久化 `QUEUED`、生成 `taskId`、业务键幂等（同键返回同 ID），并验证对应单测通过
- [x] 2.3 实现 `POST /api/tasks`，验证合法提交快速返回 `taskId`，且响应前 DB 已可见该任务

## 3. Bounded priority queue and workers

- [x] 3.1 实现有界优先内存队列（JDK 并发原语，容量=`threads×factor` 可配置），验证高优先级先出队、超容量触发反压
- [x] 3.2 实现受理后入队与「DB 已受理但入队失败」补偿扫描，验证进程内不丢已受理任务
- [x] 3.3 实现固定 Worker 循环拉取 + sleep/本地计算模拟 `DOC_INGEST`，验证成功路径写入结果表且状态为成功
- [x] 3.4 为队列有界性与优先级编写并发单测，验证多线程入出队不突破容量且顺序符合优先级

## 4. Failure, retry, and dead letter

- [x] 4.1 实现失败分类（可重试 vs 不可恢复）与失败注入配置，验证不可恢复直接失败终态
- [x] 4.2 实现限次指数退避重试（`RETRY_WAIT` + 到期再入队），验证退避不长期占用有界队列槽位
- [x] 4.3 实现达上限进入 `DEAD/FAILED` 并记录原因，验证查询可见重试次数与原因

## 5. Query, metrics, and shutdown

- [x] 5.1 实现按 `taskId` 查状态/结果与按状态统计 API，验证与 specs 场景一致
- [x] 5.2 实现指标出口（队列长度/容量、活跃线程、成功失败重试计数、平均排队/处理时延），验证接口字段齐全
- [x] 5.3 实现优雅停机：停收新任务、回写/对齐未处理任务、线程池限时收敛；验证重启后可恢复未完成任务
- [x] 5.4 编写停机恢复与统计的集成测试，验证「不丢失」主路径

## 6. Documentation and delivery hygiene

- [x] 6.1 撰写 `docs/tradeoffs.md`（容量决策、反压选型、「不丢失」边界与至少一次执行权衡），验证文档与实现配置项一致
- [x] 6.2 在仓库保留关键 AI 对话摘录（截图或文本）与分步 git commit，验证历史可反映迭代过程
- [x] 6.3 做一次端到端手工验收：提交高/低优先级任务、打满队列反压、失败重试至死信、查询指标与重启恢复，验证清单全部勾选

## Workflow follow-up

- 实现完成后运行 `/opsx-apply` 跟踪 tasks 勾选进度。
- 评审通过后 archive 本 change，将 delta specs 合并进 `openspec/specs/`。
