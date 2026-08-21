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
        private int radius;

        public Hotspot(String name, ResourceKey<Level> dimension, int blockX, int blockZ, int radius) {
            this.name = name;
            this.dimension = dimension;
            this.chunkX = blockX >> 4;
            this.chunkZ = blockZ >> 4;
            this.radius = radius;
        }

        public int getRadius() {
            return radius;
        }

        public void expandRadius(int additionalChunks) {
            this.radius += additionalChunks;
        }
    }

    private final List<Hotspot> hotspots = new ArrayList<>();

    public HotspotTracker() {
        // Overworld Main Base - Massive 10,000 Block Radius (10k North, 10k South, 10k East, 10k West = 20,000x20,000 Block Box)
        // 625 chunks = 10,000 blocks in every direction!
        hotspots.add(new Hotspot("Overworld Base Infinite Frontier (10k+ Blocks)", Level.OVERWORLD, 133, -198, 625));
        
        // Overworld World Spawn Core - 4,000 blocks radius (250 chunks)
        hotspots.add(new Hotspot("Overworld Spawn Core (4k Blocks)", Level.OVERWORLD, 0, 0, 250));
        
        // Nether Hub / Expressway - 4,000 blocks radius in Nether (= 32,000 blocks Overworld equivalent!)
        hotspots.add(new Hotspot("Nether Expressway Infinite Hub (4k Blocks)", Level.NETHER, 16, -24, 250));
        
        // The End Dragon & Outer Realm - 3,200 blocks radius (200 chunks)
        hotspots.add(new Hotspot("The End Dragon & Outer Islands (3.2k Blocks)", Level.END, 0, 0, 200));
    }

    public synchronized void addPlayerLogoutPosition(String playerName, ResourceKey<Level> dim, BlockPos pos) {
        hotspots.removeIf(h -> h.name.equals("Player: " + playerName));
        // Dynamic Player Exploration zone: 128 chunks radius (~2,000 blocks in all directions)
        hotspots.add(new Hotspot("Player: " + playerName, dim, pos.getX(), pos.getZ(), 128));
    }

    public synchronized void addCustomHotspot(String name, ResourceKey<Level> dim, BlockPos pos, int radius) {
        hotspots.removeIf(h -> h.name.equalsIgnoreCase(name));
        hotspots.add(new Hotspot(name, dim, pos.getX(), pos.getZ(), Math.max(16, radius)));
    }

    public synchronized List<Hotspot> getHotspots() {
        return new ArrayList<>(hotspots);
    }
}
