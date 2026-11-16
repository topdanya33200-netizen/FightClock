package io.github.topdanya33200_netizen.fightclock;

/**
 * Permission nodes, also declared with their defaults in {@code plugin.yml}.
 */
public final class Permissions {

    /** {@code /combat}: your own fight timer. Everyone by default. */
    public static final String STATUS = "fightclock.status";
    /** Check, start and end other players' fights, list fights, reload. Ops by default. */
    public static final String ADMIN = "fightclock.admin";
    /** Never enter a fight. Nobody by default, not even ops. */
    public static final String BYPASS = "fightclock.bypass";
    /** Use blocked commands during a fight. Nobody by default. */
    public static final String BYPASS_COMMANDS = "fightclock.bypass.commands";

    private Permissions() {
    }
}
