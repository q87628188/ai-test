package com.demo.ragingest.application;

/**
 * 提交命令（API DTO → 应用层）。
 * {@code failProbability}/{@code fatal} 仅用于模拟器可控失败，会写入 payload_json。
 */
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
