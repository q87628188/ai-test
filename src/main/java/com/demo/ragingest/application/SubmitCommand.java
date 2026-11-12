package com.demo.ragingest.application;

public record SubmitCommand(
    String businessKey,
    String userId,
    String eventTime,
    String deviceId,
    String clientIp,
    String targetSystem,
    String action,
    int priority,
    Double failProbability,
    Boolean fatal
) {}
