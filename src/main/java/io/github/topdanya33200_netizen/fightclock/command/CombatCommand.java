package io.github.topdanya33200_netizen.fightclock.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.topdanya33200_netizen.fightclock.FightClockPlugin;
import io.github.topdanya33200_netizen.fightclock.Permissions;
import io.github.topdanya33200_netizen.fightclock.api.CombatEndEvent;
import io.github.topdanya33200_netizen.fightclock.api.CombatTagEvent;
import io.github.topdanya33200_netizen.fightclock.combat.Fight;
import io.github.topdanya33200_netizen.fightclock.config.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * {@code /combat} for players and its admin subcommands.
 */
public final class CombatCommand {

    private static final int MAX_TAG_SECONDS = 3600;

    private final FightClockPlugin plugin;

    public CombatCommand(FightClockPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        Predicate<CommandSourceStack> admin = source -> source.getSender().hasPermission(Permissions.ADMIN);
        return Commands.literal("combat")
                .requires(source -> source.getSender().hasPermission(Permissions.STATUS))
                .executes(this::self)
                .then(Commands.literal("status")
                        .requires(admin)
                        .then(player().executes(this::status)))
                .then(Commands.literal("list")
                        .requires(admin)
                        .executes(this::list))
                .then(Commands.literal("tag")
                        .requires(admin)
                        .then(player()
                                .executes(context -> tag(context, plugin.settings().duration()))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_TAG_SECONDS))
                                        .executes(context -> tag(context,
                                                Duration.ofSeconds(IntegerArgumentType.getInteger(context, "seconds")))))))
                .then(Commands.literal("untag")
                        .requires(admin)
                        .then(player().executes(this::untag)))
                .then(Commands.literal("reload")
                        .requires(admin)
                        .executes(this::reload))
                .build();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> player() {
        return Commands.argument("player", StringArgumentType.word()).suggests(this::suggestPlayers);
    }

    private Messages messages() {
        return plugin.messages();
    }

    private int self(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            messages().send(sender, "admin.console-usage");
            return Command.SINGLE_SUCCESS;
        }
        Optional<Fight> fight = plugin.combat().fight(player.getUniqueId());
        if (fight.isEmpty()) {
            messages().send(player, "status.self-out");
        } else {
            Fight current = fight.get();
            messages().send(player, current.hasEnemy() ? "status.self-in" : "status.self-in-no-enemy",
                    enemy(current), messages().timeLeft(current.remaining(Instant.now())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Player target = target(context);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Fight> fight = plugin.combat().fight(target.getUniqueId());
        if (fight.isEmpty()) {
            messages().send(sender, "status.other-out", Placeholder.unparsed("player", target.getName()));
        } else {
            Fight current = fight.get();
            messages().send(sender, current.hasEnemy() ? "status.other-in" : "status.other-in-no-enemy",
                    Placeholder.unparsed("player", target.getName()), enemy(current),
                    messages().timeLeft(current.remaining(Instant.now())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Map<UUID, Fight> running = plugin.combat().running();
        if (running.isEmpty()) {
            messages().send(sender, "status.list-empty");
            return Command.SINGLE_SUCCESS;
        }
        Instant now = Instant.now();
        List<Map.Entry<UUID, Fight>> entries = new ArrayList<>(running.entrySet());
        entries.sort(Comparator.comparing((Map.Entry<UUID, Fight> entry) -> entry.getValue().expires()).reversed());
        messages().send(sender, "status.list-header", Placeholder.unparsed("count", Integer.toString(entries.size())));
        for (Map.Entry<UUID, Fight> entry : entries) {
            Fight fight = entry.getValue();
            Player online = Bukkit.getPlayer(entry.getKey());
            String name = online != null ? online.getName() : entry.getKey().toString();
            messages().send(sender, fight.hasEnemy() ? "status.list-entry" : "status.list-entry-no-enemy",
                    Placeholder.unparsed("player", name), enemy(fight), messages().timeLeft(fight.remaining(now)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int tag(CommandContext<CommandSourceStack> context, Duration duration) {
        CommandSender sender = context.getSource().getSender();
        Player target = target(context);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        plugin.tasks().entity(target, () -> {
            if (plugin.combat().tag(target, null, CombatTagEvent.Cause.COMMAND, duration)) {
                messages().send(sender, "admin.tagged", Placeholder.unparsed("player", target.getName()),
                        messages().timeLeft(duration));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int untag(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Player target = target(context);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        plugin.tasks().entity(target, () -> {
            boolean wasFighting = plugin.combat().end(target, CombatEndEvent.Reason.COMMAND, true);
            messages().send(sender, wasFighting ? "admin.untagged" : "status.other-out",
                    Placeholder.unparsed("player", target.getName()));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        try {
            plugin.reload();
            messages().send(sender, "admin.reloaded");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Reloading FightClock failed", exception);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** The online player named in the command, or {@code null} after telling the sender. */
    private @Nullable Player target(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "player");
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            messages().send(context.getSource().getSender(), "admin.not-online", Placeholder.unparsed("player", name));
        }
        return target;
    }

    private static TagResolver enemy(Fight fight) {
        return Placeholder.unparsed("enemy", fight.hasEnemy() ? fight.enemyName() : "");
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(online.getName());
            }
        }
        return builder.buildFuture();
    }
}
