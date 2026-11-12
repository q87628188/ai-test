package com.demo.ragingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 风险事件异步摄取引擎启动类（H2 真相源 + 有界优先队列 + 固定 Worker）。 */
@SpringBootApplication
public class RagDocAsyncIngestApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagDocAsyncIngestApplication.class, args);
    }
}
