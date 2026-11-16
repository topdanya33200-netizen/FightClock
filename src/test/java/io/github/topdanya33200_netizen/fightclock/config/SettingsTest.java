package io.github.topdanya33200_netizen.fightclock.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.topdanya33200_netizen.fightclock.command.CommandRules;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private static YamlConfiguration bundledConfig() throws Exception {
        try (InputStream stream = Objects.requireNonNull(SettingsTest.class.getResourceAsStream("/config.yml"))) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    /** A logger that remembers warnings, so tests can assert on them. */
    private static final class Recording extends Handler {
        final java.util.List<String> warnings = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            warnings.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static Logger recordingLogger(Recording recording) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(recording);
        return logger;
    }

    @Test
    void bundledConfigLoadsWithoutWarnings() throws Exception {
        Recording recording = new Recording();
        Settings settings = Settings.load(bundledConfig(), recordingLogger(recording));

        assertEquals(List.of(), recording.warnings);
        assertEquals("en", settings.language());
        assertEquals(Duration.ofSeconds(15), settings.duration());
        assertTrue(settings.triggers().melee() && settings.triggers().projectiles()
                && settings.triggers().explosions() && settings.triggers().potions());
        assertEquals(BossBar.Color.RED, settings.display().color());
        assertEquals(BossBar.Overlay.PROGRESS, settings.display().overlay());
        assertEquals(CommandRules.Mode.BLACKLIST, settings.commands().mode());
        assertTrue(settings.commands().isBlocked("/home", label -> List.of()));
        assertFalse(settings.commands().isBlocked("/msg Alex hi", label -> List.of()));
        assertTrue(settings.restrictions().blockedTeleports().containsAll(List.of("COMMAND", "NETHER_PORTAL", "END_PORTAL")));
        assertFalse(settings.restrictions().blockedTeleports().contains("ENDER_PEARL"));
        assertEquals(Duration.ofSeconds(15), settings.restrictions().itemCooldowns().get(Material.ENDER_PEARL));
        assertTrue(settings.logout().kill());
        assertTrue(settings.logout().announce());
    }

    @Test
    void kickCausesWorkUnderBothNames() throws Exception {
        Settings settings = Settings.load(bundledConfig(), Logger.getAnonymousLogger());
        assertTrue(settings.logout().isSafeKick("KICKED"));
        assertTrue(settings.logout().isSafeKick("KICK_COMMAND"), "the name Paper used before 26.2");
        assertTrue(settings.logout().isSafeKick("RESTART_COMMAND"));
        assertFalse(settings.logout().isSafeKick("ILLEGAL_ACTION"), "a cheat kicking itself out must still die");
        assertFalse(settings.logout().isSafeKick("DUPLICATE_LOGIN"), "relogging from a second client is a combat log");
    }

    @Test
    void badValuesFallBackWithAWarning() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                combat:
                  duration-seconds: 0
                  disabled-worlds: [Arena]
                display:
                  bossbar:
                    color: RAINBOW
                commands:
                  mode: graylist
                restrictions:
                  blocked-teleports: [chorus_fruit]
                  item-cooldowns:
                    NOT_AN_ITEM: 5
                    golden_apple: 30
                """);
        Recording recording = new Recording();
        Settings settings = Settings.load(yaml, recordingLogger(recording));

        assertEquals(Duration.ofSeconds(1), settings.duration());
        assertEquals(BossBar.Color.RED, settings.display().color());
        assertEquals(CommandRules.Mode.BLACKLIST, settings.commands().mode());
        assertTrue(settings.isDisabledWorld("arena"), "world names are compared without case");
        assertTrue(settings.restrictions().blockedTeleports().contains("CONSUMABLE_EFFECT"), "old name of the chorus cause");
        assertEquals(Duration.ofSeconds(30), settings.restrictions().itemCooldowns().get(Material.GOLDEN_APPLE));
        assertEquals(1, settings.restrictions().itemCooldowns().size());
        assertEquals(4, recording.warnings.size(), String.join("\n", recording.warnings));
    }
}
