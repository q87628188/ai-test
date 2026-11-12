package com.demo.ragingest.application;

/** 停机门禁关闭；映射为 HTTP 503 NOT_ACCEPTING。 */
public class NotAcceptingException extends RuntimeException {
    public NotAcceptingException(String message) {
        super(message);
    }
}
