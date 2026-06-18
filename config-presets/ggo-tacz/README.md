# GGO / TACZ preset — hybrid network (city hub + instanced zones), 250–500 players

Tuning for a **gun game** (TACZ on Arclight/Forge): ranged PvP where **view distance, entity
tracking range and tick fluidity are gameplay-critical** — the opposite of survival tuning. Reaching
250–500 players means **a proxy + several backends**, not one instance.

> Apply by **merging** values into each backend's config (don't overwrite — `config-version` must
> match). Validate with the [benchmark](../../benchmark/README.md) and live **Spark** profiling.

## Architecture (hybrid, 250–500)
```
                         ┌────────────┐
            players ────▶│  Velocity  │  proxy: routing + auth (1-2 cores)
                         └─────┬──────┘
        ┌──────────────────────┼───────────────────────────┐
   ┌────▼─────┐         ┌───────▼────────┐          ┌───────▼────────┐
   │ HUB(s)   │         │ ZONE INSTANCE  │   ...    │ ZONE INSTANCE  │   pool of combat
   │ the city │ x1-3    │ gunfight arena │  x many  │ gunfight arena │   instances (TACZ load)
   │ social   │         │ ~10-50 players │          │ ~10-50 players │
   └──────────┘         └────────────────┘          └────────────────┘
            shared player data via a DB / sync plugin (Redis or SQL)
```
- **Hub(s)**: the persistent GGO city — holds the most players socially. PvP/shooting **off** here →
  almost no TACZ load; tune for *many players, light ticking*. Mirror the hub (hub-1, hub-2) if one
  fills up (proxy load-balances).
- **Zone instances**: instanced gunfights — **few players each** but **heavy TACZ** (per-shot
  raytraces). Many small instances spread the bullet CPU across processes. This is where high view /
  tracking distance lives.

---

## The #1 GGO lever: kill ambient mob load
GGO is gun PvP, not mob survival. Disabling mob spawning frees the whole entity-AI tick budget for
**view distance + bullet processing**. In `server.properties` on **both** profiles:
```properties
spawn-monsters=false
spawn-animals=false
spawn-npcs=true        # keep if you use villager/NPC shops
```
And in `bukkit.yml` zero the caps (belt and suspenders):
```yaml
spawn-limits: { monsters: 0, animals: 0, water-animals: 0, water-ambient: 0,
                water-underground-creature: 0, axolotls: 0, ambient: 0 }
```

---

## Profile A — HUB (the city)
Many players, social, no combat. Show the city off (high view) but tick lightly.
```properties
# server.properties
view-distance=10            # see the city; it's the showcase
simulation-distance=4       # social area: little needs to tick -> big CPU saving with many players
spawn-monsters=false
network-compression-threshold=256
player-idle-timeout=20
sync-chunk-writes=false
```
```yaml
# spigot.yml world-settings.default
entity-tracking-range: { players: 96, animals: 32, monsters: 48, misc: 16, display: 160, other: 64 }
mob-spawn-range: 2
ticks-per: { hopper-transfer: 8, hopper-check: 8 }
```
```hocon
# arclight.conf
optimization { use-activation-and-tracking-range = true, goal-selector-update-interval = 4 }
```
- **Pre-generate the city + WorldBorder to its bounds** so the whole hub stays loaded → no runtime
  worldgen spikes. Cap hub population (e.g. 120-150) and overflow to a mirror hub.

## Profile B — COMBAT ZONE (instanced arena, TACZ)
Few players, ranged gunfights. **Sightlines and fluidity rule.**
```properties
# server.properties
view-distance=14            # long sightlines for sniping (GGO is a sniper game)
simulation-distance=8       # bullets/targets must tick across engagement range
spawn-monsters=false
spawn-animals=false
network-compression-threshold=256
sync-chunk-writes=false
```
```yaml
# spigot.yml world-settings.default
# track enemy PLAYERS as far as you can shoot them (≈ engagement range in blocks)
entity-tracking-range: { players: 160, animals: 48, monsters: 64, misc: 32, display: 192, other: 64 }
entity-activation-range: { animals: 16, monsters: 16, raiders: 16, misc: 8, water: 8 }
max-entity-collisions: 2
```
```hocon
# arclight.conf
optimization { use-activation-and-tracking-range = true, goal-selector-update-interval = 3 }
```
- **Pre-load the entire arena map** (bounded) at startup and keep it loaded → zero in-match worldgen
  → stable MSPT (no lag spikes mid-fight). Use a fixed WorldBorder = arena size.
- Keep each instance **small** (10-50). More small instances > fewer big ones (TACZ raytrace cost
  scales with simultaneous shooters per instance).

---

## TACZ on Arclight — verify first ⚠️
TACZ is a **Forge** mod; Arclight is a Forge+Bukkit hybrid. Before scaling, confirm on one test
backend (ideally the Java 25 build):
1. TACZ loads and guns work (shoot, reload, attachments).
2. **Bullet damage fires Bukkit events** (`EntityDamageByEntityEvent`) so your PvP/region/stats
   plugins see kills — this is the usual hybrid friction point.
3. Server-side bullet processing under load: `/spark profiler` during a busy firefight → see how much
   tick time TACZ takes; that sets your players-per-instance budget.

## Fluidity (non-negotiable for a shooter)
- **ZGC** ([../high-density/jvm-flags-zgc.txt](../high-density/jvm-flags-zgc.txt)) — sub-ms GC pauses,
  measured to beat G1 (no Full-GC freeze). A GC spike = a missed shot.
- Heap sized with headroom (running near-full cost ~16% p5 TPS in our benchmark).
- Pre-generated maps (above) remove the biggest MSPT spike source.

## Sizing for 250–500
| Component | Count | Players each | Notes |
|---|---|---|---|
| Velocity proxy | 1 (+1 standby) | — | light; routes everyone |
| Hub (city) | 1–3 mirrors | ~120-150 | Profile A; cap + overflow |
| Combat zones | many (pooled) | ~10-50 | Profile B; spin up per match |
| Shared DB | 1 (Redis/SQL) | — | player state across servers |

One Arclight instance ≈ 80-150 players (less in heavy TACZ firefights). The proxy + pool is what gets
you to 250-500 total. Profile each piece with Spark and the benchmark; **strong single-thread CPU**
per backend matters most.
