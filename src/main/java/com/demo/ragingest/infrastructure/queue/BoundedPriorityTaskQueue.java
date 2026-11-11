package com.demo.ragingest.infrastructure.queue;

import com.demo.ragingest.domain.QueuedTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class BoundedPriorityTaskQueue {
    private final int capacity;
    private int size = 0;
    private final PriorityQueue<QueuedTask> queue = new PriorityQueue<>(
        Comparator.comparingInt(QueuedTask::priority).reversed()
            .thenComparingLong(QueuedTask::enqueueSeq)
    );

    public BoundedPriorityTaskQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    public synchronized boolean offer(QueuedTask task) {
        if (size >= capacity) return false;
        if (!queue.offer(task)) return false;
        size++;
        notifyAll();
        return true;
    }

    public synchronized QueuedTask take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        QueuedTask task = queue.poll();
        size--;
        return task;
    }

    public synchronized boolean remove(String taskId) {
        for (QueuedTask candidate : queue) {
            if (candidate.taskId().equals(taskId) && queue.remove(candidate)) {
                size--;
                return true;
            }
        }
        return false;
    }

    public synchronized List<QueuedTask> drainAll() {
        List<QueuedTask> drained = new ArrayList<>(queue);
        queue.clear();
        size = 0;
        return drained;
    }

    public synchronized boolean contains(String taskId) {
        for (QueuedTask candidate : queue) {
            if (candidate.taskId().equals(taskId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized int size() { return size; }
    public int capacity() { return capacity; }
}
