package com.demo.ragingest.domain;

/** 可重试失败，处理器转入 RETRY_WAIT 或超限后 DEAD。 */
public class RetryableTaskException extends RuntimeException {
    public RetryableTaskException(String message) {
        super(message);
    }
}
