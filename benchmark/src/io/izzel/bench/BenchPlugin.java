package io.izzel.bench;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Headless load generator for benchmarking an Arclight (or any Bukkit) server.
 *
 * It does not need real players: it can pre-generate chunks, force-load spread-out
 * "simulated player" chunk clusters (distributed tick load), and pile up entities
 * (AI / pathfinding load). It samples real TPS (by counting ticks per wall-second)
 * and used heap, then prints a single BENCH-SUMMARY line and (optionally) shuts the
 * server down so a run is fully deterministic.
 *
 * Everything is configured with -D system properties on the server JVM:
 *   bench.warmup          seconds before measuring starts                (default 5)
 *   bench.duration        seconds to measure                            (default 45)
 *   bench.pregen          chunk radius pre-generated around spawn        (default 8)
 *   bench.players         simulated players (force-loaded clusters)      (default 0)
 *   bench.viewDist        chunk radius force-loaded per simulated player (default 4)
 *   bench.spread          chunk spacing between simulated players        (default 48)
 *   bench.entitiesPerSec  entities spawned per second                    (default 40)
 *   bench.entityCap       max live entities                             (default 1000)
 *   bench.entity          entity type to spawn                          (default ZOMBIE)
 *   bench.autostop        shutdown() after the run                       (default true)
 *   bench.out             append the summary line to this file           (optional)
 */
public class BenchPlugin extends JavaPlugin {

    private World world;
    private int second = 0, tickCount = 0;
    private long windowStart, maxHeap = 0;
    private final List<Double> tps = new ArrayList<>();
    private final Deque<long[]> forceLoadQueue = new ArrayDeque<>();
    private long setupStart;
    private boolean measuring = false;

    private int cfgWarmup, cfgDuration, cfgPregen, cfgPlayers, cfgViewDist, cfgSpread, cfgEntitiesPerSec, cfgEntityCap;
    private EntityType cfgEntity;
    private boolean cfgAutostop;
    private String cfgOut;

    private int cfgi(String k, int def) { return Integer.getInteger("bench." + k, def); }

    @Override
    public void onEnable() {
        cfgWarmup = cfgi("warmup", 5);
        cfgDuration = cfgi("duration", 45);
        cfgPregen = cfgi("pregen", 8);
        cfgPlayers = cfgi("players", 0);
        cfgViewDist = cfgi("viewDist", 4);
        cfgSpread = cfgi("spread", 48);
        cfgEntitiesPerSec = cfgi("entitiesPerSec", 40);
        cfgEntityCap = cfgi("entityCap", 1000);
        cfgAutostop = !"false".equalsIgnoreCase(System.getProperty("bench.autostop", "true"));
        cfgOut = System.getProperty("bench.out");
        try {
            cfgEntity = EntityType.valueOf(System.getProperty("bench.entity", "ZOMBIE").toUpperCase());
        } catch (IllegalArgumentException e) {
            cfgEntity = EntityType.ZOMBIE;
        }

        world = Bukkit.getWorlds().get(0);
        getLogger().info("BENCH-START jvm=" + System.getProperty("java.version") + " vm=" + System.getProperty("java.vm.name"));
        getLogger().info(String.format("BENCH-CONFIG warmup=%d duration=%d pregen=%d players=%d viewDist=%d entity=%s perSec=%d cap=%d",
            cfgWarmup, cfgDuration, cfgPregen, cfgPlayers, cfgViewDist, cfgEntity, cfgEntitiesPerSec, cfgEntityCap));

        long t0 = System.nanoTime();
        for (int x = -cfgPregen; x <= cfgPregen; x++)
            for (int z = -cfgPregen; z <= cfgPregen; z++)
                world.getChunkAt(x, z);
        long pregenMs = (System.nanoTime() - t0) / 1_000_000;
        getLogger().info(String.format("BENCH-PREGEN chunks=%d ms=%d", (2 * cfgPregen + 1) * (2 * cfgPregen + 1), pregenMs));

        // Queue force-loaded clusters for the simulated players (loaded progressively so we
        // don't block the main thread for minutes on a big run).
        int side = (int) Math.ceil(Math.sqrt(Math.max(1, cfgPlayers)));
        for (int p = 0; p < cfgPlayers; p++) {
            int gx = (p % side) - side / 2, gz = (p / side) - side / 2;
            int cx = gx * cfgSpread, cz = gz * cfgSpread;
            for (int x = cx - cfgViewDist; x <= cx + cfgViewDist; x++)
                for (int z = cz - cfgViewDist; z <= cz + cfgViewDist; z++)
                    forceLoadQueue.add(new long[]{x, z});
        }
        setupStart = System.nanoTime();
        windowStart = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskTimer(this, this::onTick, 20L * cfgWarmup, 1L);
    }

    private void onTick() {
        // Progressive force-loading of simulated-player chunks (up to 32 per tick).
        if (!forceLoadQueue.isEmpty()) {
            for (int i = 0; i < 32 && !forceLoadQueue.isEmpty(); i++) {
                long[] c = forceLoadQueue.poll();
                world.addPluginChunkTicket((int) c[0], (int) c[1], this);
            }
            if (forceLoadQueue.isEmpty()) {
                long ms = (System.nanoTime() - setupStart) / 1_000_000;
                getLogger().info(String.format("BENCH-SETUP forceLoadedChunks=%d ms=%d", world.getPluginChunkTickets().size(), ms));
                windowStart = System.currentTimeMillis(); // reset measurement window after setup
            }
            return;
        }

        tickCount++;
        long now = System.currentTimeMillis();
        long elapsed = now - windowStart;
        if (elapsed < 1000) return;
        if (!measuring) { measuring = true; getLogger().info("BENCH-MEASURE-BEGIN"); }
        second++;
        double t = tickCount * 1000.0 / elapsed;
        tickCount = 0;
        windowStart = now;

        Location l = world.getSpawnLocation();
        if (world.getEntities().size() < cfgEntityCap) {
            for (int i = 0; i < cfgEntitiesPerSec; i++) {
                Entity e = world.spawnEntity(l.clone().add(Math.random() * 16 - 8, 0, Math.random() * 16 - 8), cfgEntity);
                if (e instanceof LivingEntity le) { le.setRemoveWhenFarAway(false); e.setPersistent(true); }
            }
        }
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576;
        maxHeap = Math.max(maxHeap, used);
        tps.add(t);
        getLogger().info(String.format("BENCH s=%d tps=%.2f heapMB=%d entities=%d", second, t, used, world.getEntities().size()));

        if (second >= cfgDuration) {
            double avg = tps.stream().mapToDouble(d -> d).average().orElse(0);
            double min = tps.stream().mapToDouble(d -> d).min().orElse(0);
            List<Double> s = new ArrayList<>(tps); s.sort(Double::compare);
            double p5 = s.get(Math.max(0, (int) (s.size() * 0.05)));
            String summary = String.format("BENCH-SUMMARY jvm=%s avgTPS=%.2f minTPS=%.2f p5TPS=%.2f maxHeapMB=%d players=%d finalEntities=%d",
                System.getProperty("java.version"), avg, min, p5, maxHeap, cfgPlayers, world.getEntities().size());
            getLogger().info(summary);
            if (cfgOut != null) {
                try { Files.writeString(Path.of(cfgOut), summary + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
                catch (IOException ignored) {}
            }
            if (cfgAutostop) Bukkit.shutdown();
        }
    }
}
