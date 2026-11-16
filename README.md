# FightClock

[![Build](https://github.com/topdanya33200-netizen/FightClock/actions/workflows/build.yml/badge.svg)](https://github.com/topdanya33200-netizen/FightClock/actions/workflows/build.yml)
[![Server test](https://github.com/topdanya33200-netizen/FightClock/actions/workflows/server-test.yml/badge.svg)](https://github.com/topdanya33200-netizen/FightClock/actions/workflows/server-test.yml)
![Paper 26.x](https://img.shields.io/badge/Paper-26.x-blue)
![Folia](https://img.shields.io/badge/Folia-supported-brightgreen)
![Java 25](https://img.shields.io/badge/Java-25-orange)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Combat log protection for Paper and Folia servers.** A hit puts both players into a fight with a bossbar
countdown. Until it runs out they can't escape with commands, teleports, portals, elytra or flight, and
leaving the server kills them, with the kill credited to their enemy.

[Русская версия](README.ru.md)

## Features

- **Fights start on any player damage:** melee, arrows and tridents, end crystals and TNT, splash and lingering
  potions. Each kind can be switched off. The attacker is found through Paper's `DamageSource`, so the player who
  set off a crystal or fired the arrow is the one who enters the fight.
- **Respects protection plugins.** Hits cancelled by region, team or friendly-fire plugins never start a fight.
- **Countdown on screen:** a bossbar that drains and names the enemy, plus an action bar timer. Every new hit
  restarts the countdown.
- **Blocked commands** in blacklist or whitelist mode. Aliases and prefixed forms such as `/essentials:home`
  are recognised, so they can't be used to get around the list.
- **No escape routes:** teleports by command, nether and end portals, opening elytra and `/fly` are blocked
  during a fight. Ender pearls and chorus fruit keep working.
- **Longer item cooldowns** in a fight, for example 15 seconds on ender pearls and 60 on enchanted golden apples.
- **Leaving a fight kills you.** It's a real death: items drop, totems don't help, and the kill counts for the
  enemy in statistics and scoreboards. Everyone sees "Steve left a fight with Alex and was killed", and the
  player is told why when they come back.
- **Fair about kicks.** An admin's `/kick`, bans and restarts are excused. Every other kick is punished, so a
  cheat can't kick itself out of a fight with a bad packet.
- **Admin tools:** check, start and end fights, list everyone who is fighting.
- **PlaceholderAPI** placeholders and **developer events** to cancel tags, react to fights ending or excuse a
  logout.
- **Folia ready.** Every player is handled on the thread that owns it, and fight state is thread-safe.
- **English and Russian** messages out of the box. Every message is editable.

## Installation

1. Download `FightClock-<version>.jar` from [Releases](https://github.com/topdanya33200-netizen/FightClock/releases)
   and put it into `plugins/`.
2. Start the server once, then edit `plugins/FightClock/config.yml`.
3. Run `/combat reload`.

Requires Paper or Folia 26.1+ and Java 25. PlaceholderAPI is optional.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/combat` | `fightclock.status` (everyone) | Your fight timer and enemy |
| `/combat status <player>` | `fightclock.admin` | Another player's fight |
| `/combat list` | `fightclock.admin` | Everyone in a fight right now |
| `/combat tag <player> [seconds]` | `fightclock.admin` | Put a player into a fight |
| `/combat untag <player>` | `fightclock.admin` | End a player's fight without punishment |
| `/combat reload` | `fightclock.admin` | Reload config and messages |

`/fightclock` works as an alias.

| Permission | Default | Meaning |
|---|---|---|
| `fightclock.bypass` | nobody | Never enters a fight |
| `fightclock.bypass.commands` | nobody | Can use blocked commands during a fight |

Ops are not exempt by default, so the plugin works the same when you test it as an op.

## Configuration

The full file with comments is created on first start. The parts most servers change:

```yaml
combat:
  duration-seconds: 15        # every hit restarts the countdown
  triggers: { melee: true, projectiles: true, explosions: true, potions: true }
  disabled-worlds: []

commands:
  mode: blacklist             # or whitelist
  list: [spawn, home, warp, back, tpa, tpaccept, rtp, ec, kit, hub]   # the default list is longer

restrictions:
  blocked-teleports: [COMMAND, NETHER_PORTAL, END_PORTAL, END_GATEWAY]
  block-elytra: true
  disable-flight: true
  item-cooldowns: { ENDER_PEARL: 15, CHORUS_FRUIT: 10, ENCHANTED_GOLDEN_APPLE: 60 }

logout:
  kill: true
  safe-kick-causes: [KICKED, BANNED, IP_BANNED, WHITELIST, RESTART_COMMAND]
  commands: []                # e.g. "tempban {player} 10m Left a fight"
```

## Placeholders

With PlaceholderAPI installed: `%fightclock_in_combat%` (`true`/`false`), `%fightclock_time_left%` (`12s`),
`%fightclock_seconds_left%` (`12`), `%fightclock_enemy%` and `%fightclock_fighters%` (players in a fight right now).
They are read from memory, so a scoreboard can refresh them every tick.

## For developers

Three events, all fired on the thread that owns the player:

| Event | Cancellable | When |
|---|---|---|
| `CombatTagEvent` | yes | Before a player enters a fight or has the countdown restarted. Change the length with `setDuration`. |
| `CombatEndEvent` | no | After a fight ended: `EXPIRED`, `DEATH`, `LOGOUT` or `COMMAND`. |
| `CombatLogoutEvent` | yes | A player is leaving mid-fight. Cancel to excuse them, e.g. to spawn an NPC instead. |

```java
@EventHandler
public void onTag(CombatTagEvent event) {
    if (event.getEnemy() != null && teams.sameTeam(event.getPlayer(), event.getEnemy())) {
        event.setCancelled(true); // teammates don't fight
    }
}
```

## Building from source

```bash
./gradlew build       # plugin jar in build/libs/, runs the unit tests
./gradlew runServer   # starts a local Paper 26.3 test server with the plugin installed
```

JDK 25 is required; Gradle downloads it automatically if it is missing.

## How it is built

```
src/main/java/.../fightclock
├── api/        events for other plugins
├── combat/     fight tracker, damage attribution, countdown HUD, punishment, logout notices
├── command/    /combat (Brigadier) and the command block rules
├── config/     typed config snapshot and MiniMessage language files
├── hook/       PlaceholderAPI expansion
├── listener/   damage, death, quit and restriction listeners
└── util/       Folia-safe scheduling, time formatting
```

- **Threading:** fight state lives in a `ConcurrentHashMap` updated with atomic `compute` calls, so any thread can
  read it. Everything that touches a player runs on that player's own scheduler; when a hit crosses a Folia region
  border, the attacker is tagged on their own region's thread.
- **The punishment** is a `generic_kill` damage caused by the last enemy: the one damage type that totems can't
  stop, and one that credits the kill. If another plugin blocks it, the player's health is set to zero instead.
  On Folia the kill is only credited when the enemy is in the same region at that moment, to stay thread safe.
- **Unit tests** (JUnit) cover the fight tracker, the command rules with aliases and prefixes, config parsing,
  message rendering in both languages and the warning throttle.
- **End-to-end test on real servers:** on every push, GitHub Actions starts Paper 26.1 and 26.3 and makes two bot
  players fight. It checks the start and end of a fight, the bossbar and action bar, blocked commands and teleports,
  admin commands, the logout death with its message and kill credit, the notice on return, an excused kick and the
  ender pearl cooldown. See [`.github/server-test`](.github/server-test).

## License

[MIT](LICENSE)
