package com.demo.ragingest.support;

import com.demo.ragingest.application.SubmitCommand;

public final class TestEvents {

    private TestEvents() {}

    public static SubmitCommand cmd(
        String businessKey,
        int priority,
        Double failProbability,
        Boolean fatal
    ) {
        return new SubmitCommand(
            businessKey,
            "u10086",
            "2026-11-12T12:00:00Z",
            "ios-xxx",
            "203.0.113.10",
            "payment-gateway",
            "login",
            priority,
            failProbability,
            fatal
        );
    }

    public static String jsonBody(String businessKey, int priority, Double failProbability) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"businessKey\":\"").append(businessKey).append("\",");
        sb.append("\"userId\":\"u10086\",");
        sb.append("\"eventTime\":\"2026-11-12T12:00:00Z\",");
        sb.append("\"deviceId\":\"ios-xxx\",");
        sb.append("\"clientIp\":\"203.0.113.10\",");
        sb.append("\"targetSystem\":\"payment-gateway\",");
        sb.append("\"action\":\"login\",");
        sb.append("\"priority\":").append(priority);
        if (failProbability != null) {
            sb.append(",\"failProbability\":").append(failProbability);
        }
        sb.append('}');
        return sb.toString();
    }
}
