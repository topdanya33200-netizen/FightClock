package io.github.topdanya33200_netizen.fightclock.api;

import java.time.Duration;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

/**
 * Fired before a player enters a fight or has their countdown restarted. Cancel it to keep the
 * player out of the fight, for example for teammates or players in a duel arena.
 *
 * <p>Fired on the thread that owns the player (the main thread on Paper, the player's region
 * thread on Folia).</p>
 */
public final class CombatTagEvent extends PlayerEvent implements Cancellable {

    public enum Cause {
        /** The player was hit by another player. */
        HIT,
        /** The player hit another player. */
        ATTACK,
        /** An admin used {@code /combat tag}. */
        COMMAND
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final @Nullable Player enemy;
    private final Cause cause;
    private final boolean alreadyInFight;
    private Duration duration;
    private boolean cancelled;

    public CombatTagEvent(Player player, @Nullable Player enemy, Cause cause, Duration duration, boolean alreadyInFight) {
        super(player);
        this.enemy = enemy;
        this.cause = cause;
        this.duration = duration;
        this.alreadyInFight = alreadyInFight;
    }

    /** The other player, or {@code null} when an admin started the fight. */
    public @Nullable Player getEnemy() {
        return enemy;
    }

    public Cause getCause() {
        return cause;
    }

    /** {@code true} if this only restarts the countdown of a running fight. */
    public boolean isAlreadyInFight() {
        return alreadyInFight;
    }

    public Duration getDuration() {
        return duration;
    }

    /** Changes how long this fight lasts from now on. */
    public void setDuration(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        this.duration = duration;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
