package io.github.topdanya33200_netizen.fightclock.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired after a player's fight ended. Fired on the thread that owns the player.
 */
public final class CombatEndEvent extends PlayerEvent {

    public enum Reason {
        /** The countdown ran out. */
        EXPIRED,
        /** The player died. */
        DEATH,
        /** The player left the server (and was punished unless the logout was excused). */
        LOGOUT,
        /** An admin used {@code /combat untag}. */
        COMMAND
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Reason reason;

    public CombatEndEvent(Player player, Reason reason) {
        super(player);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
