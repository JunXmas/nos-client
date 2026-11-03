#!/usr/bin/env bash
# Chỉ thao tác trình phát giả trên D-Bus riêng. Cần writeVerificationClasspath trước khi chạy.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [ "${NOS_MEDIA_QA_BUS:-0}" != 1 ]; then
  exec env NOS_MEDIA_QA_BUS=1 dbus-run-session -- bash "$0" "$@"
fi
OUT=$(realpath -m "$1")
mkdir -p "$OUT/classes"
CP=$(cat "$ROOT/build/verification/client-classpath.txt")
javac -proc:none -cp "$CP" -d "$OUT/classes" "$ROOT/checks/MediaEnvCheck.java" "$ROOT/checks/MprisControlCheck.java"
python3 "$ROOT/checks/fakempris.py" 'Control fixture' 'Nos' 180 15 > "$OUT/player.log" 2>&1 &
PLAYER_PID=$!
trap 'kill "$PLAYER_PID" 2>/dev/null || true' EXIT
sleep 1
java -cp "$OUT/classes:$CP" MprisControlCheck > "$OUT/controls.log" 2>&1
if [ -n "${NOS_VERIFY_LAUNCHER_LIB:-}" ]; then
  LD_LIBRARY_PATH="$NOS_VERIFY_LAUNCHER_LIB" java -cp "$OUT/classes:$CP" \
    dev.jun.nosclient.media.MediaEnvCheck > "$OUT/launcher-env.log" 2>&1
fi
echo 'Media controls passed on the private bus'
