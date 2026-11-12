-- =============================================================================
-- 风险事件异步摄取：持久化真相源（H2 文件库）
-- 约定：成功受理 = task_status 事务提交；内存队列可丢，重启按状态恢复（at-least-once）
-- =============================================================================

-- 任务状态表：调度元数据 + 输入消息体（payload_json）
CREATE TABLE IF NOT EXISTS task_status (
  id VARCHAR(36) PRIMARY KEY,                    -- 任务 UUID，对外返回的 taskId
  business_key VARCHAR(255) NOT NULL,            -- 业务幂等键；同键重复提交复用同一任务
  task_type VARCHAR(32) NOT NULL,                -- 任务类型，当前为 RISK_EVENT_INGEST
  priority INT NOT NULL,                         -- 优先级，数值越大越先被 Worker 消费
  status VARCHAR(32) NOT NULL,                   -- 状态机：QUEUED/RUNNING/RETRY_WAIT/SUCCESS/FAILED/DEAD
  retry_count INT NOT NULL DEFAULT 0,            -- 已发生的可重试失败次数
  next_run_at TIMESTAMP NULL,                    -- RETRY_WAIT 时下次可执行时间；到期由 RetryScheduler 提升
  fail_reason VARCHAR(1024) NULL,               -- 最近一次失败原因摘要
  payload_json CLOB NOT NULL,                    -- 输入消息整包 JSON（事件字段，非按列拆表）
  enqueued_at TIMESTAMP NOT NULL,                -- 首次写入/入队时间（受理时间）
  started_at TIMESTAMP NULL,                     -- 最近一次进入 RUNNING 的时间
  finished_at TIMESTAMP NULL,                    -- 终态（SUCCESS/FAILED/DEAD）完成时间
  CONSTRAINT uk_task_status_business_key UNIQUE (business_key)
);

-- 任务结果表：成功处理后的输出（与状态表 1:1）
CREATE TABLE IF NOT EXISTS task_result (
  task_id VARCHAR(36) PRIMARY KEY,               -- 对应 task_status.id
  result_json CLOB NOT NULL,                     -- 输出结果 JSON（normalized / riskTags / durationMs）
  created_at TIMESTAMP NOT NULL,                 -- 结果写入时间
  CONSTRAINT fk_task_result_task FOREIGN KEY (task_id) REFERENCES task_status(id)
);

-- 按状态扫描（恢复、补偿入队、统计）
CREATE INDEX IF NOT EXISTS idx_task_status_status ON task_status(status);
-- 扫描到期重试（status + next_run_at）
CREATE INDEX IF NOT EXISTS idx_task_status_retry ON task_status(status, next_run_at);

COMMENT ON TABLE task_status IS '任务状态与输入载荷；DB 为受理真相源';
COMMENT ON COLUMN task_status.business_key IS '幂等键，唯一约束保证同键只落一条任务';
COMMENT ON COLUMN task_status.payload_json IS '风险事件消息体 JSON，Worker 按 taskId 回读';
COMMENT ON COLUMN task_status.status IS 'QUEUED|RUNNING|RETRY_WAIT|SUCCESS|FAILED|DEAD';
COMMENT ON TABLE task_result IS '任务成功结果；仅 SUCCESS 时写入';
COMMENT ON COLUMN task_result.result_json IS '处理结果 JSON';
