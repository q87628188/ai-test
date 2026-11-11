CREATE TABLE IF NOT EXISTS task_status (
  id VARCHAR(36) PRIMARY KEY,
  business_key VARCHAR(255) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  priority INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_run_at TIMESTAMP NULL,
  fail_reason VARCHAR(1024) NULL,
  payload_json CLOB NOT NULL,
  enqueued_at TIMESTAMP NOT NULL,
  started_at TIMESTAMP NULL,
  finished_at TIMESTAMP NULL,
  CONSTRAINT uk_task_status_business_key UNIQUE (business_key)
);

CREATE TABLE IF NOT EXISTS task_result (
  task_id VARCHAR(36) PRIMARY KEY,
  result_json CLOB NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_task_result_task FOREIGN KEY (task_id) REFERENCES task_status(id)
);

CREATE INDEX IF NOT EXISTS idx_task_status_status ON task_status(status);
CREATE INDEX IF NOT EXISTS idx_task_status_retry ON task_status(status, next_run_at);
