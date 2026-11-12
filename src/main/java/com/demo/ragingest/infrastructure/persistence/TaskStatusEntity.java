package com.demo.ragingest.infrastructure.persistence;

import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.domain.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 映射 {@code task_status}：任务调度状态 + 输入消息体。
 * <p>消息内容存在 {@link #payloadJson}，内存队列只持有 taskId。
 */
@Entity
@Table(name = "task_status")
public class TaskStatusEntity {

    /** 任务 UUID（对外 taskId）。 */
    @Id
    @Column(length = 36)
    private String id;

    /** 业务幂等键；DB 唯一约束。 */
    @Column(name = "business_key", nullable = false, unique = true, length = 255)
    private String businessKey;

    /** 任务类型（当前仅 RISK_EVENT_INGEST）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private TaskType taskType;

    /** 优先级，越大越先消费。 */
    @Column(nullable = false)
    private int priority;

    /** 状态机当前值。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    /** 已发生可重试失败次数。 */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** RETRY_WAIT 下次可跑时间。 */
    @Column(name = "next_run_at")
    private Instant nextRunAt;

    /** 最近失败原因。 */
    @Column(name = "fail_reason", length = 1024)
    private String failReason;

    /**
     * 输入消息整包 JSON（userId/eventTime/deviceId/...）。
     * 受理时序列化写入，Worker 执行时反序列化读取。
     */
    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    /** 受理/首次入队时间。 */
    @Column(name = "enqueued_at", nullable = false)
    private Instant enqueuedAt;

    /** 进入 RUNNING 时间。 */
    @Column(name = "started_at")
    private Instant startedAt;

    /** 终态完成时间。 */
    @Column(name = "finished_at")
    private Instant finishedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    public void setEnqueuedAt(Instant enqueuedAt) {
        this.enqueuedAt = enqueuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
