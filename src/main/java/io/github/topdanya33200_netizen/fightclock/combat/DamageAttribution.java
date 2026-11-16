package io.github.topdanya33200_netizen.fightclock.combat;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jspecify.annotations.Nullable;

/**
 * Works out which player is behind a hit, using Paper's {@link DamageSource}: its causing entity
 * is the shooter of an arrow, the player who hit an end crystal, the one who lit the TNT or threw
 * the potion. One code path covers all of them.
 */
public final class DamageAttribution {

    /** How the damage reached the victim, matching {@code combat.triggers} in the config. */
    public enum Kind {
        MELEE,
        PROJECTILE,
        EXPLOSION,
        POTION,
        OTHER
    }

    public record Hit(Player attacker, Kind kind) {
    }

    private DamageAttribution() {
    }

    /** The player who caused this damage, or {@code null} if it wasn't a player. */
    public static @Nullable Hit of(EntityDamageByEntityEvent event) {
        DamageSource source = event.getDamageSource();
        Entity direct = source.getDirectEntity();
        if (direct == null) {
            direct = event.getDamager();
        }
        Entity causing = source.getCausingEntity();
        if (causing == null && direct instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            causing = shooter;
        }
        if (causing == null) {
            causing = direct;
        }
        if (!(causing instanceof Player attacker)) {
            return null;
        }
        return new Hit(attacker, kind(direct, attacker));
    }

    static Kind kind(@Nullable Entity direct, Player attacker) {
        if (direct == null || direct.equals(attacker)) {
            return Kind.MELEE;
        }
        if (direct instanceof EnderCrystal || direct instanceof TNTPrimed || direct instanceof ExplosiveMinecart) {
            return Kind.EXPLOSION;
        }
        // Potions are projectiles too, so they are checked first
        if (direct instanceof ThrownPotion || direct instanceof AreaEffectCloud) {
            return Kind.POTION;
        }
        if (direct instanceof Projectile) {
            return Kind.PROJECTILE;
        }
        return Kind.OTHER;
    }
}
