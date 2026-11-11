package com.demo.ragingest.api.dto;

public class TaskStatsResponse {

    /** QUEUED + RETRY_WAIT */
    private long backlog;
    /** RUNNING */
    private long processing;
    /** FAILED + DEAD */
    private long failed;

    public TaskStatsResponse() {}

    public TaskStatsResponse(long backlog, long processing, long failed) {
        this.backlog = backlog;
        this.processing = processing;
        this.failed = failed;
    }

    public long getBacklog() { return backlog; }
    public void setBacklog(long backlog) { this.backlog = backlog; }
    public long getProcessing() { return processing; }
    public void setProcessing(long processing) { this.processing = processing; }
    public long getFailed() { return failed; }
    public void setFailed(long failed) { this.failed = failed; }
}
