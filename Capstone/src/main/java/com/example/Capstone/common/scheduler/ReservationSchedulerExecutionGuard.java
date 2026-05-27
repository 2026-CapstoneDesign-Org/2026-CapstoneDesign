package com.example.Capstone.common.scheduler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ReservationSchedulerExecutionGuard {

    private final Set<String> runningKeys = ConcurrentHashMap.newKeySet();

    public boolean tryStart(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return runningKeys.add(key);
    }

    public void finish(String key) {
        if (key != null) {
            runningKeys.remove(key);
        }
    }
}
