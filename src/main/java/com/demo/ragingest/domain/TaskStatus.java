package com.demo.ragingest.domain;

public enum TaskStatus {
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    SUCCESS,
    FAILED,
    DEAD
}
