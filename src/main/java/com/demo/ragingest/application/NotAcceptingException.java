package com.demo.ragingest.application;

public class NotAcceptingException extends RuntimeException {
    public NotAcceptingException(String message) {
        super(message);
    }
}
