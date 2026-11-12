package com.demo.ragingest.application;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * 受理门禁：优雅停机时关闭，提交路径据此返回 503 NOT_ACCEPTING。
 */
@Component
public class AcceptingGate {

    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public boolean isAccepting() {
        return accepting.get();
    }

    public void stopAccepting() {
        accepting.set(false);
    }

    /** 测试用：模拟重启后重新打开受理。 */
    public void resetAcceptingForTest() {
        accepting.set(true);
    }
}
