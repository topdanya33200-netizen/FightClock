// End-to-end scenario for FightClock on a real Paper server.
//
// Two bot players (mineflayer) fight while the server console is checked along the way:
// the fight timer and its HUD, blocked commands and teleports, admin commands, logging out
// mid-fight (death, death message, kill credit, notice on return), an excused kick and the
// longer ender pearl cooldown. Run by run.sh; exits non-zero on failure.
import fs from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const SERVER_LOG = process.env.SERVER_LOG
const CONSOLE_IN = process.env.CONSOLE_IN
const BOT_VERSION = process.env.BOT_VERSION ?? '26.1'
const FIGHT_SECONDS = Number(process.env.FIGHT_SECONDS ?? 6)
const MOVEMENT_PACKETS = new Set(['position', 'position_look', 'look', 'flying'])
// Players can't be hurt for 3 seconds after joining or respawning
const SPAWN_PROTECTION_MS = 4000
const PEARL_COOLDOWN_TICKS = 15 * 20

const results = []
function check (name, ok, detail = '') {
  results.push({ name, ok: Boolean(ok) })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`)
}

const stripAnsi = (text) => text.replace(/\x1b\[[0-9;]*[A-Za-z]/g, '')
const logSize = () => fs.statSync(SERVER_LOG).size
const readLog = (from = 0) => stripAnsi(fs.readFileSync(SERVER_LOG).subarray(from).toString('utf8'))

async function waitFor (probe, timeout = 15000) {
  const end = Date.now() + timeout
  while (Date.now() < end) {
    const value = probe()
    if (value) return value
    await sleep(200)
  }
  return null
}

/** Types a command into the server console and waits until its output matches. */
async function consoleCommand (command, expected, timeout = 15000) {
  const from = logSize()
  fs.appendFileSync(CONSOLE_IN, command + '\n')
  return waitFor(() => {
    const output = readLog(from)
    return expected.test(output) ? output : null
  }, timeout)
}

function joinBot (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({
      host: '127.0.0.1', port: 25565, username, auth: 'offline', version: BOT_VERSION, physicsEnabled: false
    })
    // The bots never move, so movement packets are not sent at all: translated for newer
    // servers they can be rejected as "Invalid move player packet"
    const write = bot._client.write.bind(bot._client)
    bot._client.write = (name, params) => {
      if (!MOVEMENT_PACKETS.has(name)) write(name, params)
    }
    bot.received = []
    bot.actionBars = []
    bot.bars = new Map() // mineflayer already has bot.bossBars
    bot.cooldowns = []
    bot.on('messagestr', (message, position) => {
      if (position === 'game_info') return // action bar, recorded below
      bot.received.push(message)
      console.log(`      [${username} sees] ${message.replace(/\n/g, ' | ')}`)
    })
    bot.on('actionBar', (message) => bot.actionBars.push(message.toString()))
    const showBar = (bar) => bot.bars.set(bar.entityUUID, bar.title.toString())
    bot.on('bossBarCreated', showBar)
    bot.on('bossBarUpdated', showBar)
    bot.on('bossBarDeleted', (bar) => bot.bars.delete(bar.entityUUID))
    bot._client.on('set_cooldown', (packet) => bot.cooldowns.push(packet))
    const timer = setTimeout(() => reject(new Error(`${username} did not spawn in time`)), 60000)
    bot.once('spawn', () => {
      clearTimeout(timer)
      bot.on('kicked', (reason) => console.log(`      [${username} kicked] ${JSON.stringify(reason)}`))
      bot.on('error', (error) => console.log(`      [${username} error] ${error?.stack ?? error}`))
      bot.on('end', (reason) => console.log(`      [${username} disconnected] ${reason}`))
      resolve(bot)
    })
    bot.once('kicked', (reason) => reject(new Error(`${username} was kicked: ${JSON.stringify(reason)}`)))
    bot.once('error', reject)
  })
}

/** Only messages after the last mark() count, so repeated phases don't match old messages. */
const mark = (bot) => { bot.from = bot.received.length }
const received = (bot, pattern, timeout = 15000) =>
  waitFor(() => bot.received.slice(bot.from ?? 0).find((message) => pattern.test(message)), timeout)
const notReceived = async (bot, pattern, wait = 2000) => {
  await sleep(wait)
  return !bot.received.slice(bot.from ?? 0).some((message) => pattern.test(message))
}

function hit (attacker, victimName) {
  const target = attacker.players[victimName]?.entity
  if (!target) throw new Error(`${attacker.username} can't see ${victimName}`)
  attacker.attack(target)
}

/**
 * Players spawn at random spots around the world spawn, often too far apart to hit each other,
 * so Steve is teleported to Alex before a fight.
 */
async function together (alex, steve) {
  check('console: bring Steve to Alex', await consoleCommand('tp Steve Alex', /Teleported Steve to Alex/))
  await sleep(500)
  const distance = alex.entity.position.distanceTo(alex.players.Steve?.entity?.position ?? alex.entity.position)
  console.log(`      [distance] Alex and Steve are ${distance.toFixed(2)} blocks apart`)
}

async function main () {
  check('plugin enabled with the test config',
    new RegExp(`\\[FightClock\\] Fights last ${FIGHT_SECONDS}s`).test(readLog()))
  check('console: kill counter objective', await consoleCommand('scoreboard objectives add kills playerKillCount', /Created new objective/))

  const alex = await joinBot('Alex')
  let steve = await joinBot('Steve')
  await together(alex, steve)
  await sleep(SPAWN_PROTECTION_MS)

  // --- a fight starts
  steve.chat('/combat')
  check('player: /combat outside a fight', await received(steve, /You are not in a fight/))

  mark(alex); mark(steve)
  hit(alex, 'Steve')
  check('fight: the player who was hit enters the fight', await received(steve, /You are in a fight with Alex!/))
  console.log(`      [health] Steve has ${steve.health} health after the hit`)
  check('fight: the attacker enters the fight', await received(alex, /You are in a fight with Steve!/))
  check('hud: bossbar names the enemy and counts down',
    await waitFor(() => [...steve.bars.values()].find((title) => /Fight with Alex · \d+s/.test(title))),
    [...steve.bars.values()].join(' / '))
  check('hud: action bar countdown', await waitFor(() => steve.actionBars.find((text) => /In a fight: \d+s/.test(text))))

  steve.chat('/combat')
  check('player: /combat shows the enemy and the time left', await received(steve, /You are in a fight with Alex\. Wait \d+s before logging out/))
  check('console: status of a player', await consoleCommand('combat status Steve', /Steve is in a fight with Alex, \d+s left/))
  const list = await consoleCommand('combat list', /Players in a fight \(2\)/)
  check('console: list shows both fighters', list && /Alex vs Steve/.test(list) && /Steve vs Alex/.test(list))

  // --- what is blocked during a fight
  steve.chat('/me hello')
  check('restriction: a blocked command is refused', await received(steve, /You can't use \/me during a fight/))
  steve.chat('/minecraft:me hello again')
  check('restriction: a plugin prefix does not get around it', await received(steve, /You can't use \/minecraft:me during a fight/))
  check('restriction: the blocked command did not run', await notReceived(alex, /\* Steve hello/, 1000))

  mark(steve)
  hit(alex, 'Steve') // keeps the fight going
  await sleep(500)
  check('fight: a new hit does not repeat the start message', await notReceived(steve, /You are in a fight with Alex!/, 500))
  fs.appendFileSync(CONSOLE_IN, 'tp Steve 0 120 0\n')
  check('restriction: teleporting by command is blocked', await received(steve, /You can't teleport during a fight/))

  // --- the fight runs out
  check('fight: ends when the countdown runs out', await received(steve, /You are no longer in a fight/, (FIGHT_SECONDS + 5) * 1000))
  check('fight: ends for the attacker too', await received(alex, /You are no longer in a fight/, 5000))
  check('hud: bossbar removed', await waitFor(() => steve.bars.size === 0, 3000))
  mark(alex)
  steve.chat('/me is free')
  check('restriction: commands work again after the fight', await received(alex, /\* Steve is free/))

  // --- admin commands
  mark(steve)
  check('console: tag a player', await consoleCommand('combat tag Steve 30', /Steve is now in a fight for 30s/))
  check('player: told about the admin tag', await received(steve, /You are in a fight!/))
  check('console: untag a player', await consoleCommand('combat untag Steve', /Steve is no longer in a fight/))
  check('player: told the fight is over', await received(steve, /You are no longer in a fight/))
  check('console: untag someone not fighting', await consoleCommand('combat untag Steve', /Steve is not in a fight/))
  check('console: unknown player', await consoleCommand('combat status Nobody', /Nobody is not online/))
  check('console: /combat without arguments', await consoleCommand('combat', /From the console use \/combat status <player> or \/combat list/))
  check('console: reload', await consoleCommand('combat reload', /Configuration and messages reloaded/))

  // --- logging out during a fight
  mark(alex); mark(steve)
  hit(alex, 'Steve')
  check('logout: fight started before leaving', await received(steve, /You are in a fight with Alex!/))
  let from = logSize()
  steve.quit()
  check('logout: the player is killed with a death message naming the enemy',
    await waitFor(() => /Steve left a fight with Alex and was killed\./.test(readLog(from))))
  check('logout: other players see the death message', await received(alex, /Steve left a fight with Alex and was killed\./))
  check('logout: the plugin logs the punishment', /\[FightClock\] Steve left a fight with Alex and was killed/.test(readLog(from)))
  check('logout: the kill counts for the enemy', await consoleCommand('scoreboard players get Alex kills', /Alex has 1 \[kills\]/))

  steve = await joinBot('Steve')
  check('logout: told why they died when they come back', await received(steve, /You were killed because you left a fight with Alex\./))
  await together(alex, steve)
  await sleep(SPAWN_PROTECTION_MS)

  // --- an admin's kick is excused
  mark(steve)
  hit(alex, 'Steve')
  check('kick: fight started before the kick', await received(steve, /You are in a fight with Alex!/))
  from = logSize()
  check('kick: an admin kick is not punished',
    await consoleCommand('kick Steve', /\[FightClock\] Steve was kicked during a fight \(KICKED\) and was not punished/))
  await sleep(1000)
  check('kick: no death message', !/Steve left a fight/.test(readLog(from)))
  steve = await joinBot('Steve')
  check('kick: no notice after an excused kick', await notReceived(steve, /You were killed/, 2500))
  await together(alex, steve)

  // --- item cooldowns during a fight
  check('console: give ender pearls', await consoleCommand('give Steve ender_pearl 4', /Gave 4 \[Ender Pearl\] to Steve/))
  const pearl = await waitFor(() => steve.inventory.items().find((item) => item.name === 'ender_pearl'))
  if (pearl) await steve.equip(pearl, 'hand')
  await sleep(SPAWN_PROTECTION_MS)
  mark(steve)
  hit(alex, 'Steve')
  check('cooldown: fight started', await received(steve, /You are in a fight with Alex!/))
  steve.cooldowns.length = 0
  steve.activateItem()
  const cooldown = await waitFor(() => steve.cooldowns.find((packet) => packet.cooldownTicks === PEARL_COOLDOWN_TICKS), 5000)
  check('cooldown: ender pearls get the longer cooldown during a fight', cooldown,
    JSON.stringify(steve.cooldowns))

  // End the fights first so leaving doesn't kill anyone
  await consoleCommand('combat untag Alex', /Alex is (no longer|not) in a fight/)
  await consoleCommand('combat untag Steve', /Steve is (no longer|not) in a fight/)
  alex.quit()
  steve.quit()
  await sleep(1000)
}

try {
  await main()
} catch (error) {
  check('scenario finished without errors', false, error?.stack ?? String(error))
}
const failed = results.filter((result) => !result.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
