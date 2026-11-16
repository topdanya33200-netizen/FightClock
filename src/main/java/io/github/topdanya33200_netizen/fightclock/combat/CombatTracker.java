package io.github.topdanya33200_netizen.fightclock.combat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Who is in a fight and until when. Pure bookkeeping without any Bukkit types, safe to use from
 * any thread: every change is a single atomic {@link ConcurrentHashMap#compute} call.
 *
 * <p>A fight is over once its expiry time has passed, even before {@link #expireIfDue} removes
 * it, so every query here looks at the clock rather than trusting the map alone.</p>
 */
public final class CombatTracker {

    /** Whether a tag started a new fight or extended a running one. */
    public enum TagResult {
        STARTED,
        REFRESHED
    }

    private final Map<UUID, Fight> fights = new ConcurrentHashMap<>();
    private final InstantSource clock;

    public CombatTracker(InstantSource clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    /**
     * Puts a player into a fight for {@code length}, or restarts the countdown of their current
     * one. A tag never shortens a fight: an admin tag for 5 seconds during a 15 second countdown
     * keeps the 15 seconds. A tag without an enemy keeps the enemy that is already known.
     */
    public TagResult tag(UUID player, @Nullable UUID enemyId, @Nullable String enemyName, Duration length) {
        Instant now = clock.instant();
        Instant expires = now.plus(length);
        TagResult[] result = new TagResult[1];
        fights.compute(player, (id, current) -> {
            if (current == null || current.isOver(now)) {
                result[0] = TagResult.STARTED;
                return new Fight(now, expires, length, enemyId, enemyName);
            }
            result[0] = TagResult.REFRESHED;
            boolean longer = expires.isAfter(current.expires());
            Instant newExpiry = longer ? expires : current.expires();
            Duration newLength = longer ? length : current.length();
            boolean newEnemy = enemyName != null;
            return new Fight(current.started(), newExpiry, newLength,
                    newEnemy ? enemyId : current.enemyId(), newEnemy ? enemyName : current.enemyName());
        });
        return result[0];
    }

    /** The player's fight, if it is still running. */
    public Optional<Fight> fight(UUID player) {
        Fight fight = fights.get(player);
        if (fight == null || fight.isOver(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(fight);
    }

    public boolean inFight(UUID player) {
        return fight(player).isPresent();
    }

    /** Ends the fight right away, returning it if it was still running. */
    public Optional<Fight> end(UUID player) {
        Fight removed = fights.remove(player);
        if (removed == null || removed.isOver(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(removed);
    }

    /**
     * Removes the fight if its countdown has run out. Atomic, so a hit landing at the same moment
     * either refreshes the fight before this check or starts a new one after it.
     *
     * @return {@code true} if a finished fight was removed
     */
    public boolean expireIfDue(UUID player) {
        Instant now = clock.instant();
        boolean[] expired = new boolean[1];
        fights.computeIfPresent(player, (id, fight) -> {
            if (fight.isOver(now)) {
                expired[0] = true;
                return null;
            }
            return fight;
        });
        return expired[0];
    }

    /** Running fights, for listings and placeholders. */
    public Map<UUID, Fight> running() {
        Instant now = clock.instant();
        Map<UUID, Fight> result = new HashMap<>();
        fights.forEach((id, fight) -> {
            if (!fight.isOver(now)) {
                result.put(id, fight);
            }
        });
        return result;
    }

    public int count() {
        return running().size();
    }

    public void clear() {
        fights.clear();
    }
}
