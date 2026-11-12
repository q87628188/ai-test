package com.demo.ragingest.application;

/** 内存有界队列已满；映射为 HTTP 429 QUEUE_FULL。 */
public class QueueFullException extends RuntimeException {
    public QueueFullException(String message) {
        super(message);
    }
}
