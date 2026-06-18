#!/usr/bin/env bash
# Run one deterministic benchmark of an Arclight server with the ArclightBench plugin,
# then print the BENCH-SUMMARY and a GC summary parsed from a -Xlog:gc file.
#
# Required env:
#   BENCH_JDK     path to the JDK to test (its bin/java is used to RUN the server)
#   ARCLIGHT_JAR  path to the built arclight-forge-<mc>-<ver>.jar
#   RUN_DIR       a server run directory (first run downloads Forge libs; needs network)
# Optional env:
#   LABEL         label for outputs / gc file (default = JDK folder name)
#   HEAP          -Xms/-Xmx value (default 2G)
#   JVM_EXTRA     extra JVM args (e.g. "-XX:+UseZGC -XX:+ZGenerational")
#   SEED          level-seed (default arclightbench2026)
#   Any bench.* via BENCH_ARGS, e.g. BENCH_ARGS="-Dbench.players=100 -Dbench.duration=60"
#   On a Zscaler/proxy machine, export JAVA_TOOL_OPTIONS with the proxy before the first run.
set -euo pipefail

: "${BENCH_JDK:?set BENCH_JDK}"; : "${ARCLIGHT_JAR:?set ARCLIGHT_JAR}"; : "${RUN_DIR:?set RUN_DIR}"
LABEL="${LABEL:-$(basename "$BENCH_JDK")}"
HEAP="${HEAP:-2G}"
SEED="${SEED:-arclightbench2026}"
JVM_EXTRA="${JVM_EXTRA:-}"
BENCH_ARGS="${BENCH_ARGS:-}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mkdir -p "$RUN_DIR/plugins"
cp "$HERE/ArclightBench.jar" "$RUN_DIR/plugins/"
# keep ONLY the bench plugin so nothing else perturbs the numbers
find "$RUN_DIR/plugins" -name '*.jar' ! -name 'ArclightBench.jar' -delete 2>/dev/null || true

cat > "$RUN_DIR/server.properties" <<EOF
level-seed=$SEED
difficulty=hard
spawn-monsters=true
max-tick-time=-1
view-distance=6
simulation-distance=6
online-mode=false
level-name=world
EOF
echo "eula=true" > "$RUN_DIR/eula.txt"

# fresh world so every run generates identically from the same seed
rm -rf "$RUN_DIR"/world "$RUN_DIR"/world_nether "$RUN_DIR"/world_the_end "$RUN_DIR"/crash-reports 2>/dev/null || true
# Use a RELATIVE gc log filename in -Xlog: the JVM runs with cwd=RUN_DIR, and a path
# embedded in -Xlog is NOT path-converted by Git Bash/MSYS (an absolute /c/... would fail).
GC_FILE="gc-$LABEL.log"
GC_LOG="$RUN_DIR/$GC_FILE"; rm -f "$GC_LOG"
LOG="$RUN_DIR/bench-$LABEL.log"

echo ">> Benchmark [$LABEL]  heap=$HEAP  extra='$JVM_EXTRA'  bench='$BENCH_ARGS'"
( cd "$RUN_DIR" && "$BENCH_JDK/bin/java" -Xms"$HEAP" -Xmx"$HEAP" \
    "-Xlog:gc:file=$GC_FILE:time,uptime" $JVM_EXTRA $BENCH_ARGS \
    -jar "$ARCLIGHT_JAR" nogui ) > "$LOG" 2>&1 || true

echo "--- results [$LABEL] ---"
grep -E "BENCH-PREGEN|BENCH-SETUP|BENCH-SUMMARY|Done \(" "$LOG" || echo "(no BENCH lines - check $LOG)"
if [ -f "$GC_LOG" ]; then
  pauses=$(grep -c "Pause" "$GC_LOG" || true)
  grep -oE "[0-9]+[.,][0-9]+ms" "$GC_LOG" | tr ',' '.' | sed 's/ms//' \
    | awk -v p="$pauses" '{s+=$1; if($1>m)m=$1; n++} END{if(n)printf "GC: %s pause-events, total=%.0fms max=%.1fms avg=%.1fms\n", p, s, m, s/n}'
fi
