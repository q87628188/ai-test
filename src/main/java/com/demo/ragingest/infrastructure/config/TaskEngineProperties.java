package com.demo.ragingest.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "task.engine")
public class TaskEngineProperties {

    private int workerThreads = 4;
    private int queueFactor = 20;
    private int queueCapacity = 0;
    private int maxRetries = 3;
    private long retryBaseMs = 200;
    private long retryMaxMs = 5000;
    private long processSleepMs = 100;
    private double defaultFailProbability = 0.1;
    private int shutdownTimeoutSeconds = 10;
    private long compensatorFixedDelayMs = 500;
    private long retrySchedulerFixedDelayMs = 200;

    public int resolvedQueueCapacity() {
        return queueCapacity > 0 ? queueCapacity : workerThreads * queueFactor;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getQueueFactor() {
        return queueFactor;
    }

    public void setQueueFactor(int queueFactor) {
        this.queueFactor = queueFactor;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBaseMs() {
        return retryBaseMs;
    }

    public void setRetryBaseMs(long retryBaseMs) {
        this.retryBaseMs = retryBaseMs;
    }

    public long getRetryMaxMs() {
        return retryMaxMs;
    }

    public void setRetryMaxMs(long retryMaxMs) {
        this.retryMaxMs = retryMaxMs;
    }

    public long getProcessSleepMs() {
        return processSleepMs;
    }

    public void setProcessSleepMs(long processSleepMs) {
        this.processSleepMs = processSleepMs;
    }

    public double getDefaultFailProbability() {
        return defaultFailProbability;
    }

    public void setDefaultFailProbability(double defaultFailProbability) {
        this.defaultFailProbability = defaultFailProbability;
    }

    public int getShutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }

    public void setShutdownTimeoutSeconds(int shutdownTimeoutSeconds) {
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }

    public long getCompensatorFixedDelayMs() {
        return compensatorFixedDelayMs;
    }

    public void setCompensatorFixedDelayMs(long compensatorFixedDelayMs) {
        this.compensatorFixedDelayMs = compensatorFixedDelayMs;
    }

    public long getRetrySchedulerFixedDelayMs() {
        return retrySchedulerFixedDelayMs;
    }

    public void setRetrySchedulerFixedDelayMs(long retrySchedulerFixedDelayMs) {
        this.retrySchedulerFixedDelayMs = retrySchedulerFixedDelayMs;
    }
}
