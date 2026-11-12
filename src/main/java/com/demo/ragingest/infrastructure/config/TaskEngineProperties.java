package com.demo.ragingest.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务引擎配置（{@code task.engine.*}）。
 * <p>队列容量默认 {@code workerThreads × queueFactor}，可用 {@code queueCapacity} 覆盖。
 */
@ConfigurationProperties(prefix = "task.engine")
public class TaskEngineProperties {

    /** 固定 Worker 线程数。 */
    private int workerThreads = 4;
    /** 默认容量因子：capacity = threads × factor。 */
    private int queueFactor = 20;
    /** &gt;0 时覆盖默认容量公式。 */
    private int queueCapacity = 0;
    /** 可重试失败最大次数，超限 → DEAD。 */
    private int maxRetries = 3;
    /** 退避基数（毫秒）。 */
    private long retryBaseMs = 200;
    /** 退避上限（毫秒）。 */
    private long retryMaxMs = 5000;
    /** 模拟处理 sleep。 */
    private long processSleepMs = 100;
    /** 未指定 failProbability 时的默认失败率。 */
    private double defaultFailProbability = 0.1;
    /** 优雅停机等待 Worker 的秒数。 */
    private int shutdownTimeoutSeconds = 10;
    /** 入队补偿扫描间隔。 */
    private long compensatorFixedDelayMs = 500;
    /** 重试提升扫描间隔。 */
    private long retrySchedulerFixedDelayMs = 200;

    /** 解析实际有界队列容量。 */
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
