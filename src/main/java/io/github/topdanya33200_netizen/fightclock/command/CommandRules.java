package io.github.topdanya33200_netizen.fightclock.command;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Decides which commands a player may run during a fight.
 *
 * <p>The typed label is compared together with everything the server knows the command by: its
 * real name and its aliases (looked up through {@code resolver}), all without a
 * {@code plugin:} prefix. So blocking {@code home} also blocks {@code /h},
 * {@code /essentials:home} and {@code /ESSENTIALS:H}.</p>
 */
public final class CommandRules {

    public enum Mode {
        /** The listed commands are blocked. */
        BLACKLIST,
        /** Only the listed commands are allowed. */
        WHITELIST
    }

    /** Our own command always works, so players can check their timer. */
    private static final Set<String> ALWAYS_ALLOWED = Set.of("combat", "fightclock");

    private final Mode mode;
    private final Set<String> listed;

    public CommandRules(Mode mode, Collection<String> commands) {
        this.mode = mode;
        Set<String> normalized = new HashSet<>();
        for (String command : commands) {
            String name = normalize(command.strip());
            if (!name.isEmpty()) {
                normalized.add(name);
            }
        }
        this.listed = Set.copyOf(normalized);
    }

    public Mode mode() {
        return mode;
    }

    public Set<String> listed() {
        return listed;
    }

    /**
     * @param commandLine what the player typed, with or without the leading slash
     * @param resolver    names the server knows the command by for a typed label; empty if the
     *                    command doesn't exist
     */
    public boolean isBlocked(String commandLine, Function<String, ? extends Collection<String>> resolver) {
        String label = label(commandLine);
        if (label.isEmpty()) {
            return false;
        }
        Set<String> names = new HashSet<>();
        names.add(normalize(label));
        for (String name : resolver.apply(label)) {
            names.add(normalize(name));
        }
        if (names.stream().anyMatch(ALWAYS_ALLOWED::contains)) {
            return false;
        }
        boolean isListed = names.stream().anyMatch(listed::contains);
        return mode == Mode.BLACKLIST ? isListed : !isListed;
    }

    /** The command label as typed, lower case and without the slash: {@code "/Home base"} gives {@code "home"}. */
    public static String label(String commandLine) {
        String line = commandLine.strip();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        int space = line.indexOf(' ');
        String label = space < 0 ? line : line.substring(0, space);
        return label.toLowerCase(Locale.ROOT);
    }

    /** Lower case and without a {@code plugin:} prefix: {@code "Essentials:Home"} gives {@code "home"}. */
    public static String normalize(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/")) {
            lower = lower.substring(1);
        }
        int colon = lower.indexOf(':');
        return colon < 0 ? lower : lower.substring(colon + 1);
    }

    public static CommandRules blockNothing() {
        return new CommandRules(Mode.BLACKLIST, List.of());
    }
}
