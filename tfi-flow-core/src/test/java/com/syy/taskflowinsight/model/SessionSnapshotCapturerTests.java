package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageSeverity;
import com.syy.taskflowinsight.enums.SessionStatus;
import com.syy.taskflowinsight.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionSnapshotCapturerTests {

    @Test
    void captureAllowsPreviouslyFailedRootWithCompletedSession() {
        Session session = Session.create("completed-session");
        session.getRootTask().fail();
        session.complete();

        SessionExportSnapshot snapshot = capture(session);

        assertThat(snapshot.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(snapshot.root().status()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void captureAllowsPreviouslyCompletedRootWithErrorSession() {
        Session session = Session.create("error-session");
        session.getRootTask().complete();
        session.error("session failure");

        SessionExportSnapshot snapshot = capture(session);

        assertThat(snapshot.status()).isEqualTo(SessionStatus.ERROR);
        assertThat(snapshot.root().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(snapshot.root().messages()).extracting(SessionExportSnapshot.MessageSnapshot::content)
                .containsExactly("session failure");
    }

    @Test
    void captureFreezesCollectionsAndScalarState() {
        Session session = Session.create("frozen");
        TaskNode root = session.getRootTask();
        root.addInfo("before");
        root.addAttribute("answer", 42);
        root.addAttribute("nullable", null);
        root.addTag("stable");
        root.createChild("first");

        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        root.addInfo("after");
        root.addAttribute("answer", 43);
        root.addTag("late");
        root.createChild("second");

        assertThat(snapshot.root().messages())
                .extracting(SessionExportSnapshot.MessageSnapshot::content)
                .containsExactly("before");
        assertThat(snapshot.root().attributes())
                .containsEntry("answer", 42)
                .containsEntry("nullable", null);
        assertThat(snapshot.root().tags()).containsExactly("stable");
        assertThat(snapshot.root().children())
                .extracting(SessionExportSnapshot.TaskSnapshot::taskName)
                .containsExactly("first");
    }

    @Test
    void captureFreezesSessionNameFromTheRootSnapshot() {
        Session session = Session.create("  canonical-name  ");

        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);

        assertThat(snapshot.sessionName()).isEqualTo("canonical-name");
        assertThat(snapshot.sessionName()).isEqualTo(snapshot.root().taskName());
    }

    @Test
    void capturePreservesAValidBlankJvmThreadName() throws Exception {
        FutureTask<SessionExportSnapshot> capture = new FutureTask<>(() -> {
            Session session = Session.create("blank-thread");
            session.getRootTask().addInfo("message");
            return SessionExportSnapshot.capture(session);
        });
        Thread thread = new Thread(capture, "");

        thread.start();
        SessionExportSnapshot snapshot = capture.get(2, TimeUnit.SECONDS);

        assertThat(snapshot.threadName()).isEmpty();
        assertThat(snapshot.root().threadName()).isEmpty();
        assertThat(snapshot.root().messages()).singleElement()
                .extracting(SessionExportSnapshot.MessageSnapshot::threadName)
                .isEqualTo("");
    }

    @Test
    void clockIsSampledInsideWriteLockedCapture() {
        Session session = Session.create("clock");
        long captureMillis = System.currentTimeMillis();
        long captureNanos = afterTree(session);
        CountingClock clock = new CountingClock(captureMillis, captureNanos);

        SessionExportSnapshot snapshot = SessionSnapshotCapturer.capture(
                session, SessionExportSnapshot.Limits.defaults(), clock);

        assertThat(clock.millisCalls).hasValue(1);
        assertThat(clock.nanosCalls).hasValue(1);
        assertThat(snapshot.captureMillis()).isEqualTo(captureMillis);
        assertThat(snapshot.captureNanos()).isEqualTo(captureNanos);
    }

    @Test
    void runningDurationsUseExactlyTheCapturedNanos() {
        Session session = Session.create("duration");
        TaskNode root = session.getRootTask();
        long captureNanos = root.getCreatedNanos() + 5_432_100L;

        SessionExportSnapshot snapshot = SessionSnapshotCapturer.capture(
                session, SessionExportSnapshot.Limits.defaults(),
                fixedClock(System.currentTimeMillis(), captureNanos));

        assertThat(snapshot.durationNanos()).isNull();
        assertThat(snapshot.root().durationNanos()).isNull();
        assertThat(snapshot.root().selfDurationNanos()).isEqualTo(5_432_100L);
        assertThat(snapshot.root().selfDurationMillis()).isEqualTo(5L);
        assertThat(snapshot.root().accumulatedDurationNanos()).isEqualTo(5_432_100L);
    }

    @Test
    void captureEnforcesDepthAndNodeBudgetTogether() {
        Session session = Session.create("limits");
        TaskNode root = session.getRootTask();
        TaskNode first = root.createChild("first");
        first.createChild("hidden-grandchild");
        root.createChild("hidden-sibling");
        SessionExportSnapshot.Limits limits = new SessionExportSnapshot.Limits(1, 2, 100, 10_000);

        SessionExportSnapshot snapshot = capture(session, limits);

        assertThat(snapshot.statistics())
                .isEqualTo(new SessionExportSnapshot.Statistics(2, 1, 0));
        assertThat(snapshot.truncated()).isTrue();
        assertThat(snapshot.root().childrenTruncated()).isTrue();
        assertThat(snapshot.root().children())
                .singleElement()
                .satisfies(child -> {
                    assertThat(child.taskName()).isEqualTo("first");
                    assertThat(child.children()).isEmpty();
                    assertThat(child.childrenTruncated()).isTrue();
                });
    }

    @Test
    void captureRejectsPayloadEntryBudgetWithoutPartialSnapshot() {
        Session session = Session.create("payload");
        TaskNode root = session.getRootTask();
        root.addInfo("message");
        root.addAttribute("key", "value");
        root.addTag("tag");

        assertThat(capture(session, new SessionExportSnapshot.Limits(2, 10, 3, 10_000)))
                .isNotNull();
        assertThatThrownBy(() -> capture(
                session, new SessionExportSnapshot.Limits(2, 10, 2, 10_000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Export payload entry limit exceeded: 2");

        root.addInfo("after-failure");
        assertThat(capture(session, new SessionExportSnapshot.Limits(2, 10, 4, 10_000))
                .root().messages()).hasSize(2);
    }

    @Test
    void capturePreservesEveryExactScalarClassWithinTextBudget() {
        Session session = Session.create("scalars");
        TaskNode root = session.getRootTask();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("string", "value");
        values.put("boolean", true);
        values.put("character", 'x');
        values.put("byte", (byte) 1);
        values.put("short", (short) 2);
        values.put("integer", 3);
        values.put("long", 4L);
        values.put("float", 5.5F);
        values.put("double", 6.5D);
        values.put("bigInteger", new BigInteger("12345678901234567890"));
        values.put("bigDecimal", new BigDecimal("1234567890.0123456789"));
        values.forEach(root::addAttribute);
        root.addAttribute("floatNaN", Float.NaN);
        root.addAttribute("doubleInfinity", Double.NEGATIVE_INFINITY);

        Map<String, Object> frozen = SessionExportSnapshot.capture(session).root().attributes();

        for (Map.Entry<String, Object> expected : values.entrySet()) {
            assertThat(frozen.get(expected.getKey()))
                    .isEqualTo(expected.getValue())
                    .isExactlyInstanceOf(expected.getValue().getClass());
        }
        assertThat(frozen.get("floatNaN")).isEqualTo(new SessionExportSnapshot.NonFiniteNumber(
                SessionExportSnapshot.NumberKind.FLOAT, "NaN"));
        assertThat(frozen.get("doubleInfinity")).isEqualTo(new SessionExportSnapshot.NonFiniteNumber(
                SessionExportSnapshot.NumberKind.DOUBLE, "-Infinity"));
    }

    @Test
    void captureAcceptsTextEstimateAtLimitAndRejectsLimitPlusOneAtomically() {
        Session session = Session.create("text-limit");
        session.getRootTask().addAttribute("payload", "abcdef");
        SessionExportSnapshot baseline = SessionExportSnapshot.capture(session);
        long exact = SessionExportSnapshot.estimateTextCharacters(baseline);

        SessionExportSnapshot exactSnapshot = capture(
                session, new SessionExportSnapshot.Limits(2, 10, 10, exact));
        assertThat(SessionExportSnapshot.estimateTextCharacters(exactSnapshot)).isEqualTo(exact);
        assertThatThrownBy(() -> capture(
                session, new SessionExportSnapshot.Limits(2, 10, 10, exact - 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Export text character limit exceeded: " + (exact - 1));

        session.getRootTask().addTag("gate-released");
        assertThat(SessionExportSnapshot.capture(session).root().tags())
                .containsExactly("gate-released");
    }

    @Test
    void textEstimateUsesUtf16CodeUnitsAndCallbackFreeNumericBounds() {
        assertThat(SessionExportSnapshot.frozenValueTextLength("😀")).isEqualTo(2L);
        assertThat(SessionExportSnapshot.frozenValueTextLength("ab")).isEqualTo(2L);
        assertThat(SessionExportSnapshot.frozenValueTextLength(new BigInteger("99999999999999999999")))
                .isGreaterThanOrEqualTo(20L);
        assertThat(SessionExportSnapshot.frozenValueTextLength(new BigDecimal("9999999999.9999999999")))
                .isGreaterThanOrEqualTo(20L);

        Session emoji = Session.create("utf16");
        emoji.getRootTask().addAttribute("value", "😀");
        Session ascii = Session.create("utf16");
        ascii.getRootTask().addAttribute("value", "ab");
        assertThat(SessionExportSnapshot.estimateTextCharacters(SessionExportSnapshot.capture(emoji)))
                .isEqualTo(SessionExportSnapshot.estimateTextCharacters(
                        SessionExportSnapshot.capture(ascii)));
    }

    @Test
    void statisticsAndAccumulatedDurationsDescribeOnlyVisibleNodes() {
        Session session = Session.create("statistics");
        TaskNode root = session.getRootTask();
        root.addInfo("root-message");
        TaskNode first = root.createChild("first");
        first.addInfo("first-message");
        first.createChild("hidden").addInfo("hidden-message");
        root.createChild("second");

        SessionExportSnapshot snapshot = capture(
                session, new SessionExportSnapshot.Limits(1, 10, 10, 10_000));

        assertThat(snapshot.statistics())
                .isEqualTo(new SessionExportSnapshot.Statistics(3, 1, 2));
        long visibleChildren = snapshot.root().children().stream()
                .mapToLong(SessionExportSnapshot.TaskSnapshot::accumulatedDurationNanos)
                .sum();
        assertThat(snapshot.root().accumulatedDurationNanos())
                .isEqualTo(snapshot.root().selfDurationNanos() + visibleChildren);
        assertThat(snapshot.root().children().get(0).childrenTruncated()).isTrue();
    }

    @Test
    void captureBlocksMutationUntilTheFrozenTreeIsComplete() throws Exception {
        Session session = Session.create("blocked-mutation");
        TaskNode root = session.getRootTask();
        CountDownLatch clockEntered = new CountDownLatch(1);
        CountDownLatch releaseClock = new CountDownLatch(1);
        long captureNanos = afterTree(session);
        CaptureClock clock = new CaptureClock() {
            @Override
            public long currentTimeMillis() {
                return System.currentTimeMillis();
            }

            @Override
            public long nanoTime() {
                clockEntered.countDown();
                await(releaseClock);
                return captureNanos;
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SessionExportSnapshot> capture = executor.submit(() ->
                    SessionSnapshotCapturer.capture(
                            session, SessionExportSnapshot.Limits.defaults(), clock));
            assertThat(clockEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<?> mutation = executor.submit(() -> root.addInfo("late"));
            assertThatThrownBy(() -> mutation.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseClock.countDown();
            SessionExportSnapshot snapshot = capture.get(2, TimeUnit.SECONDS);
            mutation.get(2, TimeUnit.SECONDS);

            assertThat(snapshot.root().messages()).isEmpty();
            assertThat(root.getMessages()).extracting(Message::getContent).containsExactly("late");
        } finally {
            releaseClock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void captureNeverInvokesUnknownValueToStringOrContainerIteration() {
        Session session = Session.create("hostile");
        HostileIterable hostile = new HostileIterable();
        session.getRootTask().addAttribute("hostile", hostile);

        Object frozen = SessionExportSnapshot.capture(session).root().attributes().get("hostile");

        assertThat(frozen).isEqualTo(new SessionExportSnapshot.UnsupportedValue(
                HostileIterable.class.getName()));
    }

    @Test
    void captureFreezesL6MessageSeverityWithWireAndDisplayValues() {
        Session session = Session.create("severity");
        session.getRootTask().addDebug("debug");
        session.getRootTask().addWarn("warn");

        List<SessionExportSnapshot.MessageSnapshot> messages =
                SessionExportSnapshot.capture(session).root().messages();

        assertThat(messages).extracting(
                SessionExportSnapshot.MessageSnapshot::wireType,
                SessionExportSnapshot.MessageSnapshot::displayLabel,
                SessionExportSnapshot.MessageSnapshot::severity,
                SessionExportSnapshot.MessageSnapshot::content)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "核心指标", "核心指标", MessageSeverity.DEBUG, "debug"),
                        org.assertj.core.groups.Tuple.tuple(
                                "⚠️异常提示", "⚠️异常提示", MessageSeverity.WARN, "warn"));
    }

    @Test
    void hostileBigIntegerSubclassBecomesUnsupportedWithoutCallback() {
        Session session = Session.create("hostile-big-integer");
        session.getRootTask().addAttribute("value", new HostileBigInteger());

        assertThat(SessionExportSnapshot.capture(session).root().attributes().get("value"))
                .isEqualTo(new SessionExportSnapshot.UnsupportedValue(
                        HostileBigInteger.class.getName()));
    }

    @Test
    void hostileBigDecimalSubclassBecomesUnsupportedWithoutCallback() {
        Session session = Session.create("hostile-big-decimal");
        session.getRootTask().addAttribute("value", new HostileBigDecimal());

        assertThat(SessionExportSnapshot.capture(session).root().attributes().get("value"))
                .isEqualTo(new SessionExportSnapshot.UnsupportedValue(
                        HostileBigDecimal.class.getName()));
    }

    private static SessionExportSnapshot capture(Session session) {
        return SessionSnapshotCapturer.capture(
                session, SessionExportSnapshot.Limits.defaults(),
                fixedClock(System.currentTimeMillis(), afterTree(session)));
    }

    private static SessionExportSnapshot capture(
            Session session, SessionExportSnapshot.Limits limits) {
        return SessionSnapshotCapturer.capture(
                session, limits, fixedClock(System.currentTimeMillis(), afterTree(session)));
    }

    private static long afterTree(Session session) {
        return Math.max(System.nanoTime(), session.getRootTask().getCreatedNanos()) + 1_000_000L;
    }

    private static CaptureClock fixedClock(long millis, long nanos) {
        return new CaptureClock() {
            @Override
            public long currentTimeMillis() {
                return millis;
            }

            @Override
            public long nanoTime() {
                return nanos;
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static final class CountingClock implements CaptureClock {
        private final long millis;
        private final long nanos;
        private final AtomicInteger millisCalls = new AtomicInteger();
        private final AtomicInteger nanosCalls = new AtomicInteger();

        private CountingClock(long millis, long nanos) {
            this.millis = millis;
            this.nanos = nanos;
        }

        @Override
        public long currentTimeMillis() {
            millisCalls.incrementAndGet();
            return millis;
        }

        @Override
        public long nanoTime() {
            nanosCalls.incrementAndGet();
            return nanos;
        }
    }

    private static final class HostileIterable implements Iterable<Object> {
        @Override
        public Iterator<Object> iterator() {
            throw new AssertionError("must not iterate unsupported value");
        }

        @Override
        public String toString() {
            throw new AssertionError("must not stringify unsupported value");
        }
    }

    private static final class HostileBigInteger extends BigInteger {
        private HostileBigInteger() {
            super("1");
        }

        @Override
        public String toString() {
            throw new AssertionError("must not stringify hostile BigInteger");
        }
    }

    private static final class HostileBigDecimal extends BigDecimal {
        private HostileBigDecimal() {
            super("1.0");
        }

        @Override
        public String toString() {
            throw new AssertionError("must not stringify hostile BigDecimal");
        }
    }
}
