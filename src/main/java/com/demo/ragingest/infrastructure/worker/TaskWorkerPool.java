package com.demo.ragingest.infrastructure.worker;

import com.demo.ragingest.domain.QueuedTask;
import com.demo.ragingest.infrastructure.config.TaskEngineProperties;
import com.demo.ragingest.infrastructure.metrics.TaskMetrics;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TaskWorkerPool {

    private static final Logger log = LoggerFactory.getLogger(TaskWorkerPool.class);

    private final BoundedPriorityTaskQueue queue;
    private final TaskProcessor processor;
    private final TaskEngineProperties props;
    private final TaskMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Thread> workers = new ArrayList<>();

    public TaskWorkerPool(
        BoundedPriorityTaskQueue queue,
        TaskProcessor processor,
        TaskEngineProperties props,
        TaskMetrics metrics
    ) {
        this.queue = queue;
        this.processor = processor;
        this.props = props;
        this.metrics = metrics;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        int n = props.getWorkerThreads();
        for (int i = 0; i < n; i++) {
            Thread worker = new Thread(this::loop, "doc-ingest-worker-" + i);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }
        log.info("Started {} DOC_INGEST workers", n);
    }

    private void loop() {
        while (running.get()) {
            try {
                QueuedTask qt = queue.take();
                metrics.incActiveWorkers();
                try {
                    processor.process(qt);
                } catch (RuntimeException ex) {
                    log.warn("Worker failed processing task {}: {}", qt.taskId(), ex.getMessage());
                } finally {
                    metrics.decActiveWorkers();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void shutdown(Duration timeout) {
        running.set(false);
        for (Thread worker : workers) {
            worker.interrupt();
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        for (Thread worker : workers) {
            long remainingMs = Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L);
            try {
                worker.join(remainingMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        workers.clear();
    }
}
