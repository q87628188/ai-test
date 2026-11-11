package com.demo.ragingest.application;

public record SubmitCommand(
    String businessKey,
    String docId,
    String fileName,
    int priority,
    Double failProbability,
    Boolean fatal
) {}
