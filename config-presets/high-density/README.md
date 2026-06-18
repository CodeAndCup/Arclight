# High-density config preset (MC 1.20.1 / Arclight)

A tuned baseline for **many players per shard**. Arclight generates **vanilla Spigot/Bukkit
defaults** (e.g. entity-activation-range 32/32, 70 monsters, monster-spawns every tick,
tick-inactive-villagers true) — these values trade FPS for a smoother single instance.

Pair this with the **architecture in [docs/high-player-tuning.md](../../docs/high-player-tuning.md)**
(Velocity proxy + several shards) and the JVM flags here. Validate changes with the
[benchmark](../../benchmark/README.md).

## ⚠️ How to apply — MERGE, don't overwrite
Each file below has a `config-version`. **Do not replace the whole file** (a version mismatch makes
the server reset it). **Back up**, then **copy these values into your existing** `server.properties` /
`spigot.yml` / `bukkit.yml` / `arclight.conf`. Start conservative, measure, then push further.

The values target **~100 players/shard**. Notes show how to scale for lighter/heavier shards.

---

## server.properties
```properties
view-distance=6            # biggest CPU+bandwidth lever. 8≈vanilla feel, 5 for very high pop, 4 lobbies
simulation-distance=5      # how far things TICK; decouple from view. 4 = big CPU saving
network-compression-threshold=256   # behind a proxy you can set -1 (let the proxy compress)
player-idle-timeout=15     # kick AFK to free slots
sync-chunk-writes=false    # async chunk I/O: big win, tiny crash-safety tradeoff
entity-broadcast-range-percentage=80   # send entity updates slightly less far
```

## spigot.yml  (section `world-settings.default`)
```yaml
world-settings:
  default:
    entity-activation-range:
      animals: 16
      monsters: 24
      raiders: 48
      misc: 8
      water: 8
      villagers: 16
      flying-monsters: 48
      wake-up-inactive:
        animals-max-per-tick: 2
        monsters-max-per-tick: 4
        villagers-max-per-tick: 2
        flying-monsters-max-per-tick: 2
      tick-inactive-villagers: false   # vanilla=true; villagers are expensive at scale
      ignore-spectators: true
    entity-tracking-range:
      players: 48
      animals: 32
      monsters: 48
      misc: 16
      display: 128
      other: 48
    merge-radius:
      exp: 4.0
      item: 3.5
    mob-spawn-range: 6
    max-entity-collisions: 2
    hopper-amount: 1
    ticks-per:
      hopper-transfer: 8
      hopper-check: 8        # vanilla=1: hopper polling every tick is costly
```

## bukkit.yml
```yaml
spawn-limits:               # caps TOTAL mobs per world (vanilla 70/10/...)
  monsters: 50
  animals: 8
  water-animals: 3
  water-ambient: 6
  water-underground-creature: 3
  axolotls: 3
  ambient: 1
ticks-per:
  animal-spawns: 400
  monster-spawns: 2         # vanilla=1 (a spawn attempt EVERY tick); 2-4 = noticeable CPU saving
  water-spawns: 1
  water-ambient-spawns: 1
  water-underground-creature-spawns: 1
  axolotl-spawns: 1
  ambient-spawns: 1
  autosave: 6000
chunk-gc:
  period-in-ticks: 400      # unload unused chunks a bit more eagerly
```

## arclight.conf  (section `optimization`)
```hocon
optimization {
  use-activation-and-tracking-range = true   # keep ON — applies the spigot.yml ranges above
  goal-selector-update-interval = 3           # mob AI recomputes every 3 ticks (2-4); big saving with many mobs
  cache-plugin-class = true
  disable-data-fixer = false                  # set true ONLY if you never load pre-1.20.1 chunks/data (saves RAM + startup)
}
```

## JVM flags
Use **[jvm-flags-zgc.txt](jvm-flags-zgc.txt)** (Java 21+, recommended). Benchmark-backed: ZGC stayed
smooth where G1 froze on a Full GC. Replace `<HEAP>` and keep peak usage well below it.
G1 fallback: [jvm-flags-g1-aikar.txt](jvm-flags-g1-aikar.txt).

---

## Scaling cheat-sheet
| Shard role | view / sim | spawn monsters | heap |
|---|---|---|---|
| Lobby / hub (light) | 4 / 4 | 10 | 4–6 G |
| Survival ~100 players | 6 / 5 | 50 | 10–12 G |
| Very high pop / minigames | 5 / 4 | 30 | 8–10 G |

## What this does NOT do
- It won't beat the single-thread CPU ceiling — for 250–500 players, **shard behind a proxy** (see the
  tuning guide). One instance ≈ 80–150 players.
- It can't be measured fully without real players (entity AI deactivates with no players nearby), so
  treat entity-range values as best-practice defaults and confirm on a live shard with **Spark**.
