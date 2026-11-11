package com.demo.ragingest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void exponentialBackoffCaps() {
        RetryPolicy p = new RetryPolicy();
        assertThat(p.nextDelayMs(0, 200, 5000)).isEqualTo(200);
        assertThat(p.nextDelayMs(1, 200, 5000)).isEqualTo(400);
        assertThat(p.nextDelayMs(2, 200, 5000)).isEqualTo(800);
        assertThat(p.nextDelayMs(10, 200, 5000)).isEqualTo(5000);
    }
}
