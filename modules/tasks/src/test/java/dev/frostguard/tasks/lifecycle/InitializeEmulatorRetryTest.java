package dev.frostguard.tasks.lifecycle;

import dev.frostguard.engine.error.StopExecutionException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitializeEmulatorRetryTest {

    @Test
    void waitsForLaunchReadinessInsteadOfFirstAdbVisibility() {
        AtomicInteger readinessProbes = new AtomicInteger();
        AtomicInteger launchRequests = new AtomicInteger();
        AtomicInteger waits = new AtomicInteger();

        InitializeRoutine.awaitEmulatorRunning(
                () -> readinessProbes.incrementAndGet() >= 3,
                launchRequests::incrementAndGet,
                waits::incrementAndGet,
                () -> { });

        assertTrue(readinessProbes.get() >= 3);
        assertTrue(launchRequests.get() >= 2);
        assertTrue(waits.get() >= 2);
    }

    @Test
    void cancellationAfterStateProbePreventsLaunch() {
        AtomicInteger checks = new AtomicInteger();
        AtomicBoolean launched = new AtomicBoolean();

        assertThrows(StopExecutionException.class, () -> InitializeRoutine.awaitEmulatorRunning(
                () -> false,
                () -> launched.set(true),
                () -> { },
                () -> {
                    if (checks.incrementAndGet() == 2) {
                        throw new StopExecutionException("cancelled");
                    }
                }));

        assertFalse(launched.get());
    }

    @Test
    void cancellationAfterLaunchPreventsRetryDelay() {
        AtomicInteger checks = new AtomicInteger();
        AtomicBoolean launched = new AtomicBoolean();
        AtomicBoolean waited = new AtomicBoolean();

        assertThrows(StopExecutionException.class, () -> InitializeRoutine.awaitEmulatorRunning(
                () -> false,
                () -> launched.set(true),
                () -> waited.set(true),
                () -> {
                    if (checks.incrementAndGet() == 3) {
                        throw new StopExecutionException("cancelled");
                    }
                }));

        assertTrue(launched.get());
        assertFalse(waited.get());
    }
}
