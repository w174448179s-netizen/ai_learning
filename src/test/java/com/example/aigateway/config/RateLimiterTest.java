package com.example.aigateway.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(10)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(10))
            .build();
        rateLimiter = RateLimiter.of("test-rate-limiter", config);
    }

    @Test
    void testRateLimiterAllowsWithinLimit() {
        int successCount = 0;
        
        for (int i = 0; i < 10; i++) {
            try {
                rateLimiter.executeRunnable(() -> successCount++);
            } catch (RequestNotPermitted e) {
                // 不应该进入这里
            }
        }
        
        assertEquals(10, successCount);
    }

    @Test
    void testRateLimiterBlocksExcessRequests() {
        int successCount = 0;
        int blockCount = 0;
        
        for (int i = 0; i < 15; i++) {
            try {
                rateLimiter.executeRunnable(() -> successCount++);
            } catch (RequestNotPermitted e) {
                blockCount++;
            }
        }
        
        assertEquals(10, successCount);
        assertEquals(5, blockCount);
    }

    @Test
    void testRateLimiterRefreshesAfterPeriod() throws InterruptedException {
        int firstPeriodSuccess = 0;
        
        for (int i = 0; i < 10; i++) {
            rateLimiter.executeRunnable(() -> firstPeriodSuccess++);
        }
        
        assertEquals(10, firstPeriodSuccess);
        
        Thread.sleep(1100);
        
        int secondPeriodSuccess = 0;
        for (int i = 0; i < 10; i++) {
            rateLimiter.executeRunnable(() -> secondPeriodSuccess++);
        }
        
        assertEquals(10, secondPeriodSuccess);
    }

    @Test
    void testRateLimiterConcurrentRequests() throws InterruptedException {
        int threadCount = 20;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger blockCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    rateLimiter.executeRunnable(() -> successCount.incrementAndGet());
                } catch (RequestNotPermitted e) {
                    blockCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals(10, successCount.get());
        assertEquals(10, blockCount.get());
    }

    @Test
    void testRateLimiterMetrics() {
        for (int i = 0; i < 10; i++) {
            rateLimiter.executeRunnable(() -> {});
        }
        
        RateLimiter.Metrics metrics = rateLimiter.getMetrics();
        
        assertEquals(10, metrics.getNumberOfSuccessfulCalls());
        
        try {
            rateLimiter.executeRunnable(() -> {});
            fail("Should throw RequestNotPermitted");
        } catch (RequestNotPermitted e) {
            assertEquals(1, metrics.getNumberOfFailedCalls());
        }
    }

    @Test
    void testRateLimiterTimeout() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofSeconds(10))
            .timeoutDuration(Duration.ofMillis(50))
            .build();
        RateLimiter slowRateLimiter = RateLimiter.of("slow-rate-limiter", config);
        
        slowRateLimiter.executeRunnable(() -> {});
        
        long startTime = System.currentTimeMillis();
        try {
            slowRateLimiter.executeRunnable(() -> {});
            fail("Should throw RequestNotPermitted due to timeout");
        } catch (RequestNotPermitted e) {
            long elapsed = System.currentTimeMillis() - startTime;
            assertTrue(elapsed >= 50, "Timeout should be at least 50ms, actual: " + elapsed);
        }
    }
}