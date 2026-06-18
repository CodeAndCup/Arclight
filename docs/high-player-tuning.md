# Tuning Arclight for high player counts (250–500)

Honest, practical guide to running Arclight at scale. Use the [benchmark
system](../benchmark/README.md) to A/B every change.

## 1. Reality check — architecture first
Minecraft's tick loop is **single-threaded**. One Arclight instance realistically holds
**~80–150 players** for normal survival gameplay (more only for very light/lobby use). A
single instance cannot reasonably host 250–500 players of real gameplay — the main thread
saturates regardless of CPU core count.

**The proven way to reach 250–500 concurrent players is horizontal scaling:**

```
            ┌─────────────┐
 players ──▶│  Velocity   │  (proxy: auth, routing — very light, 1 core)
            │   proxy     │
            └──────┬──────┘
        ┌──────────┼───────────┬───────────┐
   ┌────▼───┐ ┌────▼───┐  ┌────▼───┐   ┌────▼───┐
   │Arclight│ │Arclight│  │Arclight│   │Arclight│   each tuned to ~100 players
   │ lobby  │ │survival│  │survival│   │minigame│   (sharded by world/region/purpose)
   └────────┘ └────────┘  └────────┘   └────────┘
        shared state via Redis / a database
```

- **Folia** (regionised multithreading) would let one instance use many cores — but Folia is
  **Paper-only and not available on Forge/Arclight**. So Arclight scales by sharding, not Folia.
- A Velocity proxy + N tuned Arclight backends is how large networks serve thousands.

The rest of this doc maximises **per-instance** capacity, so each shard holds as many players as possible.

## 2. JVM & GC — the Java 25 payoff
- Size the heap to the workload (~10–16 GB for a busy ~150-player shard). **Stay under ~31 GB**
  so compressed oops stay on.
- **Generational ZGC (Java 25):** `-XX:+UseZGC` → **sub-millisecond GC pauses** even with large
  heaps, i.e. no GC lag spikes for players. Generational mode is the default on Java 24+ (on Java
  21 add `-XX:+ZGenerational`). This is the main runtime reason to run Arclight on Java 25 (see the
  Java 25 work on `feat/java25-runtime`).
- Pre-touch + fixed heap: `-Xms = -Xmx`, `-XX:+AlwaysPreTouch`.
- If you prefer G1 (smaller heaps), use Aikar's flags (well-known tuned G1 set).
- Verify with the benchmark: `JVM_EXTRA="-XX:+UseZGC -XX:+ZGenerational" HEAP=8G ./benchmark/run-benchmark.sh`.

## 3. Arclight config (`arclight.conf`)
```hocon
optimization {
  use-activation-and-tracking-range = true   # keep ON: huge entity CPU saver
  goal-selector-update-interval = 3           # mob AI recomputes less often (2–4); big saving with many mobs
  cache-plugin-class = true
  disable-data-fixer = false                  # true only if you never load old chunks/data
}
async-catcher { warn = true }                 # surfaces plugins/mods illegally touching the main thread
```

## 4. spigot.yml — the biggest entity levers
```yaml
entities:
  entity-activation-range: { animals: 16, monsters: 24, raiders: 48, misc: 8, water: 8, villagers: 16, flying-monsters: 48 }
  entity-tracking-range:   { players: 48, animals: 32, monsters: 48, misc: 16, other: 48 }
  nerf-spawner-mobs: true        # spawner mobs don't run AI
  merge-radius: { item: 3.5, exp: 4.0 }
  max-entity-collisions: 2
  mob-spawn-range: 6
  ticks-per: { hopper-transfer: 8, hopper-check: 8 }   # hoppers are expensive
```

## 5. server.properties / bukkit.yml
```properties
view-distance=6           # SINGLE biggest CPU+bandwidth lever — lower = many more players
simulation-distance=5     # decouple ticked area from view; lower = big CPU saving
network-compression-threshold=-1   # behind a proxy, let the proxy compress
player-idle-timeout=15    # free slots from AFK players
```
```yaml
# bukkit.yml — cap total mobs and spawn less often
spawn-limits: { monsters: 50, animals: 8, water-animals: 4, water-ambient: 4, ambient: 1 }
ticks-per: { monster-spawns: 4, animal-spawns: 400 }
chunk-gc: { period-in-ticks: 400 }
```

## 6. Mods (Forge)
- Every server-side mod adds main-thread tick cost — audit them; drop heavy worldgen / tile-entity
  / pathfinding-heavy mods for high-pop shards.
- Memory-reduction mods help a lot at scale (more players per GB): **FerriteCore**, **ModernFix**.
  Forge ports of perf mods exist (e.g. Radium/Canary/Saturn) — **test each with Arclight's Bukkit
  layer**, since Arclight mixes Forge + CraftBukkit and not every mod is compatible.

## 7. Plugins
- No heavy work on the main thread: do DB / HTTP / file I/O **async**; pool DB connections.
- Avoid plugins that force synchronous chunk loads or run per-tick scans over all entities/players.
- Prefer a fast permissions backend (e.g. LuckPerms with a pooled SQL/Redis backend).

## 8. Profile — don't guess
- Install **Spark**: `/spark profiler --timeout 120` → a flame graph of exactly what eats tick time
  (usually one mod, plugin or entity type). `/spark tps` and `/spark health` give live MSPT + GC.
- Workflow: profile → change ONE thing → re-measure (Spark live, or this repo's benchmark) → repeat.

## 9. OS / hardware
- **Single-thread CPU performance is king** (the tick loop is one thread): pick high boost-clock
  CPUs over many slow cores. Per shard, a few fast cores beat many slow ones.
- NVMe SSD for world I/O; enough RAM for heap **plus** OS page cache; Linux, raised ulimits.

## TL;DR for 250–500 players
1. Don't do it on one instance. **Velocity proxy + ~3–5 Arclight shards**, each tuned to ~100.
2. Run on **Java 25 with Generational ZGC** for smooth GC.
3. `view-distance=6`, `simulation-distance=5`, tight entity activation/tracking ranges, mob caps.
4. `use-activation-and-tracking-range=true`, `goal-selector-update-interval=3`.
5. **Profile with Spark**, audit mods/plugins, add FerriteCore/ModernFix.
6. Validate every change with the [benchmark system](../benchmark/README.md).
