package io.github.topdanya33200_netizen.fightclock;

import io.github.topdanya33200_netizen.fightclock.combat.CombatService;
import io.github.topdanya33200_netizen.fightclock.combat.CombatTracker;
import io.github.topdanya33200_netizen.fightclock.combat.LogoutNotices;
import io.github.topdanya33200_netizen.fightclock.combat.MessageThrottle;
import io.github.topdanya33200_netizen.fightclock.command.CombatCommand;
import io.github.topdanya33200_netizen.fightclock.config.Messages;
import io.github.topdanya33200_netizen.fightclock.config.Settings;
import io.github.topdanya33200_netizen.fightclock.hook.FightClockExpansion;
import io.github.topdanya33200_netizen.fightclock.listener.CombatListener;
import io.github.topdanya33200_netizen.fightclock.listener.RestrictionListener;
import io.github.topdanya33200_netizen.fightclock.util.Tasks;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Locale;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Entry point: loads the configuration and wires the fight tracker, listeners and commands.
 */
public final class FightClockPlugin extends JavaPlugin {

    private volatile Settings settings;
    private volatile Messages messages;
    private Tasks tasks;
    private CombatService combat;
    private @Nullable FightClockExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.saveBundled(this);
        loadConfiguration();

        tasks = new Tasks(this);
        LogoutNotices notices = new LogoutNotices(new File(getDataFolder(), "notices.yml"), getLogger());
        notices.load();
        InstantSource clock = InstantSource.system();
        combat = new CombatService(this, new CombatTracker(clock), new MessageThrottle(clock, Duration.ofMillis(1500)), notices);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new CombatListener(this), this);
        pluginManager.registerEvents(new RestrictionListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(new CombatCommand(this).node(), "Show your fight timer", List.of("fightclock")));

        hookPlaceholderApi();
        logSummary();
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        if (combat != null) {
            combat.shutdown();
        }
        if (tasks != null) {
            tasks.cancelAll();
        }
    }

    /** Re-reads config.yml and the language file; used by {@code /combat reload}. */
    public void reload() {
        reloadConfig();
        Messages.saveBundled(this);
        loadConfiguration();
        hookPlaceholderApi();
        logSummary();
    }

    private void loadConfiguration() {
        Settings loaded = Settings.load(getConfig(), getLogger());
        this.messages = Messages.load(this, loaded.language());
        this.settings = loaded;
    }

    private void hookPlaceholderApi() {
        boolean wanted = settings.placeholderApi() && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (wanted && expansion == null) {
            FightClockExpansion created = new FightClockExpansion(this);
            if (created.register()) {
                expansion = created;
            }
        } else if (!wanted && expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    private void logSummary() {
        Settings current = settings;
        getLogger().info("Fights last " + current.duration().toSeconds() + "s; "
                + current.commands().listed().size() + " commands in the " + current.commands().mode().name().toLowerCase(Locale.ROOT)
                + ", " + current.restrictions().itemCooldowns().size() + " item cooldowns, logout "
                + (current.logout().kill() ? "kills" : "is not punished"));
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Tasks tasks() {
        return tasks;
    }

    public CombatService combat() {
        return combat;
    }
}
