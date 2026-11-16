package io.github.topdanya33200_netizen.fightclock.api;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

/**
 * Fired when a player leaves the server during a fight, right before they are punished.
 * Cancel it to let them go unpunished, for example to spawn a stand-in NPC instead.
 *
 * <p>Not fired for excused kicks (see {@code logout.safe-kick-causes}). Fired on the thread that
 * owns the player, while the player is still online.</p>
 */
public final class CombatLogoutEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final @Nullable UUID enemyId;
    private final @Nullable String enemyName;
    private final PlayerQuitEvent.QuitReason quitReason;
    private boolean cancelled;

    public CombatLogoutEvent(Player player, @Nullable UUID enemyId, @Nullable String enemyName,
                             PlayerQuitEvent.QuitReason quitReason) {
        super(player);
        this.enemyId = enemyId;
        this.enemyName = enemyName;
        this.quitReason = quitReason;
    }

    /** The last player they fought, who will be credited with the kill. */
    public @Nullable UUID getEnemyId() {
        return enemyId;
    }

    public @Nullable String getEnemyName() {
        return enemyName;
    }

    public PlayerQuitEvent.QuitReason getQuitReason() {
        return quitReason;
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
