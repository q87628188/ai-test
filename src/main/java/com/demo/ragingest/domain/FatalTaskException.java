package com.demo.ragingest.domain;

/** 不可重试失败，处理器将其落为 FAILED。 */
public class FatalTaskException extends RuntimeException {
    public FatalTaskException(String message) {
        super(message);
    }
}
