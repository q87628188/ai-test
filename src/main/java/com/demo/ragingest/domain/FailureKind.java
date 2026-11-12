package com.demo.ragingest.domain;

/** 失败分类（文档/扩展用）：可重试业务、可恢复系统、不可恢复。 */
public enum FailureKind {
    RETRYABLE_BUSINESS,
    RECOVERABLE_SYSTEM,
    UNRECOVERABLE
}
