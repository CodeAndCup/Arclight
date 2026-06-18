#!/usr/bin/env bash
# Build the ArclightBench load-generator plugin.
#
# Prereqs: the main Arclight build must have run at least once so that
# org.spigotmc:spigot-api:<mc>-R0.1-SNAPSHOT is installed in your local Maven repo
# (BuildTools installs it). Override paths with env vars if needed.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

JAVA_HOME="${BENCH_JDK:-${JAVA_HOME:?set JAVA_HOME or BENCH_JDK to a JDK 17}}"
MC_VERSION="${MC_VERSION:-1.20.1}"
M2="${M2_REPO:-$HOME/.m2/repository}"
SPIGOT_API="$M2/org/spigotmc/spigot-api/${MC_VERSION}-R0.1-SNAPSHOT/spigot-api-${MC_VERSION}-R0.1-SNAPSHOT.jar"

[ -f "$SPIGOT_API" ] || { echo "spigot-api not found: $SPIGOT_API
Run the main Arclight build first (it installs spigot-api to your local Maven repo)."; exit 1; }

OUT="$HERE/out"
rm -rf "$OUT"; mkdir -p "$OUT"
# Single classpath entry: pass it directly. As a standalone arg, Git Bash/MSYS
# rewrites the unix path (/c/...) to a Windows path that javac.exe understands;
# an @argfile would NOT get that rewrite.
"$JAVA_HOME/bin/javac" -cp "$SPIGOT_API" -d "$OUT" "$HERE/src/io/izzel/bench/BenchPlugin.java"
cp "$HERE/plugin.yml" "$OUT/"
( cd "$OUT" && "$JAVA_HOME/bin/jar" cf "$HERE/ArclightBench.jar" . )
echo "Built $HERE/ArclightBench.jar"
