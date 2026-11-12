package com.demo.ragingest.domain;

/**
 * 内存队列元素：仅索引，不含消息体（消息在 DB {@code payload_json}）。
 *
 * @param taskId     对应 task_status.id
 * @param priority   越大越优先
 * @param enqueueSeq 同优先级 FIFO 序号
 */
public record QueuedTask(String taskId, int priority, long enqueueSeq) {}
