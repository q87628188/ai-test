package com.demo.ragingest.domain;

/**
 * 任务状态机。
 * <pre>
 *   提交 → QUEUED → RUNNING → SUCCESS
 *                     │
 *                     ├─ fatal        → FAILED
 *                     └─ retryable    → RETRY_WAIT →（到期）QUEUED → …
 *                                     →（超限）DEAD
 * </pre>
 */
public enum TaskStatus {
    /** 已落库，等待或已在内存队列中。 */
    QUEUED,
    /** Worker 正在执行。 */
    RUNNING,
    /** 可重试失败，等待 next_run_at；不占内存队列槽。 */
    RETRY_WAIT,
    /** 处理成功，已写 task_result。 */
    SUCCESS,
    /** 不可重试失败（fatal / 非法载荷）。 */
    FAILED,
    /** 可重试次数耗尽。 */
    DEAD
}
