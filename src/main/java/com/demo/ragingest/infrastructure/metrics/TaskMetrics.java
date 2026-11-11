package com.demo.ragingest.infrastructure.metrics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class TaskMetrics {

    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder retryCount = new LongAdder();
    private final LongAdder queueWaitMsTotal = new LongAdder();
    private final LongAdder queueWaitSamples = new LongAdder();
    private final LongAdder processMsTotal = new LongAdder();
    private final LongAdder processSamples = new LongAdder();
    private final AtomicInteger activeWorkers = new AtomicInteger();

    public void incSuccess() {
        successCount.increment();
    }

    public void incFailed() {
        failureCount.increment();
    }

    public void incRetry() {
        retryCount.increment();
    }

    public void recordQueueWaitMs(long ms) {
        queueWaitMsTotal.add(ms);
        queueWaitSamples.increment();
    }

    public void recordProcessMs(long ms) {
        processMsTotal.add(ms);
        processSamples.increment();
    }

    public void incActiveWorkers() {
        activeWorkers.incrementAndGet();
    }

    public void decActiveWorkers() {
        activeWorkers.decrementAndGet();
    }

    public long successCount() {
        return successCount.sum();
    }

    public long failureCount() {
        return failureCount.sum();
    }

    public long retryCount() {
        return retryCount.sum();
    }

    public int activeThreads() {
        return activeWorkers.get();
    }

    public double avgQueueMs() {
        long n = queueWaitSamples.sum();
        return n == 0 ? 0.0 : (double) queueWaitMsTotal.sum() / n;
    }

    public double avgProcessMs() {
        long n = processSamples.sum();
        return n == 0 ? 0.0 : (double) processMsTotal.sum() / n;
    }
}
