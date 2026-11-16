package io.github.topdanya33200_netizen.fightclock.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatTrackerTest {

    /** A clock the test moves by hand. */
    static final class ManualClock implements InstantSource {
        private Instant now = Instant.parse("2026-11-16T12:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private static final Duration FIFTEEN = Duration.ofSeconds(15);

    private final ManualClock clock = new ManualClock();
    private final CombatTracker tracker = new CombatTracker(clock);
    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final UUID herobrine = UUID.randomUUID();

    @Test
    void firstHitStartsAFightThatEndsAfterTheCountdown() {
        assertEquals(CombatTracker.TagResult.STARTED, tracker.tag(steve, alex, "Alex", FIFTEEN));
        assertTrue(tracker.inFight(steve));
        assertEquals("Alex", tracker.fight(steve).orElseThrow().enemyName());

        clock.advance(Duration.ofSeconds(14));
        assertTrue(tracker.inFight(steve));
        assertEquals(Duration.ofSeconds(1), tracker.fight(steve).orElseThrow().remaining(clock.instant()));

        clock.advance(Duration.ofSeconds(1));
        assertFalse(tracker.inFight(steve), "the fight is over as soon as the countdown reaches zero");
    }

    @Test
    void everyHitRestartsTheCountdown() {
        tracker.tag(steve, alex, "Alex", FIFTEEN);
        clock.advance(Duration.ofSeconds(10));
        assertEquals(CombatTracker.TagResult.REFRESHED, tracker.tag(steve, alex, "Alex", FIFTEEN));

        clock.advance(Duration.ofSeconds(10));
        assertTrue(tracker.inFight(steve), "20s after the first hit but only 10s after the last one");
        assertEquals(Instant.parse("2026-11-16T12:00:00Z"), tracker.fight(steve).orElseThrow().started());
    }

    @Test
    void aShortTagNeverShortensARunningFight() {
        tracker.tag(steve, alex, "Alex", FIFTEEN);
        tracker.tag(steve, null, null, Duration.ofSeconds(3));

        clock.advance(Duration.ofSeconds(10));
        assertTrue(tracker.inFight(steve));
        assertEquals("Alex", tracker.fight(steve).orElseThrow().enemyName(), "a tag without an enemy keeps the known one");
    }

    @Test
    void theLastEnemyIsRemembered() {
        tracker.tag(steve, alex, "Alex", FIFTEEN);
        tracker.tag(steve, herobrine, "Herobrine", FIFTEEN);
        Fight fight = tracker.fight(steve).orElseThrow();
        assertEquals(herobrine, fight.enemyId());
        assertEquals("Herobrine", fight.enemyName());
    }

    @Test
    void anAdminTagHasNoEnemy() {
        tracker.tag(steve, null, null, FIFTEEN);
        Fight fight = tracker.fight(steve).orElseThrow();
        assertFalse(fight.hasEnemy());
        assertNull(fight.enemyId());
    }

    @Test
    void aNewFightStartsAfterTheOldOneEnded() {
        tracker.tag(steve, alex, "Alex", FIFTEEN);
        clock.advance(Duration.ofSeconds(20));
        assertEquals(CombatTracker.TagResult.STARTED, tracker.tag(steve, alex, "Alex", FIFTEEN));
        assertEquals(clock.instant(), tracker.fight(steve).orElseThrow().started());
    }

    @Test
    void expireIfDueOnlyRemovesFinishedFights() {
        tracker.tag(steve, alex, "Alex", FIFTEEN);
        assertFalse(tracker.expireIfDue(steve));
        clock.advance(FIFTEEN);
        assertTrue(tracker.expireIfDue(steve));
        assertFalse(tracker.expireIfDue(steve), "already removed");
    }

    @Test
    void endReturnsOnlyRunningFights() {
        tracker.tag(steve, alex, "Alex", FIFTEEN);
        assertTrue(tracker.end(steve).isPresent());
        assertFalse(tracker.inFight(steve));

        tracker.tag(alex, steve, "Steve", FIFTEEN);
        clock.advance(Duration.ofSeconds(30));
        assertTrue(tracker.end(alex).isEmpty(), "a fight that already timed out is not reported as ended now");
    }

    @Test
    void runningListsOnlyActiveFights() {
        tracker.tag(steve, alex, "Alex", FIFTEEN);
        tracker.tag(alex, steve, "Steve", Duration.ofSeconds(5));
        assertEquals(2, tracker.count());
        clock.advance(Duration.ofSeconds(6));
        assertEquals(1, tracker.count());
        assertTrue(tracker.running().containsKey(steve));
    }

    @Test
    void progressDrainsFromOneToZero() {
        tracker.tag(steve, alex, "Alex", Duration.ofSeconds(10));
        Fight fight = tracker.fight(steve).orElseThrow();
        assertEquals(1f, fight.progress(clock.instant()), 1e-6);
        clock.advance(Duration.ofSeconds(4));
        assertEquals(0.6f, fight.progress(clock.instant()), 1e-6);
        clock.advance(Duration.ofSeconds(20));
        assertEquals(0f, fight.progress(clock.instant()), 1e-6);
        assertEquals(Duration.ZERO, fight.remaining(clock.instant()));
    }
}
