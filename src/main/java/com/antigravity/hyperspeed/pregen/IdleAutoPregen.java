package com.antigravity.hyperspeed.pregen;

import com.antigravity.hyperspeed.HyperSpeedMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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

    // Turbo Throughput Configuration
    private int batchSize = 10; // 10 chunks per tick = ~200 chunks/second (Turbo Mode)
    private int progressReportThreshold = 500;

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
            return h.name + " (" + h.dimension.location().getPath() + ")";
        }
        return "All Complete";
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

        // 4. Turbo Tick: Dispatch adaptive batch every tick
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        if (mspt < 25.0) {
            batchSize = 10; // Max Turbo: 200 chunks/sec
        } else if (mspt < 40.0) {
            batchSize = 5;  // Medium: 100 chunks/sec
        } else {
            batchSize = 2;  // Safe throttle: 40 chunks/sec
        }

        tickTurboPregen(server, batchSize);
    }

    private void startIdlePregen() {
        isRunning = true;
        chunksGeneratedThisSession = 0;
        currentHotspotIndex = 0;
        resetSpiral();
        HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] ⚡ Turbo-Boost Auto-Pregeneration ACTIVATED (Up to 200 chunks/sec).");
    }

    public void pause(String reason) {
        if (isRunning) {
            isRunning = false;
            HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] ☀️ Instant Pause: {} (Session Chunks Pregenerated: {}). 100% resources released.", reason, chunksGeneratedThisSession);
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
        if (currentHotspotIndex >= spots.size()) {
            HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] 🏆 All multi-dimension frontier regions fully pre-rendered! Total chunks: {}", chunksGeneratedTotal);
            pause("All priority regions completed");
            return;
        }

        HotspotTracker.Hotspot spot = spots.get(currentHotspotIndex);
        ServerLevel level = server.getLevel(spot.dimension);
        if (level == null) {
            currentHotspotIndex++;
            resetSpiral();
            return;
        }

        for (int i = 0; i < count; i++) {
            if (!isRunning || currentHotspotIndex >= spots.size()) break;

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

            // Advance coordinates in spiral
            advanceSpiral(spot.radius);
        }

        // Periodic maintenance every 500 chunks
        if (chunksGeneratedThisSession > 0 && chunksGeneratedThisSession % progressReportThreshold < count) {
            level.getChunkSource().save(false);
            System.gc(); // Clean RAM safely during idle
            HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] 🚀 TURBO PROGRESS: {} chunks pre-rendered for [{}] in {}", chunksGeneratedThisSession, spot.name, spot.dimension.location().getPath());
        }
    }

    private void advanceSpiral(int maxRadius) {
        if (Math.abs(spiralX) > maxRadius || Math.abs(spiralZ) > maxRadius) {
            currentHotspotIndex++;
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
