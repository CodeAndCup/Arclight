# Arclight benchmark system

A small, reproducible load generator + runner to measure an Arclight server's
**TPS, heap usage and GC behaviour** under synthetic load — no real players needed.
Useful for comparing JDKs (e.g. Java 17 vs Java 25), GC choices (G1 vs Generational
ZGC), config tuning, or before/after a code change.

## Contents
| File | Purpose |
|------|---------|
| `src/io/izzel/bench/BenchPlugin.java` | Bukkit plugin: pre-gens chunks, force-loads spread "simulated player" clusters, spawns entities, samples TPS/heap, prints `BENCH-SUMMARY`, optionally shuts down. |
| `plugin.yml` | Plugin descriptor. |
| `build.sh` | Compiles the plugin into `ArclightBench.jar`. |
| `run-benchmark.sh` | Runs one deterministic benchmark and prints a TPS + GC summary. |

## Prerequisites
1. A built Arclight server jar (`arclight-forge-<mc>-<ver>.jar`).
2. `org.spigotmc:spigot-api:<mc>-R0.1-SNAPSHOT` in your local Maven repo — the main
   Arclight build (BuildTools) installs it automatically.
3. A server run directory. The **first** run downloads Forge libraries (needs network);
   on a Zscaler/corporate machine export `JAVA_TOOL_OPTIONS` with the mitmproxy first
   (see `~/.arclight-proxy/README.md`).

## 1. Build the plugin
```bash
BENCH_JDK=~/.jdks/corretto-17.0.19 ./benchmark/build.sh
```

## 2. Run a benchmark
```bash
export BENCH_JDK=~/.jdks/corretto-17.0.19          # JDK to TEST (runs the server)
export ARCLIGHT_JAR=$PWD/build/libs/arclight-forge-1.20.1-1.0.6-SNAPSHOT.jar
export RUN_DIR=$PWD/run_bench
./benchmark/run-benchmark.sh
```
Output (also saved in `$RUN_DIR/bench-<label>.log` and `gc-<label>.log`):
```
BENCH-PREGEN chunks=289 ms=17658
BENCH-SUMMARY jvm=17.0.19 avgTPS=19.56 minTPS=0.07 p5TPS=19.98 maxHeapMB=1406 players=0 finalEntities=1029
GC: 38 pause-events, total=1499ms max=313.3ms avg=34.9ms
```

## 3. Tuning the load (`-Dbench.*`, pass via `BENCH_ARGS`)
| Property | Default | Meaning |
|----------|---------|---------|
| `bench.warmup` | 5 | seconds before measuring |
| `bench.duration` | 45 | seconds measured |
| `bench.pregen` | 8 | chunk radius pre-generated around spawn |
| `bench.players` | 0 | **simulated players** — each force-loads a spread cluster of chunks (distributed tick load) |
| `bench.viewDist` | 4 | chunk radius force-loaded per simulated player |
| `bench.spread` | 48 | chunk spacing between simulated players |
| `bench.entitiesPerSec` | 40 | entities spawned per second |
| `bench.entityCap` | 1000 | max live entities |
| `bench.entity` | ZOMBIE | entity type (any `EntityType`) |
| `bench.autostop` | true | `shutdown()` after the run |
| `bench.out` | — | append the summary line to this file |

Heavy, distributed load (closer to a populated server) — 150 simulated players:
```bash
BENCH_ARGS="-Dbench.players=150 -Dbench.viewDist=4 -Dbench.duration=90 -Dbench.entityCap=4000" \
  ./benchmark/run-benchmark.sh
```

## 4. Compare two JDKs / GC settings
Run twice with different `BENCH_JDK` / `JVM_EXTRA` and compare the summaries. Example
Generational ZGC on Java 25 (sub-millisecond GC pauses):
```bash
BENCH_JDK=~/.jdks/corretto-25.0.2 LABEL=zgc \
  JVM_EXTRA="-XX:+UseZGC -XX:+ZGenerational" HEAP=8G \
  ./benchmark/run-benchmark.sh
```

## Interpreting results
- **p5TPS** is the honest "sustained" tick rate (5th percentile; ignores the warmup dip).
  `minTPS` is usually just the first measurement window.
- **GC max** pause is what players feel as a lag spike. Lower is better; ZGen ZGC keeps it <1ms.
- **maxHeapMB** vs your `-Xmx` shows headroom.

## ⚠️ Caveats
- **Thermal throttling** dominates run-to-run variance on laptops: two heavy runs back to
  back will make the *second* look slower. Add a cooldown between runs and/or average several runs.
- A single instance on a fast CPU stays at 20 TPS until genuinely saturated; if TPS never
  drops, increase `bench.players` / `bench.entityCap` until it does, then compare.
- Numbers are **relative** indicators on the test box, not absolute server capacity.
