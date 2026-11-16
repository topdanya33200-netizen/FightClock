package io.github.topdanya33200_netizen.fightclock.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private static YamlConfiguration language(String code) throws Exception {
        String path = "/lang/messages_" + code + ".yml";
        try (InputStream stream = Objects.requireNonNull(MessagesTest.class.getResourceAsStream(path), path)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static Set<String> keys(YamlConfiguration yaml) {
        Set<String> keys = new TreeSet<>();
        for (String key : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static String plain(Messages messages, String key) {
        return PlainTextComponentSerializer.plainText().serialize(messages.render(key,
                Placeholder.unparsed("enemy", "Alex"), Placeholder.unparsed("player", "Steve"),
                Placeholder.unparsed("command", "/home"), Placeholder.unparsed("count", "2"),
                messages.timeLeft(Duration.ofMillis(11_200))));
    }

    @Test
    void everyLanguageHasTheSameKeys() throws Exception {
        assertEquals(keys(language("en")), keys(language("ru")));
    }

    @Test
    void messagesRenderWithTheirPlaceholders() throws Exception {
        Messages english = Messages.of(language("en"));
        assertEquals("[FightClock] You are in a fight with Alex! Logging out now kills you. Wait 12s.",
                plain(english, "combat.started"));
        assertEquals("Steve left a fight with Alex and was killed.", plain(english, "logout.death-message"));
        assertEquals("[FightClock] You can't use /home during a fight. Wait 12s.", plain(english, "restrictions.command"));
        assertEquals("⚔ Fight with Alex · 12s", plain(english, "hud.bossbar"));

        Messages russian = Messages.of(language("ru"));
        assertEquals("[FightClock] Вы в бою с Alex! Выход сейчас — смерть. Подождите 12с.", plain(russian, "combat.started"));
    }

    @Test
    void noMessageShowsARawTag() throws Exception {
        for (String code : new String[] {"en", "ru"}) {
            Messages messages = Messages.of(language(code));
            for (String key : keys(language(code))) {
                String text = plain(messages, key);
                // An escaped \<player> in a usage hint is meant to show up literally
                String withoutHints = text.replace("<player>", "").replace("<игрок>", "");
                assertFalse(withoutHints.contains("<") || withoutHints.contains(">"), code + " " + key + ": " + text);
            }
        }
    }
}
