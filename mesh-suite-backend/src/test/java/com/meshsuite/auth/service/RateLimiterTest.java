package com.meshsuite.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.auth.service.RateLimiter;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void blocksAfterFiveFailuresFromSameIp() {
        RateLimiter rateLimiter = new RateLimiter();

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.isBlocked("1.2.3.4", "user" + i + "@example.com")).isFalse();
            rateLimiter.recordFailure("1.2.3.4", "user" + i + "@example.com");
        }

        assertThat(rateLimiter.isBlocked("1.2.3.4", "another@example.com")).isTrue();
    }

    @Test
    void blocksAfterFiveFailuresForSameEmailFromDifferentIps() {
        RateLimiter rateLimiter = new RateLimiter();

        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure("1.2.3." + i, "marina@aurora.com.br");
        }

        assertThat(rateLimiter.isBlocked("9.9.9.9", "marina@aurora.com.br")).isTrue();
    }

    @Test
    void successClearsFailureCountForThatIpAndEmail() {
        RateLimiter rateLimiter = new RateLimiter();

        rateLimiter.recordFailure("1.2.3.4", "marina@aurora.com.br");
        rateLimiter.recordFailure("1.2.3.4", "marina@aurora.com.br");
        rateLimiter.recordSuccess("1.2.3.4", "marina@aurora.com.br");

        assertThat(rateLimiter.isBlocked("1.2.3.4", "marina@aurora.com.br")).isFalse();
    }
}
