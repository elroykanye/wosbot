package dev.frostguard.tasks.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BearFrameStreamTest {

    @Test
    void transientCaptureFailuresRecoverInsideTheSameStream() {
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger recoveries = new AtomicInteger();
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
        BearFrameStream<BearNavigationPolicy.Screen> stream = new BearFrameStream<>(
                () -> {
                    if (captures.incrementAndGet() < 3) {
                        throw new IllegalStateException("temporary screenshot failure");
                    }
                    return BearNavigationPolicy.Screen.WORLD;
                },
                screen -> screen,
                () -> false,
                (failure, attempt) -> {
                    recoveries.incrementAndGet();
                    return true;
                },
                3,
                clock);

        BearFrameStream.Snapshot<BearNavigationPolicy.Screen> snapshot = stream.next();

        assertEquals(3, captures.get());
        assertEquals(2, recoveries.get());
        assertEquals(1, snapshot.sequence());
        assertEquals(clock.instant(), snapshot.capturedAt());
        assertEquals(BearNavigationPolicy.Screen.WORLD, snapshot.screen());
    }

    @Test
    void persistentCaptureFailurePreservesTheOriginalCauseAndCreatesNoFrame() {
        AtomicInteger captures = new AtomicInteger();
        RuntimeException original = new RuntimeException("adb screenshot timed out");
        BearFrameStream<String> stream = new BearFrameStream<>(
                () -> {
                    captures.incrementAndGet();
                    throw original;
                },
                ignored -> BearNavigationPolicy.Screen.UNKNOWN,
                () -> false,
                (failure, attempt) -> true,
                3,
                Clock.systemUTC());

        RuntimeException thrown = assertThrows(RuntimeException.class, stream::next);

        assertSame(original, thrown);
        assertEquals(3, captures.get());
    }

    @Test
    void transitionPollingIsPacedInsteadOfHammeringCapture() {
        AtomicInteger captures = new AtomicInteger();
        BearFrameStream<String> stream = new BearFrameStream<>(
                () -> "frame-" + captures.incrementAndGet(),
                ignored -> BearNavigationPolicy.Screen.TRANSITIONING,
                () -> false);

        stream.awaitAfter(0, Duration.ofMillis(130), ignored -> false);

        assertTrue(captures.get() >= 2);
        assertTrue(captures.get() <= 4, "50ms polling should bound screenshot pressure");
    }

    @Test
    void staleAuthorizingFrameCannotTap() {
        AtomicInteger taps = new AtomicInteger();
        Instant now = Instant.parse("2026-10-01T10:00:10Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        BearFrameStream<String> stream = new BearFrameStream<>(
                () -> "world",
                ignored -> BearNavigationPolicy.Screen.WORLD,
                () -> false,
                (failure, attempt) -> false,
                1,
                clock);
        BearVerifiedActionExecutor<String> executor = new BearVerifiedActionExecutor<>(stream, 1);
        BearFrameStream.Snapshot<String> stale = new BearFrameStream.Snapshot<>(
                1,
                now.minusSeconds(3),
                "active-icon",
                BearNavigationPolicy.Screen.WORLD_ACTIVE_BEAR_ICON_READY);

        BearVerifiedActionExecutor.Outcome outcome = executor.tapWithOneVerifiedRetry(
                stale,
                taps::incrementAndGet,
                frame -> frame.screen() == BearNavigationPolicy.Screen.WORLD_AT_BEAR,
                frame -> frame.screen() == BearNavigationPolicy.Screen.WORLD_ACTIVE_BEAR_ICON_READY);

        assertEquals(BearVerifiedActionExecutor.Outcome.STALE_AUTHORIZATION, outcome);
        assertEquals(0, taps.get());
    }

    @Test
    void everyObservationHasASequenceAndPostconditionsUseALaterFrame() {
        Deque<BearNavigationPolicy.Screen> frames = new ArrayDeque<>();
        frames.add(BearNavigationPolicy.Screen.TRANSITIONING);
        frames.add(BearNavigationPolicy.Screen.ALLIANCE_MENU);
        BearFrameStream<BearNavigationPolicy.Screen> stream = new BearFrameStream<>(
                frames::removeFirst,
                screen -> screen,
                () -> false);

        BearFrameStream.Snapshot<BearNavigationPolicy.Screen> first = stream.next();
        BearFrameStream.Snapshot<BearNavigationPolicy.Screen> destination = stream.awaitAfter(
                first.sequence(),
                Duration.ofMillis(100),
                frame -> frame.screen() == BearNavigationPolicy.Screen.ALLIANCE_MENU).orElseThrow();

        assertTrue(destination.sequence() > first.sequence());
        assertEquals(BearNavigationPolicy.Screen.ALLIANCE_MENU, destination.screen());
    }

    @Test
    void interruptedPostconditionPollCannotCauseASecondTap() {
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicInteger taps = new AtomicInteger();
        BearFrameStream<BearNavigationPolicy.Screen> stream = new BearFrameStream<>(
                () -> {
                    interrupted.set(true);
                    return BearNavigationPolicy.Screen.WORLD_ACTIVE_BEAR_ICON_READY;
                },
                screen -> screen,
                interrupted::get);
        BearFrameStream.Snapshot<BearNavigationPolicy.Screen> source = new BearFrameStream.Snapshot<>(
                1, Instant.now(), BearNavigationPolicy.Screen.WORLD_ACTIVE_BEAR_ICON_READY,
                BearNavigationPolicy.Screen.WORLD_ACTIVE_BEAR_ICON_READY);
        BearVerifiedActionExecutor<BearNavigationPolicy.Screen> executor =
                new BearVerifiedActionExecutor<>(stream, 2);

        BearVerifiedActionExecutor.Outcome outcome = executor.tapWithOneVerifiedRetry(
                source,
                taps::incrementAndGet,
                frame -> frame.screen() == BearNavigationPolicy.Screen.WORLD_AT_BEAR,
                frame -> frame.screen() == BearNavigationPolicy.Screen.WORLD_ACTIVE_BEAR_ICON_READY);

        assertEquals(BearVerifiedActionExecutor.Outcome.INTERRUPTED, outcome);
        assertEquals(1, taps.get());
    }

    @Test
    void secondTapRequiresLaterFrameProofThatTheSameTargetIsStillPresent() {
        Deque<BearNavigationPolicy.Screen> frames = new ArrayDeque<>();
        frames.add(BearNavigationPolicy.Screen.TRANSITIONING);
        frames.add(BearNavigationPolicy.Screen.WORLD_ACTIVE_BEAR_ICON_READY);
        frames.add(BearNavigationPolicy.Screen.WORLD_AT_BEAR);
        AtomicInteger taps = new AtomicInteger();
        BearFrameStream<BearNavigationPolicy.Screen> stream = new BearFrameStream<>(
                frames::removeFirst,
                screen -> screen,
                () -> false);
        BearFrameStream.Snapshot<BearNavigationPolicy.Screen> source = new BearFrameStream.Snapshot<>(
                1, Instant.now(), BearNavigationPolicy.Screen.WORLD_ACTIVE_BEAR_ICON_READY,
                BearNavigationPolicy.Screen.WORLD_ACTIVE_BEAR_ICON_READY);
        BearVerifiedActionExecutor<BearNavigationPolicy.Screen> executor =
                new BearVerifiedActionExecutor<>(stream, 2);

        BearVerifiedActionExecutor.Outcome outcome = executor.tapWithOneVerifiedRetry(
                source,
                taps::incrementAndGet,
                frame -> frame.screen() == BearNavigationPolicy.Screen.WORLD_AT_BEAR,
                frame -> frame.screen() == BearNavigationPolicy.Screen.WORLD_ACTIVE_BEAR_ICON_READY);

        assertEquals(BearVerifiedActionExecutor.Outcome.CONFIRMED, outcome);
        assertEquals(2, taps.get());
    }
}
