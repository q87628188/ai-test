package com.demo.ragingest.domain;

public enum FailureKind {
    RETRYABLE_BUSINESS,
    RECOVERABLE_SYSTEM,
    UNRECOVERABLE
}
