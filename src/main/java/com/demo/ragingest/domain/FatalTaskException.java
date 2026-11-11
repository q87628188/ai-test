package com.demo.ragingest.domain;

public class FatalTaskException extends RuntimeException {
    public FatalTaskException(String message) {
        super(message);
    }
}
