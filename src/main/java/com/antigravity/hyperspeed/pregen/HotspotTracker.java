package com.antigravity.hyperspeed.pregen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class HotspotTracker {
    public static class Hotspot {
        public final String name;
        public final ResourceKey<Level> dimension;
        public final int chunkX;
        public final int chunkZ;
        public final int radius;

        public Hotspot(String name, ResourceKey<Level> dimension, int blockX, int blockZ, int radius) {
            this.name = name;
            this.dimension = dimension;
            this.chunkX = blockX >> 4;
            this.chunkZ = blockZ >> 4;
            this.radius = radius;
        }
    }

    private final List<Hotspot> hotspots = new ArrayList<>();

    public HotspotTracker() {
        // Overworld Main Base - Massive 10k x 10k block frontier (radius = 312 chunks = 5000 blocks in each direction!)
        hotspots.add(new Hotspot("Overworld Base Frontier (10kx10k)", Level.OVERWORLD, 133, -198, 312));
        // Overworld World Spawn
        hotspots.add(new Hotspot("Overworld Spawn Core", Level.OVERWORLD, 0, 0, 128));
        // Nether Hub / Fortress Network
        hotspots.add(new Hotspot("Nether Expressway Hub", Level.NETHER, 16, -24, 128));
        // The End Realm & Outer Islands
        hotspots.add(new Hotspot("The End Dragon & Outer Realm", Level.END, 0, 0, 96));
    }

    public synchronized void addPlayerLogoutPosition(String playerName, ResourceKey<Level> dim, BlockPos pos) {
        hotspots.removeIf(h -> h.name.equals("Player: " + playerName));
        // Dynamic Player Exploration zone: 64 chunks radius (~2000 blocks width)
        hotspots.add(new Hotspot("Player: " + playerName, dim, pos.getX(), pos.getZ(), 64));
    }

    public synchronized void addCustomHotspot(String name, ResourceKey<Level> dim, BlockPos pos, int radius) {
        hotspots.removeIf(h -> h.name.equalsIgnoreCase(name));
        hotspots.add(new Hotspot(name, dim, pos.getX(), pos.getZ(), Math.max(8, Math.min(radius, 500))));
    }

    public synchronized List<Hotspot> getHotspots() {
        return new ArrayList<>(hotspots);
    }
}
