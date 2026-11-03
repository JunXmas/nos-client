#!/usr/bin/env bash
# Nos client in a real Fabric 1.21.11 with isolated config/game directories and an offline account.
#   checks/nosrun.sh <out dir> [--fo] [--world <save dir>] [-- extra game args]
# Mods: the freshly built jar + fabric-api (+ every Fabulously Optimized mod with --fo, copied
# read-only from jun's instance). Writes <out>/latest.log, <out>/run.log, screenshots via $SHOT.
# Steps after load: $STEPS is a file of lines "sleep N" | "key K" | "type TEXT" | "shot NAME".
set -euo pipefail
OUT=$(realpath -m "$1"); shift
FO=0; WORLD=""; EXTRA=()
while [ $# -gt 0 ]; do
  case "$1" in
    --fo) FO=1 ;;
    --world) WORLD="$2"; shift ;;
    --) shift; EXTRA=("$@"); break ;;
  esac
  shift
done
N=${NOSTALGIA:-$HOME/.local/share/nostalgia}
SRC=$N/instances/optimized-1.21.11
ROOT=$(cd "$(dirname "$0")/.." && pwd)
JAR=${NOS_JAR:-$(ls -t "$ROOT"/build/libs/*.jar | grep -v sources | head -1)}; echo "jar: $JAR"
mkdir -p "$OUT"; G=$(mktemp -d); FAKEHOME=$(mktemp -d)
mkdir -p "$G/mods" "$G/saves" "$FAKEHOME/runtime"
chmod 700 "$FAKEHOME/runtime"
if [ $FO = 1 ]; then
  cp "$SRC"/mods/*.jar "$G/mods/"; rm -f "$G"/mods/nos-client-*.jar "$G"/mods/nosclient-*.jar
  cp -r "$SRC/config" "$G/" 2>/dev/null || true
else
  cp "$SRC"/mods/fabric-api-*.jar "$G/mods/"
fi
cp "$JAR" "$G/mods/"
if [ "${NOS_DISABLE_IRIS:-0}" = 1 ]; then rm -f "$G"/mods/iris-*.jar; fi
if [ -n "${NOS_VERIFIER_JAR:-}" ]; then cp "$NOS_VERIFIER_JAR" "$G/mods/"; fi
mkdir -p "$G/config"
# Preset config files: NOS_CONFIG="<file in config/>=<local path> ..."
for pair in ${NOS_CONFIG:-}; do mkdir -p "$(dirname "$G/config/${pair%%=*}")"; cp "${pair#*=}" "$G/config/${pair%%=*}"; done
printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nnarrator:0\nenableVsync:false\nmaxFps:260\nrenderDistance:8\nsimulationDistance:5\ntutorialStep:none\nlang:%s\nsoundCategory_master:0.0\n' "${NOS_LANG:-vi_vn}" > "$G/options.txt"
if [ -n "${NOS_SHADERPACK:-}" ]; then
  mkdir -p "$G/shaderpacks"
  cp "$NOS_SHADERPACK" "$G/shaderpacks/"
  if [ -n "${NOS_SHADER_OPTIONS:-}" ]; then
    printf '%s\n' "$NOS_SHADER_OPTIONS" > "$G/shaderpacks/$(basename "$NOS_SHADERPACK").txt"
  fi
  printf 'enableShaders=true\nshaderPack=%s\n' "$(basename "$NOS_SHADERPACK")" > "$G/config/iris.properties"
fi
if [ -n "$WORLD" ]; then cp -r "$WORLD" "$G/saves/"; EXTRA+=(--quickPlaySingleplayer "$(basename "$WORLD")"); fi
cp "$G/options.txt" "$OUT/options-before.txt"
sha256sum "$JAR" > "$OUT/artifact.sha256"
DISPLAY_NO=${DISPLAY_NO:-97}
Xvfb ":$DISPLAY_NO" -screen 0 1280x720x24 >/dev/null 2>&1 & XV=$!
cleanup() { kill "${GP:-}" 2>/dev/null || true; sleep 2; kill -9 "${GP:-}" 2>/dev/null || true; kill $XV 2>/dev/null || true; cp "$G/logs/latest.log" "$OUT/" 2>/dev/null || true; cp -r "$G/config" "$OUT/config" 2>/dev/null || true; cp -r "$G/crash-reports" "$OUT/" 2>/dev/null || true; cp "$G"/hs_err_pid*.log "$OUT/" 2>/dev/null || true; cp "$G/options.txt" "$OUT/options.txt" 2>/dev/null || true; rm -rf "$G" "$FAKEHOME"; }
trap cleanup EXIT
sleep 1
mapfile -t ARGV < <(python3 "$ROOT/checks/fabric_argv.py" "$G" "${EXTRA[@]}" | python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)))')
cd "$G"
XDG_CONFIG_HOME=$FAKEHOME/.config XDG_DATA_HOME=$FAKEHOME/.local/share \
  XDG_CACHE_HOME=$FAKEHOME/.cache XDG_RUNTIME_DIR=$FAKEHOME/runtime \
  DISPLAY=":$DISPLAY_NO" LIBGL_ALWAYS_SOFTWARE="${NOS_SOFTWARE:-1}" MESA_GL_VERSION_OVERRIDE=4.6 \
  "${ARGV[0]}" "-Duser.home=$FAKEHOME" "${ARGV[@]:1}" > "$OUT/run.log" 2>&1 & GP=$!
for _ in $(seq 1 ${LOAD_WAIT:-240}); do
  grep -qE 'Created: .*minecraft:textures/atlas/gui|Loaded [0-9]+ advancements|Stopping!' "$OUT/run.log" 2>/dev/null && break
  kill -0 $GP 2>/dev/null || break; sleep 1
done
if [ -n "${NOS_VERIFY_OUT:-}" ]; then
  for _ in $(seq 1 600); do
    [ -f "$NOS_VERIFY_OUT/complete.txt" ] && break
    [ -f "$NOS_VERIFY_OUT/failed.txt" ] && break
    kill -0 $GP 2>/dev/null || break
    sleep 1
  done
  for _ in $(seq 1 30); do
    kill -0 $GP 2>/dev/null || break
    sleep 1
  done
  if [ -f "$NOS_VERIFY_OUT/failed.txt" ] || [ ! -f "$NOS_VERIFY_OUT/complete.txt" ]; then
    if ! kill -0 "$GP" 2>/dev/null; then
      if wait "$GP"; then echo 'Minecraft exit code: 0'; else echo "Minecraft exit code: $?"; fi
    fi
    echo 'Minecraft verification failed or did not finish' >&2
    exit 1
  fi
fi
sleep ${SETTLE:-20}
if [ -n "${STEPS:-}" ]; then
  while read -r op arg; do
    case "$op" in
      sleep) sleep "$arg" ;;
      key) DISPLAY=":$DISPLAY_NO" xdotool key --clearmodifiers "$arg" ;;
      type) DISPLAY=":$DISPLAY_NO" xdotool type --delay 60 "$arg" ;;
      shot) DISPLAY=":$DISPLAY_NO" import -window root "$OUT/$arg.png" ;;
    esac
  done < "$STEPS"
else
  DISPLAY=":$DISPLAY_NO" import -window root "$OUT/screen.png"
fi
