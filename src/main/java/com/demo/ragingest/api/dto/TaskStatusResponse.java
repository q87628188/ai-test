package com.demo.ragingest.api.dto;

import java.time.Instant;

public class TaskStatusResponse {

    private String taskId;
    private String businessKey;
    private String status;
    private int priority;
    private int retryCount;
    private String failReason;
    private Instant enqueuedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant nextRunAt;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public Instant getEnqueuedAt() { return enqueuedAt; }
    public void setEnqueuedAt(Instant enqueuedAt) { this.enqueuedAt = enqueuedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
}
