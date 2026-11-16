#!/usr/bin/env bash
# End-to-end test of FightClock on a real Paper server (used by .github/workflows/server-test.yml).
#
#  1. downloads the newest Paper build of PAPER_LINE (e.g. 26.1 or 26.3) and installs the built plugin
#  2. shortens the fight timer to a few seconds and blocks /me, so the scenario has a command to test
#  3. starts the server and plays scenario.mjs with two bot players
#  4. stops the server and fails if the plugin logged a stack trace
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE="$ROOT/.github/server-test"
WORK="$ROOT/build/server-test"
PAPER_LINE=${PAPER_LINE:-26.3}
BOT_VERSION=${BOT_VERSION:-26.1}
FIGHT_SECONDS=${FIGHT_SECONDS:-6}
UA="FightClock-CI (+https://github.com/${GITHUB_REPOSITORY:-topdanya33200-netizen/FightClock})"
API=https://fill.papermc.io/v3/projects/paper

rm -rf "$WORK"
mkdir -p "$WORK/server/plugins/FightClock"
cd "$WORK"

echo "::group::Download Paper $PAPER_LINE"
VERSION=$(curl -fsS -H "User-Agent: $UA" "$API" \
  | jq -r --arg line "$PAPER_LINE" '.versions | to_entries[] | .value[] | select(. == $line or startswith($line + "."))' \
  | sort -V | tail -n 1)
[ -n "$VERSION" ] || { echo "No Paper version matches $PAPER_LINE"; exit 1; }
URL=$(curl -fsS -H "User-Agent: $UA" "$API/versions/$VERSION/builds" \
  | jq -r 'first(.[] | select(.channel == "STABLE") | .downloads."server:default".url) // .[0].downloads."server:default".url')
echo "Paper $VERSION: $URL"
curl -fsSL -H "User-Agent: $UA" -o server/paper.jar "$URL"
echo "::endgroup::"

echo "::group::Install plugins"
cp "$ROOT"/build/libs/FightClock-*.jar server/plugins/
if [[ "$VERSION" != "$BOT_VERSION" && "$VERSION" != "$BOT_VERSION".* ]]; then
  # The bots speak $BOT_VERSION; ViaVersion and ViaBackwards let them join newer servers
  gh release download --repo ViaVersion/ViaVersion --pattern 'ViaVersion-*.jar' --dir server/plugins
  gh release download --repo ViaVersion/ViaBackwards --pattern 'ViaBackwards-*.jar' --dir server/plugins
fi
ls server/plugins
unzip -p "$ROOT"/build/libs/FightClock-*.jar config.yml > server/plugins/FightClock/config.yml
sed -i \
  -e "s|^  duration-seconds: 15|  duration-seconds: $FIGHT_SECONDS|" \
  -e 's|^  list:|  list:\n    - me|' \
  server/plugins/FightClock/config.yml
grep -n -A2 "duration-seconds:\|^  list:" server/plugins/FightClock/config.yml
echo "eula=true" > server/eula.txt
cat > server/server.properties <<'EOF'
online-mode=false
enforce-secure-profile=false
white-list=false
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
pvp=true
motd=FightClock server test
EOF
echo "::endgroup::"

echo "::group::Start Paper $VERSION"
: > console.in
tail -n +1 -f "$WORK/console.in" | (cd server && exec java -Xms1G -Xmx2G \
  -Dterminal.jline=false -Dterminal.ansi=false \
  -jar paper.jar --nogui) > server.log 2>&1 &
SERVER_PID=$!
for _ in $(seq 1 300); do
  grep -q 'Done (' server.log && break
  kill -0 "$SERVER_PID" 2>/dev/null || { cat server.log; echo "::error::Paper exited during startup"; exit 1; }
  sleep 1
done
grep -q 'Done (' server.log || { tail -n 100 server.log; echo "::error::Paper did not start in time"; exit 1; }
# Newer versions enable the whitelist by default; the bots must be able to join
echo "whitelist off" >> console.in
for _ in $(seq 1 20); do grep -qi "whitelist is" server.log && break; sleep 0.5; done
grep -E "\[(FightClock|ViaVersion|ViaBackwards)\]|Done \(|[Ww]hitelist is" server.log || true
echo "::endgroup::"

echo "::group::Scenario"
(cd "$HERE" && npm install --no-audit --no-fund --loglevel=error)
set +e
SERVER_LOG="$WORK/server.log" CONSOLE_IN="$WORK/console.in" BOT_VERSION="$BOT_VERSION" FIGHT_SECONDS="$FIGHT_SECONDS" \
  timeout 400 node "$HERE/scenario.mjs"
STATUS=$?
set -e
echo "::endgroup::"

echo "::group::Stop Paper"
echo "stop" >> console.in
for _ in $(seq 1 60); do
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 1
done
kill "$SERVER_PID" 2>/dev/null || true
pkill -f "tail -n +1 -f $WORK/console.in" || true
grep "\[FightClock\]" server.log || true
echo "::endgroup::"

# Our package only appears in the log inside stack traces
if grep -n "\.fightclock\." server.log; then
  echo "::error::FightClock logged a stack trace, see server.log"
  STATUS=1
fi
if [ "$STATUS" -ne 0 ]; then
  echo "::group::Server log (warnings, errors and connections)"
  grep -n -i -E "WARN|ERROR|exception|lost connection|disconnect|kicked|left the game|joined the game|FightClock" server.log | head -n 250 || true
  echo "::endgroup::"
fi
exit "$STATUS"
