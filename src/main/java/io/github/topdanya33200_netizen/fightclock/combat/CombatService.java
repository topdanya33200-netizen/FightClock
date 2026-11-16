package io.github.topdanya33200_netizen.fightclock.combat;

import io.github.topdanya33200_netizen.fightclock.FightClockPlugin;
import io.github.topdanya33200_netizen.fightclock.Permissions;
import io.github.topdanya33200_netizen.fightclock.api.CombatEndEvent;
import io.github.topdanya33200_netizen.fightclock.api.CombatLogoutEvent;
import io.github.topdanya33200_netizen.fightclock.api.CombatTagEvent;
import io.github.topdanya33200_netizen.fightclock.config.Messages;
import io.github.topdanya33200_netizen.fightclock.config.Settings;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Starts, refreshes and ends fights, draws the countdown and punishes players who leave.
 *
 * <p>Threading: methods that take a {@link Player} must run on the thread that owns that player
 * (the main thread on Paper, the player's region on Folia). Fight state itself lives in the
 * thread-safe {@link CombatTracker}, so status queries work from anywhere.</p>
 */
public final class CombatService {

    /** How often the countdown is redrawn, in ticks. */
    private static final long HUD_PERIOD = 5;
    /** Delay before the "you were killed" notice, so it isn't lost among the join messages. */
    private static final long NOTICE_DELAY = 20;

    private final FightClockPlugin plugin;
    private final CombatTracker tracker;
    private final MessageThrottle throttle;
    private final LogoutNotices notices;
    private final Map<UUID, CombatHud> huds = new ConcurrentHashMap<>();
    private final Map<UUID, String> kickCauses = new ConcurrentHashMap<>();
    private final Set<UUID> punishing = ConcurrentHashMap.newKeySet();

    public CombatService(FightClockPlugin plugin, CombatTracker tracker, MessageThrottle throttle, LogoutNotices notices) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.throttle = throttle;
        this.notices = notices;
    }

    private Settings settings() {
        return plugin.settings();
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ---------------------------------------------------------------- queries

    public Optional<Fight> fight(UUID player) {
        return tracker.fight(player);
    }

    public boolean inFight(Player player) {
        return tracker.inFight(player.getUniqueId());
    }

    public Map<UUID, Fight> running() {
        return tracker.running();
    }

    public int count() {
        return tracker.count();
    }

    public Duration remaining(Player player) {
        return tracker.fight(player.getUniqueId()).map(fight -> fight.remaining(tracker.now())).orElse(Duration.ZERO);
    }

    /** Players with the bypass permission, in creative, in a disabled world or NPCs never fight. */
    public boolean canFight(Player player) {
        Settings settings = settings();
        if (player.hasMetadata("NPC") || player.hasPermission(Permissions.BYPASS)) {
            return false;
        }
        if (settings.ignoreCreative() && isCreativeOrSpectator(player)) {
            return false;
        }
        return !settings.isDisabledWorld(player.getWorld().getName());
    }

    private static boolean isCreativeOrSpectator(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    // ---------------------------------------------------------------- starting fights

    /** A player hurt another one. Runs on the victim's thread. */
    public void onHit(Player victim, Player attacker) {
        Settings settings = settings();
        if (attacker.hasMetadata("NPC") || victim.hasMetadata("NPC")) {
            return;
        }
        if (settings.tagVictim()) {
            tag(victim, attacker, CombatTagEvent.Cause.HIT, settings.duration());
        }
        if (settings.tagAttacker()) {
            // On Folia the attacker can be in another region; tag them on their own thread
            plugin.tasks().entity(attacker, () -> {
                if (attacker.isOnline()) {
                    tag(attacker, victim, CombatTagEvent.Cause.ATTACK, settings().duration());
                }
            });
        }
    }

    /**
     * Starts a fight or restarts its countdown. Runs on the player's thread.
     *
     * @return {@code false} if the player can't fight or another plugin cancelled the tag
     */
    public boolean tag(Player player, @Nullable Player enemy, CombatTagEvent.Cause cause, Duration length) {
        if (cause != CombatTagEvent.Cause.COMMAND && !canFight(player)) {
            return false;
        }
        UUID id = player.getUniqueId();
        CombatTagEvent event = new CombatTagEvent(player, enemy, cause, length, tracker.inFight(id));
        if (!event.callEvent()) {
            return false;
        }
        CombatTracker.TagResult result = tracker.tag(id,
                enemy == null ? null : enemy.getUniqueId(), enemy == null ? null : enemy.getName(), event.getDuration());
        Optional<Fight> fight = tracker.fight(id);
        if (fight.isEmpty()) {
            return false;
        }
        CombatHud hud = huds.get(id);
        if (result == CombatTracker.TagResult.STARTED || hud == null) {
            hud = startHud(player);
            applyRestrictions(player);
            if (settings().display().chat()) {
                Fight started = fight.get();
                messages().send(player, started.hasEnemy() ? "combat.started" : "combat.started-no-enemy",
                        Placeholder.unparsed("enemy", started.hasEnemy() ? started.enemyName() : ""),
                        messages().timeLeft(started.remaining(tracker.now())));
            }
        }
        hud.update(player, fight.get(), tracker.now(), settings().display(), messages(), true);
        return true;
    }

    private CombatHud startHud(Player player) {
        UUID id = player.getUniqueId();
        CombatHud hud = new CombatHud();
        CombatHud previous = huds.put(id, hud);
        if (previous != null) {
            previous.stop(player);
        }
        hud.ticker(plugin.tasks().entityTimer(player, task -> tick(player, hud), () -> huds.remove(id, hud),
                HUD_PERIOD, HUD_PERIOD));
        return hud;
    }

    /** Elytra close and flight stops the moment a fight starts. */
    private void applyRestrictions(Player player) {
        Settings.Restrictions restrictions = settings().restrictions();
        if (restrictions.blockElytra() && player.isGliding()) {
            player.setGliding(false);
        }
        if (restrictions.disableFlight() && player.isFlying() && !isCreativeOrSpectator(player)) {
            player.setFlying(false);
        }
    }

    private void tick(Player player, CombatHud hud) {
        UUID id = player.getUniqueId();
        if (huds.get(id) != hud) {
            hud.cancelTicker(); // a newer fight took over
            return;
        }
        if (tracker.expireIfDue(id)) {
            finish(player, CombatEndEvent.Reason.EXPIRED, true);
            return;
        }
        Optional<Fight> fight = tracker.fight(id);
        if (fight.isEmpty()) {
            finish(player, CombatEndEvent.Reason.EXPIRED, true);
            return;
        }
        hud.update(player, fight.get(), tracker.now(), settings().display(), messages(), false);
    }

    // ---------------------------------------------------------------- ending fights

    /**
     * Ends the player's fight without punishment. Runs on the player's thread.
     *
     * @return {@code true} if they were in a fight
     */
    public boolean end(Player player, CombatEndEvent.Reason reason, boolean notify) {
        boolean wasFighting = tracker.end(player.getUniqueId()).isPresent();
        if (wasFighting || huds.containsKey(player.getUniqueId())) {
            finish(player, reason, notify);
        }
        return wasFighting;
    }

    private void finish(Player player, CombatEndEvent.Reason reason, boolean notify) {
        CombatHud hud = huds.remove(player.getUniqueId());
        if (hud != null) {
            hud.stop(player);
        }
        new CombatEndEvent(player, reason).callEvent();
        if (notify && settings().display().chat()) {
            messages().send(player, "combat.ended");
        }
    }

    // ---------------------------------------------------------------- deaths and logouts

    /** Remembers why a player is being kicked, for the quit handler right after. */
    public void onKick(Player player, String cause) {
        kickCauses.put(player.getUniqueId(), cause);
    }

    /** Runs on the quitting player's thread while they are still online. */
    public void onQuit(Player player, PlayerQuitEvent.QuitReason reason) {
        UUID id = player.getUniqueId();
        String kickCause = kickCauses.remove(id);
        throttle.forget(id);
        Optional<Fight> fight = tracker.fight(id);
        if (fight.isEmpty()) {
            end(player, CombatEndEvent.Reason.LOGOUT, false);
            return;
        }
        Settings.Logout logout = settings().logout();
        if (reason == PlayerQuitEvent.QuitReason.KICKED && kickCause != null && logout.isSafeKick(kickCause)) {
            plugin.getLogger().info(player.getName() + " was kicked during a fight (" + Settings.kickCause(kickCause)
                    + ") and was not punished");
        } else if (logout.kill()) {
            Fight current = fight.get();
            CombatLogoutEvent event = new CombatLogoutEvent(player, current.enemyId(), current.enemyName(), reason);
            if (event.callEvent()) {
                punish(player, current);
            } else {
                plugin.getLogger().info(player.getName() + " left a fight; another plugin excused them");
            }
        }
        end(player, CombatEndEvent.Reason.LOGOUT, false);
    }

    /**
     * Kills a player who left a fight. The damage is a {@code generic_kill} caused by their last
     * enemy: totems can't save them, their items drop as on any death, and the enemy is credited
     * with the kill (statistics, scoreboards, {@code Player#getKiller()}).
     */
    private void punish(Player player, Fight fight) {
        UUID id = player.getUniqueId();
        DamageSource.Builder source = DamageSource.builder(DamageType.GENERIC_KILL);
        Player enemy = fight.enemyId() == null ? null : Bukkit.getPlayer(fight.enemyId());
        // On Folia the enemy may be in another region; kill credit is skipped there to stay thread safe
        if (enemy != null && !enemy.equals(player) && enemy.isValid() && Bukkit.isOwnedByCurrentRegion(enemy)) {
            source.withCausingEntity(enemy).withDirectEntity(enemy);
        }
        punishing.add(id);
        try {
            player.damage(Float.MAX_VALUE, source.build());
            if (!player.isDead()) {
                player.setHealth(0); // another plugin cancelled the damage
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not punish " + player.getName() + " for leaving a fight", exception);
        } finally {
            punishing.remove(id);
        }

        notices.add(id, fight.enemyName());
        plugin.tasks().async(notices::save);

        String enemyName = fight.enemyName() == null ? "" : fight.enemyName();
        for (String command : settings().logout().commands()) {
            String line = command.replace("{player}", player.getName()).replace("{enemy}", enemyName).strip();
            String toRun = line.startsWith("/") ? line.substring(1) : line;
            if (!toRun.isEmpty()) {
                plugin.tasks().global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toRun));
            }
        }
        plugin.getLogger().info(player.getName() + " left a fight" + (enemyName.isEmpty() ? "" : " with " + enemyName)
                + " and was killed");
    }

    /** Sets the death message for logout kills and ends the fight on death. */
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID id = player.getUniqueId();
        if (punishing.contains(id)) {
            if (settings().logout().announce()) {
                Optional<Fight> fight = tracker.fight(id);
                boolean hasEnemy = fight.isPresent() && fight.get().hasEnemy();
                String key = hasEnemy ? "logout.death-message" : "logout.death-message-no-enemy";
                if (!messages().isEmpty(key)) {
                    event.deathMessage(messages().render(key,
                            Placeholder.unparsed("player", player.getName()),
                            Placeholder.unparsed("enemy", hasEnemy ? fight.get().enemyName() : "")));
                }
            }
            return; // the quit handler ends the fight
        }
        if (settings().endOnDeath()) {
            end(player, CombatEndEvent.Reason.DEATH, false);
        }
    }

    /** Tells a returning player that they were killed for leaving a fight. */
    public void onJoin(Player player) {
        notices.take(player.getUniqueId()).ifPresent(enemy -> {
            plugin.tasks().async(notices::save);
            plugin.tasks().entityLater(player, () -> messages().send(player,
                    enemy.isEmpty() ? "logout.notice-no-enemy" : "logout.notice",
                    Placeholder.unparsed("enemy", enemy)), NOTICE_DELAY);
        });
    }

    // ---------------------------------------------------------------- restrictions

    /** Shows a warning at most once per second or so, however often the event fires. */
    public void warn(Player player, String key) {
        if (!messages().isEmpty(key) && throttle.allow(player.getUniqueId(), key)) {
            messages().send(player, key, messages().timeLeft(remaining(player)));
        }
    }

    /** Applies the configured cooldown after an item was used during a fight. */
    public void onItemUsed(Player player, ItemStack item) {
        Duration cooldown = settings().restrictions().itemCooldowns().get(item.getType());
        if (cooldown == null || !inFight(player)) {
            return;
        }
        ItemStack used = item.clone();
        int ticks = (int) Math.min(Integer.MAX_VALUE, cooldown.toMillis() / 50);
        // Vanilla sets its own short cooldown right after this event, so ours goes on next tick
        plugin.tasks().entityLater(player, () -> {
            if (player.isOnline() && player.getCooldown(used) < ticks) {
                player.setCooldown(used, ticks);
            }
        }, 1);
    }

    // ---------------------------------------------------------------- lifecycle

    /** Clears every countdown, for plugin shutdown. */
    public void shutdown() {
        for (Map.Entry<UUID, CombatHud> entry : Map.copyOf(huds).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            try {
                if (player != null) {
                    entry.getValue().stop(player);
                } else {
                    entry.getValue().cancelTicker();
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.FINE, "Could not clear the countdown of " + entry.getKey(), exception);
            }
        }
        huds.clear();
        tracker.clear();
        kickCauses.clear();
        notices.save();
    }
}
