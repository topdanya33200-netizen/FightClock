package io.github.topdanya33200_netizen.fightclock.combat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps repeated warnings from flooding the chat. Standing in a portal or holding the jump key
 * with elytra fires an event every tick; the player only needs to read the warning once.
 */
public final class MessageThrottle {

    private record Key(UUID player, String message) {
    }

    private final Map<Key, Instant> lastSent = new ConcurrentHashMap<>();
    private final InstantSource clock;
    private final Duration interval;

    public MessageThrottle(InstantSource clock, Duration interval) {
        this.clock = clock;
        this.interval = interval;
    }

    /** {@code true} if this warning may be shown to the player now; remembers that it was. */
    public boolean allow(UUID player, String message) {
        Instant now = clock.instant();
        boolean[] allowed = new boolean[1];
        lastSent.compute(new Key(player, message), (key, last) -> {
            if (last == null || !now.isBefore(last.plus(interval))) {
                allowed[0] = true;
                return now;
            }
            return last;
        });
        return allowed[0];
    }

    public void forget(UUID player) {
        lastSent.keySet().removeIf(key -> key.player().equals(player));
    }
}
