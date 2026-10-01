package dev.frostguard.tasks.combat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class BearFrameStream<T> {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    record Snapshot<T>(
            long sequence,
            Instant capturedAt,
            T frame,
            BearNavigationPolicy.Screen screen) {

        boolean isFresh(Clock clock, Duration maximumAge) {
            return !capturedAt.plus(maximumAge).isBefore(clock.instant());
        }
    }

    @FunctionalInterface
    interface CaptureRecovery {
        boolean recover(RuntimeException failure, int failedAttempt);
    }

    private final Supplier<T> source;
    private final Function<T, BearNavigationPolicy.Screen> classifier;
    private final BooleanSupplier interrupted;
    private final CaptureRecovery recovery;
    private final int captureAttempts;
    private final Clock clock;
    private long sequence;

    BearFrameStream(
            Supplier<T> source,
            Function<T, BearNavigationPolicy.Screen> classifier,
            BooleanSupplier interrupted) {
        this(source, classifier, interrupted, (failure, attempt) -> false, 1, Clock.systemUTC());
    }

    BearFrameStream(
            Supplier<T> source,
            Function<T, BearNavigationPolicy.Screen> classifier,
            BooleanSupplier interrupted,
            CaptureRecovery recovery,
            int captureAttempts,
            Clock clock) {
        this.source = Objects.requireNonNull(source, "source");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.interrupted = Objects.requireNonNull(interrupted, "interrupted");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        if (captureAttempts < 1) {
            throw new IllegalArgumentException("captureAttempts must be positive");
        }
        this.captureAttempts = captureAttempts;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Snapshot<T> next() {
        T frame = capture();
        return new Snapshot<>(++sequence, clock.instant(), frame, classifier.apply(frame));
    }

    Snapshot<T> nextUnclassified() {
        T frame = capture();
        return new Snapshot<>(++sequence, clock.instant(), frame, BearNavigationPolicy.Screen.UNKNOWN);
    }

    Optional<Snapshot<T>> awaitAfter(
            long earlierSequence,
            Duration timeout,
            Predicate<Snapshot<T>> predicate) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && !interrupted.getAsBoolean()) {
            Snapshot<T> frame = next();
            if (frame.sequence() > earlierSequence && predicate.test(frame)) {
                return Optional.of(frame);
            }
            pauseBetweenCaptures();
        }
        return Optional.empty();
    }

    boolean interrupted() {
        return interrupted.getAsBoolean();
    }

    boolean isFresh(Snapshot<T> snapshot, Duration maximumAge) {
        return snapshot != null && snapshot.isFresh(clock, maximumAge);
    }

    private T capture() {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= captureAttempts; attempt++) {
            if (interrupted.getAsBoolean()) {
                throw new CaptureInterruptedException();
            }
            try {
                return Objects.requireNonNull(source.get(), "captured frame");
            } catch (RuntimeException failure) {
                lastFailure = failure;
                if (attempt == captureAttempts || !recovery.recover(failure, attempt)) {
                    throw failure;
                }
            }
        }
        throw Objects.requireNonNull(lastFailure, "capture failure");
    }

    private void pauseBetweenCaptures() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException interruptedFailure) {
            Thread.currentThread().interrupt();
            throw new CaptureInterruptedException();
        }
    }

    static final class CaptureInterruptedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
