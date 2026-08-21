package com.antigravity.hyperspeed.pregen;

import com.antigravity.hyperspeed.HyperSpeedMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.File;
import java.util.List;

public class IdleAutoPregen {
    private final HotspotTracker hotspotTracker = new HotspotTracker();
    
    private boolean isRunning = false;
    private int idleCooldownTicks = 100; // 5 seconds after 0 players
    
    // Multi-Dimension Spiral State
    private int currentHotspotIndex = 0;
    private int spiralX = 0;
    private int spiralZ = 0;
    private int spiralDx = 0;
    private int spiralDz = -1;
    
    private int chunksGeneratedTotal = 0;
    private int chunksGeneratedThisSession = 0;

    // Turbo Configuration
    private int batchSize = 10; // Up to 200 chunks/sec
    private int progressReportThreshold = 500;
    private final long minFreeDiskSpaceBytes = 2_000_000_000L; // 2GB minimum disk space safety margin

    public HotspotTracker getHotspotTracker() {
        return hotspotTracker;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getChunksGeneratedTotal() {
        return chunksGeneratedTotal;
    }

    public int getChunksGeneratedThisSession() {
        return chunksGeneratedThisSession;
    }

    public String getCurrentTargetInfo() {
        List<HotspotTracker.Hotspot> spots = hotspotTracker.getHotspots();
        if (currentHotspotIndex < spots.size()) {
            HotspotTracker.Hotspot h = spots.get(currentHotspotIndex);
            return h.name + " (" + h.dimension.location().getPath() + ") [Radius: " + (h.getRadius() * 16) + "m]";
        }
        return "All Expanding Infinitely";
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (isRunning) {
            pause("Player " + event.getEntity().getName().getString() + " connected");
        }
        idleCooldownTicks = 100;
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            hotspotTracker.addPlayerLogoutPosition(
                serverPlayer.getName().getString(),
                serverPlayer.level().dimension(),
                serverPlayer.blockPosition()
            );
        }
        idleCooldownTicks = 100;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || !server.isRunning()) {
            return;
        }

        int playerCount = server.getPlayerList().getPlayerCount();

        // 1. If players are online, pause immediately (0ms delay)
        if (playerCount > 0) {
            if (isRunning) {
                pause("Players online (" + playerCount + ")");
            }
            return;
        }

        // 2. 0 players online: countdown stabilization cooldown
        if (idleCooldownTicks > 0) {
            idleCooldownTicks--;
            return;
        }

        // 3. Start pregen if not running
        if (!isRunning) {
            startIdlePregen();
        }

        // 4. Adaptive throughput based on server MSPT
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        if (mspt < 25.0) {
            batchSize = 10; // 200 chunks/sec
        } else if (mspt < 40.0) {
            batchSize = 5;  // 100 chunks/sec
        } else {
            batchSize = 2;  // 40 chunks/sec
        }

        tickTurboPregen(server, batchSize);
    }

    private void startIdlePregen() {
        isRunning = true;
        chunksGeneratedThisSession = 0;
        currentHotspotIndex = 0;
        resetSpiral();
        HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] ⚡ Infinite-Frontier Auto-Pregeneration ACTIVATED (10k+ Blocks baseline, Infinite Auto-Expansion).");
    }

    public void pause(String reason) {
        if (isRunning) {
            isRunning = false;
            HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] ☀️ Instant Pause: {} (Session Chunks: {}). 100% resources released.", reason, chunksGeneratedThisSession);
        }
    }

    private void resetSpiral() {
        spiralX = 0;
        spiralZ = 0;
        spiralDx = 0;
        spiralDz = -1;
    }

    private void tickTurboPregen(MinecraftServer server, int count) {
        if (!isRunning) return;

        List<HotspotTracker.Hotspot> spots = hotspotTracker.getHotspots();
        if (spots.isEmpty()) return;

        if (currentHotspotIndex >= spots.size()) {
            currentHotspotIndex = 0;
        }

        HotspotTracker.Hotspot spot = spots.get(currentHotspotIndex);
        ServerLevel level = server.getLevel(spot.dimension);
        if (level == null) {
            currentHotspotIndex = (currentHotspotIndex + 1) % spots.size();
            resetSpiral();
            return;
        }

        for (int i = 0; i < count; i++) {
            if (!isRunning) break;

            int targetChunkX = spot.chunkX + spiralX;
            int targetChunkZ = spot.chunkZ + spiralZ;

            // Non-blocking async full chunk request
            level.getChunkSource().getChunkFuture(targetChunkX, targetChunkZ, ChunkStatus.FULL, true)
                .whenComplete((result, throwable) -> {
                    if (throwable == null && result != null) {
                        chunksGeneratedTotal++;
                        chunksGeneratedThisSession++;
                    }
                });

            // Advance coordinates in spiral with infinite boundary expansion
            advanceSpiral(spot, spots);
        }

        // Periodic maintenance every 500 chunks
        if (chunksGeneratedThisSession > 0 && chunksGeneratedThisSession % progressReportThreshold < count) {
            level.getChunkSource().save(false);
            System.gc(); // Clean RAM safely during idle
            HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] 🚀 INFINITE FRONTIER: {} chunks pre-rendered for [{}] in {} (Radius: {}m)",
                chunksGeneratedThisSession, spot.name, spot.dimension.location().getPath(), spot.getRadius() * 16);
        }
    }

    private void advanceSpiral(HotspotTracker.Hotspot spot, List<HotspotTracker.Hotspot> spots) {
        int maxRadius = spot.getRadius();

        // When current spiral reaches the boundary of this hotspot:
        if (Math.abs(spiralX) > maxRadius || Math.abs(spiralZ) > maxRadius) {
            // Check disk space before auto-expanding
            long freeDisk = new File(".").getUsableSpace();
            if (freeDisk > minFreeDiskSpaceBytes) {
                // Auto-expand this hotspot by +100 chunks (+1,600 blocks) infinitely!
                spot.expandRadius(100);
                HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] 🌟 BOUNDARY EXPANDED: [{}] expanded to radius {} chunks ({} blocks in all directions)!",
                    spot.name, spot.getRadius(), spot.getRadius() * 16);
            }
            
            // Cycle to next registered dimension hotspot
            currentHotspotIndex = (currentHotspotIndex + 1) % spots.size();
            resetSpiral();
            return;
        }

        if (spiralX == spiralZ || (spiralX < 0 && spiralX == -spiralZ) || (spiralX > 0 && spiralX == 1 - spiralZ)) {
            int temp = spiralDx;
            spiralDx = -spiralDz;
            spiralDz = temp;
        }
        spiralX += spiralDx;
        spiralZ += spiralDz;
    }
}
