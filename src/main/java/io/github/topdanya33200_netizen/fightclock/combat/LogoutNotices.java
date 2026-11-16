package io.github.topdanya33200_netizen.fightclock.combat;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Players who were killed for leaving a fight and haven't been told yet. They read the notice
 * the next time they join, even if the server restarted in between ({@code notices.yml}).
 */
public final class LogoutNotices {

    private final File file;
    private final Logger logger;
    private final Map<UUID, String> pending = new ConcurrentHashMap<>();

    public LogoutNotices(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                pending.put(UUID.fromString(key), yaml.getString(key, ""));
            } catch (IllegalArgumentException exception) {
                logger.warning("notices.yml: skipping " + key + ", not a player UUID");
            }
        }
    }

    /** Remembers to tell the player; {@code enemyName} is who they ran from, if known. */
    public void add(UUID player, @Nullable String enemyName) {
        pending.put(player, enemyName == null ? "" : enemyName);
    }

    /**
     * The notice for this player, removing it. The value is the enemy's name, or an empty
     * string if the enemy is unknown.
     */
    public Optional<String> take(UUID player) {
        return Optional.ofNullable(pending.remove(player));
    }

    /** Writes the pending notices to disk. Safe to call from any thread. */
    public synchronized void save() {
        Map<UUID, String> snapshot = new HashMap<>(pending);
        if (snapshot.isEmpty()) {
            if (file.exists() && !file.delete()) {
                logger.warning("Could not delete " + file.getName());
            }
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.forEach((id, enemy) -> yaml.set(id.toString(), enemy));
        try {
            yaml.save(file);
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not save " + file.getName(), exception);
        }
    }
}
