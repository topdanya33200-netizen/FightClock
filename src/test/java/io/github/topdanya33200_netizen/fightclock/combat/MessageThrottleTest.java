package io.github.topdanya33200_netizen.fightclock.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageThrottleTest {

    private final CombatTrackerTest.ManualClock clock = new CombatTrackerTest.ManualClock();
    private final MessageThrottle throttle = new MessageThrottle(clock, Duration.ofMillis(1500));
    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();

    @Test
    void sameWarningOnlyOncePerInterval() {
        assertTrue(throttle.allow(steve, "portal"));
        assertFalse(throttle.allow(steve, "portal"));
        clock.advance(Duration.ofMillis(1499));
        assertFalse(throttle.allow(steve, "portal"));
        clock.advance(Duration.ofMillis(1));
        assertTrue(throttle.allow(steve, "portal"));
    }

    @Test
    void differentWarningsAndPlayersAreIndependent() {
        assertTrue(throttle.allow(steve, "portal"));
        assertTrue(throttle.allow(steve, "elytra"));
        assertTrue(throttle.allow(alex, "portal"));
    }

    @Test
    void forgettingAPlayerResetsTheirWarnings() {
        assertTrue(throttle.allow(steve, "portal"));
        throttle.forget(steve);
        assertTrue(throttle.allow(steve, "portal"));
    }
}
