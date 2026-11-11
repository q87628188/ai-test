package com.demo.ragingest.domain;

public class RetryableTaskException extends RuntimeException {
    public RetryableTaskException(String message) {
        super(message);
    }
}
