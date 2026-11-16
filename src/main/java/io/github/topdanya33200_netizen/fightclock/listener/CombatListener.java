package io.github.topdanya33200_netizen.fightclock.listener;

import io.github.topdanya33200_netizen.fightclock.FightClockPlugin;
import io.github.topdanya33200_netizen.fightclock.combat.DamageAttribution;
import io.github.topdanya33200_netizen.fightclock.config.Settings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Starts fights on player damage and handles deaths, kicks, logouts and joins.
 */
public final class CombatListener implements Listener {

    private final FightClockPlugin plugin;

    public CombatListener(FightClockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * MONITOR with {@code ignoreCancelled}: hits cancelled by region protection, team or
     * friendly-fire plugins never start a fight.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        DamageAttribution.Hit hit = DamageAttribution.of(event);
        if (hit == null || hit.attacker().equals(victim)) {
            return;
        }
        Settings.Triggers triggers = plugin.settings().triggers();
        boolean counts = switch (hit.kind()) {
            case MELEE, OTHER -> triggers.melee();
            case PROJECTILE -> triggers.projectiles();
            case EXPLOSION -> triggers.explosions();
            case POTION -> triggers.potions();
        };
        if (counts) {
            plugin.combat().onHit(victim, hit.attacker());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        plugin.combat().onDeath(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        plugin.combat().onKick(event.getPlayer(), event.getCause().name());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        plugin.combat().onQuit(event.getPlayer(), event.getReason());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.combat().onJoin(event.getPlayer());
    }
}
