package com.example.aigateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerOpenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(4)
            .minimumNumberOfCalls(2)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .permittedNumberOfCallsInHalfOpenState(2)
            .build();
        circuitBreaker = CircuitBreaker.of("test-circuit-breaker", config);
    }

    @Test
    void testCircuitBreakerInitiallyClosed() {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void testCircuitBreakerTransitionsToOpen() {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        
        try {
            circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Failure 1"); });
        } catch (RuntimeException e) {}
        
        try {
            circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Failure 2"); });
        } catch (RuntimeException e) {}
        
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        
        try {
            circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Failure 3"); });
        } catch (RuntimeException e) {}
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void testCircuitBreakerRejectsRequestsWhenOpen() {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Failure"); });
            } catch (RuntimeException e) {}
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        assertThrows(CircuitBreakerOpenException.class, () -> {
            circuitBreaker.executeRunnable(() -> {});
        });
    }

    @Test
    void testCircuitBreakerTransitionsToHalfOpen() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Failure"); });
            } catch (RuntimeException e) {}
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        Thread.sleep(150);
        
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
    }

    @Test
    void testCircuitBreakerReturnsToClosedAfterSuccessfulProbe() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Failure"); });
            } catch (RuntimeException e) {}
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        Thread.sleep(150);
        
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        
        circuitBreaker.executeRunnable(() -> {});
        circuitBreaker.executeRunnable(() -> {});
        
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void testCircuitBreakerStaysOpenAfterFailedProbe() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Failure"); });
            } catch (RuntimeException e) {}
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        Thread.sleep(150);
        
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        
        try {
            circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Probe failure"); });
        } catch (RuntimeException e) {}
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void testCircuitBreakerMetrics() {
        AtomicInteger successCount = new AtomicInteger(0);
        
        circuitBreaker.executeRunnable(successCount::incrementAndGet);
        circuitBreaker.executeRunnable(successCount::incrementAndGet);
        
        try {
            circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Failure"); });
        } catch (RuntimeException e) {}
        
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        
        assertEquals(2, metrics.getNumberOfSuccessfulCalls());
        assertEquals(1, metrics.getNumberOfFailedCalls());
        
        double failureRate = (double) metrics.getNumberOfFailedCalls() / 
                             (metrics.getNumberOfSuccessfulCalls() + metrics.getNumberOfFailedCalls()) * 100;
        assertEquals(33.33, failureRate, 0.01);
    }

    @Test
    void testCircuitBreakerDoesNotOpenWithPartialFailures() {
        circuitBreaker.executeRunnable(() -> {});
        circuitBreaker.executeRunnable(() -> {});
        
        try {
            circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Failure"); });
        } catch (RuntimeException e) {}
        
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void testCircuitBreakerAllowsPermittedCallsInHalfOpen() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.executeRunnable(() -> { throw new RuntimeException("Failure"); });
            } catch (RuntimeException e) {}
        }
        
        Thread.sleep(150);
        
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        
        circuitBreaker.executeRunnable(() -> {});
        circuitBreaker.executeRunnable(() -> {});
        
        assertThrows(CircuitBreakerOpenException.class, () -> {
            circuitBreaker.executeRunnable(() -> {});
        });
    }
}