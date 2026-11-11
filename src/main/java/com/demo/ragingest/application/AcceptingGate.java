package com.demo.ragingest.application;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class AcceptingGate {

    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public boolean isAccepting() {
        return accepting.get();
    }

    public void stopAccepting() {
        accepting.set(false);
    }

    /** Test/recovery helper to reopen acceptance after simulated restart. */
    public void resetAcceptingForTest() {
        accepting.set(true);
    }
}
