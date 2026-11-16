package io.github.topdanya33200_netizen.fightclock.listener;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import io.github.topdanya33200_netizen.fightclock.FightClockPlugin;
import io.github.topdanya33200_netizen.fightclock.Permissions;
import io.github.topdanya33200_netizen.fightclock.combat.CombatService;
import io.github.topdanya33200_netizen.fightclock.command.CommandRules;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

/**
 * What players can't do during a fight: blocked commands, teleports and portals, elytra and
 * flight, plus longer item cooldowns.
 */
public final class RestrictionListener implements Listener {

    private final FightClockPlugin plugin;

    public RestrictionListener(FightClockPlugin plugin) {
        this.plugin = plugin;
    }

    private CombatService combat() {
        return plugin.combat();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!combat().inFight(player) || player.hasPermission(Permissions.BYPASS_COMMANDS)) {
            return;
        }
        CommandRules rules = plugin.settings().commands();
        if (rules.isBlocked(event.getMessage(), RestrictionListener::knownNames)) {
            event.setCancelled(true);
            plugin.messages().send(player, "restrictions.command",
                    Placeholder.unparsed("command", "/" + CommandRules.label(event.getMessage())),
                    plugin.messages().timeLeft(combat().remaining(player)));
        }
    }

    /** Every name the server knows a command by: its name, label and aliases. */
    private static List<String> knownNames(String label) {
        Command command = Bukkit.getCommandMap().getCommand(label);
        if (command == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(command.getAliases());
        names.add(command.getName());
        names.add(command.getLabel());
        return names;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isBlocked(event)) {
            event.setCancelled(true);
            combat().warn(event.getPlayer(), "restrictions.teleport");
        }
    }

    /** Portals fire their own event type, which a teleport listener doesn't receive. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (isBlocked(event)) {
            event.setCancelled(true);
            combat().warn(event.getPlayer(), "restrictions.portal");
        }
    }

    private boolean isBlocked(PlayerTeleportEvent event) {
        return plugin.settings().restrictions().blockedTeleports().contains(event.getCause().name())
                && combat().inFight(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player
                && plugin.settings().restrictions().blockElytra() && combat().inFight(player)) {
            event.setCancelled(true);
            combat().warn(player, "restrictions.elytra");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (event.isFlying() && plugin.settings().restrictions().disableFlight() && combat().inFight(player)
                && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            combat().warn(player, "restrictions.flight");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        combat().onItemUsed(event.getPlayer(), event.getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        combat().onItemUsed(event.getPlayer(), event.getItem());
    }
}
