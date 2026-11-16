package io.github.topdanya33200_netizen.fightclock.combat;

import io.github.topdanya33200_netizen.fightclock.config.Messages;
import io.github.topdanya33200_netizen.fightclock.config.Settings;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.time.Instant;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * The bossbar and action bar countdown of one player, plus the task that keeps them ticking.
 * Only touched from the thread that owns the player.
 */
final class CombatHud {

    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private boolean barShown;
    private boolean actionbarShown;
    private long lastSecond = -1;
    private @Nullable ScheduledTask ticker;

    void ticker(@Nullable ScheduledTask task) {
        this.ticker = task;
    }

    /**
     * Redraws the countdown. The action bar is only resent when the shown second changes (or
     * when {@code force} is set after a new hit), the bossbar follows every call so its
     * progress drains smoothly.
     */
    void update(Player player, Fight fight, Instant now, Settings.Display display, Messages messages, boolean force) {
        Duration left = fight.remaining(now);
        long second = (left.toMillis() + 999) / 1000;
        String enemy = fight.enemyName() == null ? "" : fight.enemyName();
        TagResolver placeholders = TagResolver.resolver(messages.timeLeft(left), Placeholder.unparsed("enemy", enemy));

        if (display.bossbar()) {
            bar.name(messages.render(fight.hasEnemy() ? "hud.bossbar" : "hud.bossbar-no-enemy", placeholders));
            bar.progress(fight.progress(now));
            bar.color(display.color());
            bar.overlay(display.overlay());
            if (!barShown) {
                player.showBossBar(bar);
                barShown = true;
            }
        } else if (barShown) {
            player.hideBossBar(bar);
            barShown = false;
        }

        if (display.actionbar() && !messages.isEmpty("hud.actionbar")) {
            if (force || second != lastSecond) {
                player.sendActionBar(messages.render("hud.actionbar", placeholders));
                actionbarShown = true;
            }
        } else if (actionbarShown) {
            player.sendActionBar(Component.empty());
            actionbarShown = false;
        }
        lastSecond = second;
    }

    /** Stops the countdown task and clears what the player sees. */
    void stop(Player player) {
        cancelTicker();
        if (barShown) {
            player.hideBossBar(bar);
            barShown = false;
        }
        if (actionbarShown) {
            player.sendActionBar(Component.empty());
            actionbarShown = false;
        }
    }

    void cancelTicker() {
        ScheduledTask task = ticker;
        ticker = null;
        if (task != null) {
            task.cancel();
        }
    }
}
