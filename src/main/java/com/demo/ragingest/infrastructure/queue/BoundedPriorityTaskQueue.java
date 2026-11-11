package com.demo.ragingest.infrastructure.queue;

import com.demo.ragingest.domain.QueuedTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class BoundedPriorityTaskQueue {
    private final int capacity;
    private final AtomicInteger size = new AtomicInteger(0);
    private final PriorityBlockingQueue<QueuedTask> queue = new PriorityBlockingQueue<>(
        11,
        Comparator.comparingInt(QueuedTask::priority).reversed()
            .thenComparingLong(QueuedTask::enqueueSeq)
    );

    public BoundedPriorityTaskQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    public boolean offer(QueuedTask task) {
        while (true) {
            int current = size.get();
            if (current >= capacity) return false;
            if (!size.compareAndSet(current, current + 1)) continue;
            if (!queue.offer(task)) {
                size.decrementAndGet();
                return false;
            }
            return true;
        }
    }

    public QueuedTask take() throws InterruptedException {
        QueuedTask task = queue.take();
        size.decrementAndGet();
        return task;
    }

    public boolean remove(String taskId) {
        for (QueuedTask candidate : queue) {
            if (candidate.taskId().equals(taskId) && queue.remove(candidate)) {
                size.decrementAndGet();
                return true;
            }
        }
        return false;
    }

    public synchronized List<QueuedTask> drainAll() {
        List<QueuedTask> drained = new ArrayList<>();
        queue.drainTo(drained);
        size.set(0);
        return drained;
    }

    public int size() { return size.get(); }
    public int capacity() { return capacity; }
}
