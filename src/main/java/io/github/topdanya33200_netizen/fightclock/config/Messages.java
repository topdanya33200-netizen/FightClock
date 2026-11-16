package io.github.topdanya33200_netizen.fightclock.config;

import io.github.topdanya33200_netizen.fightclock.util.TimeFormat;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Language file loaded from {@code plugins/FightClock/lang/messages_<language>.yml}.
 *
 * <p>Messages use MiniMessage. Player names are always inserted with
 * {@link Placeholder#unparsed(String, String)}, so nothing in a name is ever read as a tag.</p>
 */
public final class Messages {

    private static final List<String> BUNDLED = List.of("en", "ru");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> strings;
    private final TagResolver prefix;
    private final TimeFormat.Units units;

    private Messages(Map<String, String> strings) {
        this.strings = Map.copyOf(strings);
        this.prefix = Placeholder.parsed("prefix", this.strings.getOrDefault("prefix", ""));
        this.units = new TimeFormat.Units(raw("time.days"), raw("time.hours"), raw("time.minutes"), raw("time.seconds"));
    }

    /** Copies the bundled language files to the data folder unless they already exist. */
    public static void saveBundled(JavaPlugin plugin) {
        for (String language : BUNDLED) {
            String path = "lang/messages_" + language + ".yml";
            if (!new File(plugin.getDataFolder(), path).exists()) {
                plugin.saveResource(path, false);
            }
        }
    }

    /**
     * Loads a language. Keys missing from the user's file fall back to the bundled version, so
     * messages added by an update always show up.
     */
    public static Messages load(JavaPlugin plugin, String language) {
        Map<String, String> strings = new HashMap<>();
        readInto(strings, plugin.getResource("lang/messages_en.yml"));
        if (!language.equals("en") && BUNDLED.contains(language)) {
            readInto(strings, plugin.getResource("lang/messages_" + language + ".yml"));
        }
        File file = new File(plugin.getDataFolder(), "lang/messages_" + language + ".yml");
        if (file.isFile()) {
            readInto(strings, YamlConfiguration.loadConfiguration(file));
        } else {
            plugin.getLogger().warning("lang/messages_" + language + ".yml was not found, using the built-in messages");
        }
        return new Messages(strings);
    }

    /** Messages from a single YAML file, for tests. */
    public static Messages of(YamlConfiguration yaml) {
        Map<String, String> strings = new HashMap<>();
        readInto(strings, yaml);
        return new Messages(strings);
    }

    private static void readInto(Map<String, String> target, @Nullable InputStream stream) {
        if (stream == null) {
            return;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            readInto(target, YamlConfiguration.loadConfiguration(reader));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void readInto(Map<String, String> target, YamlConfiguration yaml) {
        for (String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key)) {
                continue;
            }
            String value = yaml.getString(key);
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    /** The unformatted entry, or the key itself so missing translations are easy to spot. */
    public String raw(String key) {
        return strings.getOrDefault(key, key);
    }

    public boolean isEmpty(String key) {
        return raw(key).isEmpty();
    }

    public Component render(String key, TagResolver... resolvers) {
        return MINI.deserialize(raw(key), TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
    }

    /** Sends the message unless it was set to an empty string, which turns it off. */
    public void send(Audience audience, String key, TagResolver... resolvers) {
        if (!isEmpty(key)) {
            audience.sendMessage(render(key, resolvers));
        }
    }

    /** {@code 12s}, {@code 1m 5s}: always at least one second, so a countdown never shows 0. */
    public String time(Duration duration) {
        return TimeFormat.duration(duration, units);
    }

    /** {@code <time>} and {@code <seconds>} placeholders for a remaining duration. */
    public TagResolver timeLeft(Duration remaining) {
        long seconds = (Math.max(0, remaining.toMillis()) + 999) / 1000;
        return TagResolver.resolver(
                Placeholder.unparsed("time", time(remaining)),
                Placeholder.unparsed("seconds", Long.toString(seconds)));
    }
}
