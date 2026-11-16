package io.github.topdanya33200_netizen.fightclock.combat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One player's current fight.
 *
 * @param started   when the fight began
 * @param expires   when the player may log out again
 * @param length    the countdown length the last hit started, used for the bossbar progress
 * @param enemyId   the last player they fought, if any (admins can start a fight without one)
 * @param enemyName that player's name, kept so it can be shown after they log out
 */
public record Fight(Instant started, Instant expires, Duration length, @Nullable UUID enemyId, @Nullable String enemyName) {

    public Duration remaining(Instant now) {
        Duration left = Duration.between(now, expires);
        return left.isNegative() ? Duration.ZERO : left;
    }

    public boolean isOver(Instant now) {
        return !now.isBefore(expires);
    }

    /** Share of the countdown that is left, from 1 right after a hit down to 0. */
    public float progress(Instant now) {
        long total = length.toMillis();
        if (total <= 0) {
            return 0f;
        }
        double share = (double) remaining(now).toMillis() / total;
        return (float) Math.max(0, Math.min(1, share));
    }

    public boolean hasEnemy() {
        return enemyName != null;
    }
}
