#!/usr/bin/env bash
# Một D-Bus riêng cho từng ca kiểm; không điều khiển trình phát nhạc thật của người dùng.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [ "${NOS_QA_BUS:-0}" != 1 ]; then
  exec env NOS_QA_BUS=1 dbus-run-session -- bash "$0" "$@"
fi
OUT=$(realpath -m "$1"); shift
if [ -e "$OUT/complete.txt" ] || [ -e "$OUT/failed.txt" ]; then
  echo 'Choose a new output directory for each verification run' >&2
  exit 1
fi
VERSION=$(sed -n 's/^version=//p' "$ROOT/gradle.properties")
FIXTURES="$ROOT/build/verification/fixtures"
mapfile -t NAMES < <(python3 "$ROOT/checks/game/fixtures.py" "$FIXTURES")
python3 "$ROOT/checks/fakempris.py" 'Never Gonna Give You Up' 'Rick Astley' 3600 15 > "$FIXTURES/player.log" 2>&1 & PLAYER_PID=$!
trap 'kill "$PLAYER_PID" 2>/dev/null || true' EXIT
export NOS_JAR="${NOS_JAR:-$ROOT/build/libs/nosclient-$VERSION.jar}"
export NOS_VERIFIER_JAR="$ROOT/build/verification/nosclient-$VERSION-verification.jar"
export NOS_VERIFY_OUT="$OUT"
export NOS_JVM="${NOS_JVM:-} -Dnosclient.probe=true -Dmixin.debug.verify=true -Dnos.verify.output=$OUT -Dnos.verify.mode=${NOS_VERIFY_MODE:-visual} -Dnos.verify.scene=${NOS_VERIFY_SCENE:-off} -Dnos.verify.quick=${NOS_VERIFY_QUICK:-false}"
export NOS_CONFIG="nosclient.json=$FIXTURES/nosclient.json nosclient.properties=$FIXTURES/nosclient.properties nosclient-lyrics/cache/${NAMES[0]}=$FIXTURES/words.json"
if [ "${NOS_VERIFY_BASELINE:-0}" = 1 ]; then
  NOS_CONFIG="$NOS_CONFIG nosclient-lyrics/${NAMES[1]}=$FIXTURES/words.lrc"
fi
export SETTLE=1
bash "$ROOT/checks/nosrun.sh" "$OUT" "$@"
