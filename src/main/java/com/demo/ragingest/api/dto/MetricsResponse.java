package com.demo.ragingest.api.dto;

public class MetricsResponse {

    private int queueSize;
    private int queueCapacity;
    private int activeThreads;
    private long successCount;
    private long failureCount;
    private long retryCount;
    private double avgQueueMs;
    private double avgProcessMs;

    public int getQueueSize() { return queueSize; }
    public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getActiveThreads() { return activeThreads; }
    public void setActiveThreads(int activeThreads) { this.activeThreads = activeThreads; }
    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }
    public long getFailureCount() { return failureCount; }
    public void setFailureCount(long failureCount) { this.failureCount = failureCount; }
    public long getRetryCount() { return retryCount; }
    public void setRetryCount(long retryCount) { this.retryCount = retryCount; }
    public double getAvgQueueMs() { return avgQueueMs; }
    public void setAvgQueueMs(double avgQueueMs) { this.avgQueueMs = avgQueueMs; }
    public double getAvgProcessMs() { return avgProcessMs; }
    public void setAvgProcessMs(double avgProcessMs) { this.avgProcessMs = avgProcessMs; }
}
