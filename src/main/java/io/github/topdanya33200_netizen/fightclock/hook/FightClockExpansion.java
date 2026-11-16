package io.github.topdanya33200_netizen.fightclock.hook;

import io.github.topdanya33200_netizen.fightclock.FightClockPlugin;
import io.github.topdanya33200_netizen.fightclock.combat.Fight;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.Nullable;

/**
 * PlaceholderAPI placeholders for scoreboards, tab lists and menus:
 * <ul>
 *   <li>{@code %fightclock_in_combat%}: {@code true} or {@code false}</li>
 *   <li>{@code %fightclock_time_left%}: e.g. {@code 12s}</li>
 *   <li>{@code %fightclock_seconds_left%}: e.g. {@code 12}, {@code 0} outside a fight</li>
 *   <li>{@code %fightclock_enemy%}: the last player fought</li>
 *   <li>{@code %fightclock_fighters%}: how many players are in a fight right now</li>
 * </ul>
 * Everything is read from memory; no placeholder blocks or touches the world.
 */
public final class FightClockExpansion extends PlaceholderExpansion {

    private final FightClockPlugin plugin;

    public FightClockExpansion(FightClockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "fightclock";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Keep the expansion registered across {@code /papi reload}. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("fighters")) {
            return Integer.toString(plugin.combat().count());
        }
        Optional<Fight> fight = player == null ? Optional.empty() : plugin.combat().fight(player.getUniqueId());
        String none = plugin.messages().raw("placeholder.none");
        Instant now = Instant.now();
        return switch (key) {
            case "in_combat" -> Boolean.toString(fight.isPresent());
            case "time_left" -> fight.map(current -> plugin.messages().time(current.remaining(now))).orElse(none);
            case "seconds_left" -> Long.toString(fight.map(current -> (current.remaining(now).toMillis() + 999) / 1000).orElse(0L));
            case "enemy" -> fight.filter(Fight::hasEnemy).map(Fight::enemyName).orElse(none);
            default -> null;
        };
    }
}
