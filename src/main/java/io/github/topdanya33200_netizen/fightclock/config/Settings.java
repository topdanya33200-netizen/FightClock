package io.github.topdanya33200_netizen.fightclock.config;

import io.github.topdanya33200_netizen.fightclock.command.CommandRules;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable snapshot of {@code config.yml}. A reload builds a new one and swaps it in, so code
 * running on other threads always sees a complete, consistent configuration.
 */
public record Settings(
        String language,
        Duration duration,
        Triggers triggers,
        boolean tagVictim,
        boolean tagAttacker,
        boolean ignoreCreative,
        Set<String> disabledWorlds,
        boolean endOnDeath,
        Display display,
        CommandRules commands,
        Restrictions restrictions,
        Logout logout,
        boolean placeholderApi) {

    /** Which kinds of player damage start a fight. */
    public record Triggers(boolean melee, boolean projectiles, boolean explosions, boolean potions) {
    }

    public record Display(boolean bossbar, BossBar.Color color, BossBar.Overlay overlay, boolean actionbar, boolean chat) {
    }

    /**
     * @param blockedTeleports names of {@code PlayerTeleportEvent.TeleportCause} values
     * @param itemCooldowns    cooldown applied after using the item during a fight
     */
    public record Restrictions(Set<String> blockedTeleports, boolean blockElytra, boolean disableFlight,
                               Map<Material, Duration> itemCooldowns) {
    }

    /**
     * @param safeKickCauses names of {@code PlayerKickEvent.Cause} values that are not punished,
     *                       normalised with {@link #kickCause(String)}
     */
    public record Logout(boolean kill, boolean announce, Set<String> safeKickCauses, List<String> commands) {

        public boolean isSafeKick(String cause) {
            return safeKickCauses.contains(kickCause(cause));
        }
    }

    public static final Duration MIN_DURATION = Duration.ofSeconds(1);

    public boolean isDisabledWorld(String worldName) {
        return disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    /**
     * Paper 26.2 renamed the {@code /kick} cause from {@code KICK_COMMAND} to {@code KICKED}.
     * Both names mean the same here, whichever server version the plugin runs on.
     */
    public static String kickCause(String name) {
        String upper = name.strip().toUpperCase(Locale.ROOT);
        return upper.equals("KICK_COMMAND") ? "KICKED" : upper;
    }

    public static Settings load(ConfigurationSection config, Logger logger) {
        String language = config.getString("language", "en").strip().toLowerCase(Locale.ROOT);

        int seconds = config.getInt("combat.duration-seconds", 15);
        if (seconds < MIN_DURATION.toSeconds()) {
            logger.warning("combat.duration-seconds must be at least 1, using 1");
            seconds = 1;
        }

        Triggers triggers = new Triggers(
                config.getBoolean("combat.triggers.melee", true),
                config.getBoolean("combat.triggers.projectiles", true),
                config.getBoolean("combat.triggers.explosions", true),
                config.getBoolean("combat.triggers.potions", true));

        Set<String> disabledWorlds = new HashSet<>();
        for (String world : config.getStringList("combat.disabled-worlds")) {
            disabledWorlds.add(world.strip().toLowerCase(Locale.ROOT));
        }

        Display display = new Display(
                config.getBoolean("display.bossbar.enabled", true),
                parseEnum(BossBar.Color.class, config.getString("display.bossbar.color", "RED"), BossBar.Color.RED,
                        "display.bossbar.color", logger),
                parseEnum(BossBar.Overlay.class, config.getString("display.bossbar.overlay", "PROGRESS"),
                        BossBar.Overlay.PROGRESS, "display.bossbar.overlay", logger),
                config.getBoolean("display.actionbar", true),
                config.getBoolean("display.chat", true));

        CommandRules.Mode mode = parseEnum(CommandRules.Mode.class, config.getString("commands.mode", "blacklist"),
                CommandRules.Mode.BLACKLIST, "commands.mode", logger);
        CommandRules commands = new CommandRules(mode, config.getStringList("commands.list"));

        Set<String> teleports = new HashSet<>();
        for (String cause : config.getStringList("restrictions.blocked-teleports")) {
            String name = cause.strip().toUpperCase(Locale.ROOT);
            // Renamed in 1.21.5; both spellings work
            teleports.add(name.equals("CHORUS_FRUIT") ? "CONSUMABLE_EFFECT" : name);
        }

        Map<Material, Duration> cooldowns = new EnumMap<>(Material.class);
        ConfigurationSection cooldownSection = config.getConfigurationSection("restrictions.item-cooldowns");
        if (cooldownSection != null) {
            for (String key : cooldownSection.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                int cooldown = cooldownSection.getInt(key);
                if (material == null) {
                    logger.warning("restrictions.item-cooldowns: unknown item " + key + ", skipping it");
                } else if (cooldown > 0) {
                    cooldowns.put(material, Duration.ofSeconds(cooldown));
                }
            }
        }
        Restrictions restrictions = new Restrictions(Set.copyOf(teleports),
                config.getBoolean("restrictions.block-elytra", true),
                config.getBoolean("restrictions.disable-flight", true),
                Map.copyOf(cooldowns));

        Set<String> safeKicks = new HashSet<>();
        for (String cause : config.getStringList("logout.safe-kick-causes")) {
            safeKicks.add(kickCause(cause));
        }
        Logout logout = new Logout(
                config.getBoolean("logout.kill", true),
                config.getBoolean("logout.announce", true),
                Set.copyOf(safeKicks),
                List.copyOf(config.getStringList("logout.commands")));

        return new Settings(
                language,
                Duration.ofSeconds(seconds),
                triggers,
                config.getBoolean("combat.tag-victim", true),
                config.getBoolean("combat.tag-attacker", true),
                config.getBoolean("combat.ignore-creative", true),
                Set.copyOf(disabledWorlds),
                config.getBoolean("combat.end-on-death", true),
                display,
                commands,
                restrictions,
                logout,
                config.getBoolean("placeholderapi", true));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback, String path, Logger logger) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            logger.warning(path + ": unknown value " + value + ", using " + fallback.name());
            return fallback;
        }
    }
}
