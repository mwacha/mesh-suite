package com.meshsuite.auth.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Bucket(int failures, Instant windowStart, Instant blockedUntil) {
    }

    private final ConcurrentHashMap<String, AtomicReference<Bucket>> buckets = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip, String email) {
        return isKeyBlocked("ip:" + ip) || isKeyBlocked("email:" + email);
    }

    public void recordFailure(String ip, String email) {
        recordFailureForKey("ip:" + ip);
        recordFailureForKey("email:" + email);
    }

    public void recordSuccess(String ip, String email) {
        buckets.remove("ip:" + ip);
        buckets.remove("email:" + email);
    }

    private boolean isKeyBlocked(String key) {
        AtomicReference<Bucket> ref = buckets.get(key);
        if (ref == null) {
            return false;
        }
        Bucket bucket = ref.get();
        return bucket.blockedUntil() != null && Instant.now().isBefore(bucket.blockedUntil());
    }

    private void recordFailureForKey(String key) {
        AtomicReference<Bucket> ref = buckets.computeIfAbsent(key, k -> new AtomicReference<>(new Bucket(0, Instant.now(), null)));
        ref.updateAndGet(bucket -> {
            Instant now = Instant.now();
            if (now.isAfter(bucket.windowStart().plus(WINDOW))) {
                bucket = new Bucket(0, now, null);
            }
            int failures = bucket.failures() + 1;
            Instant blockedUntil = failures >= MAX_ATTEMPTS ? now.plus(WINDOW) : null;
            return new Bucket(failures, bucket.windowStart(), blockedUntil);
        });
    }
}
