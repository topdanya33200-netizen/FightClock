package io.github.topdanya33200_netizen.fightclock.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Thin wrapper over Paper's region-aware schedulers, so the plugin runs on both Paper and Folia.
 *
 * <p>Everything that touches a player runs on that player's own scheduler. On Paper that is the
 * main thread; on Folia it is the thread of the region the player is in.</p>
 */
public final class Tasks {

    private final Plugin plugin;

    public Tasks(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Runs right away if this thread owns the entity, otherwise on the entity's next tick. */
    public void entity(Entity entity, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            task.run();
        } else {
            entity.getScheduler().run(plugin, scheduled -> task.run(), null);
        }
    }

    public void entityLater(Entity entity, Runnable task, long delayTicks) {
        if (plugin.isEnabled()) {
            entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, Math.max(1, delayTicks));
        }
    }

    /**
     * Repeats {@code task} on the entity's thread until cancelled or until the entity is removed,
     * in which case {@code retired} runs once.
     */
    public @Nullable ScheduledTask entityTimer(Entity entity, Consumer<ScheduledTask> task, @Nullable Runnable retired,
                                               long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return null;
        }
        return entity.getScheduler().runAtFixedRate(plugin, task, retired, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    /** Runs on the global region (the main thread on Paper). Console commands must run there. */
    public void global(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        }
    }

    public void async(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
        }
    }

    public void cancelAll() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
