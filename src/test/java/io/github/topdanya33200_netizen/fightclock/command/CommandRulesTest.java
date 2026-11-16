package io.github.topdanya33200_netizen.fightclock.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class CommandRulesTest {

    /** What a server with EssentialsX might report: label -> the command's name and aliases. */
    private static final Map<String, List<String>> SERVER = Map.of(
            "home", List.of("home", "h", "homes"),
            "h", List.of("home", "h", "homes"),
            "essentials:h", List.of("home", "h", "homes"),
            "essentials:home", List.of("home", "h", "homes"),
            "msg", List.of("msg", "tell", "w"),
            "combat", List.of("combat", "fightclock"));

    private static final Function<String, List<String>> RESOLVER = label -> SERVER.getOrDefault(label, List.of());

    private final CommandRules blacklist = new CommandRules(CommandRules.Mode.BLACKLIST, List.of("home", "/Spawn", "tpa"));

    @Test
    void blocksListedCommandsWithArguments() {
        assertTrue(blacklist.isBlocked("/home base", RESOLVER));
        assertTrue(blacklist.isBlocked("/spawn", RESOLVER), "entries may be written with a slash and any case");
        assertFalse(blacklist.isBlocked("/msg Alex hi", RESOLVER));
    }

    @Test
    void aliasesAndPluginPrefixesCannotGetAround() {
        assertTrue(blacklist.isBlocked("/h", RESOLVER));
        assertTrue(blacklist.isBlocked("/essentials:home", RESOLVER));
        assertTrue(blacklist.isBlocked("/ESSENTIALS:H base", RESOLVER));
        assertTrue(blacklist.isBlocked("/someplugin:tpa Alex", RESOLVER), "unknown to the server but listed after the prefix");
    }

    @Test
    void whitelistAllowsOnlyListedCommands() {
        CommandRules whitelist = new CommandRules(CommandRules.Mode.WHITELIST, List.of("msg", "report"));
        assertFalse(whitelist.isBlocked("/msg Alex hi", RESOLVER));
        assertFalse(whitelist.isBlocked("/tell Alex hi", label -> label.equals("tell") ? List.of("msg", "tell", "w") : List.of()));
        assertFalse(whitelist.isBlocked("/report Steve fly", RESOLVER));
        assertTrue(whitelist.isBlocked("/home", RESOLVER));
        assertTrue(whitelist.isBlocked("/unknowncommand", RESOLVER));
    }

    @Test
    void theCombatCommandAlwaysWorks() {
        CommandRules whitelist = new CommandRules(CommandRules.Mode.WHITELIST, List.of("msg"));
        assertFalse(whitelist.isBlocked("/combat", RESOLVER));
        assertFalse(whitelist.isBlocked("/fightclock:combat", RESOLVER));
        CommandRules blocksEverything = new CommandRules(CommandRules.Mode.BLACKLIST, List.of("combat"));
        assertFalse(blocksEverything.isBlocked("/combat", RESOLVER));
    }

    @Test
    void emptyInputIsNeverBlocked() {
        assertFalse(blacklist.isBlocked("/", RESOLVER));
        assertFalse(blacklist.isBlocked("   ", RESOLVER));
        assertFalse(CommandRules.blockNothing().isBlocked("/home", RESOLVER));
    }

    @Test
    void labelAndNormalize() {
        assertEquals("home", CommandRules.label("/Home base"));
        assertEquals("essentials:home", CommandRules.label("  /essentials:home"));
        assertEquals("spawn", CommandRules.label("spawn"));
        assertEquals("home", CommandRules.normalize("Essentials:Home"));
        assertEquals("tpa", CommandRules.normalize("/tpa"));
    }
}
