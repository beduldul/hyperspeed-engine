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
    private int idleCooldownTicks = 200; // 10 seconds after 0 players
    private int tickCounter = 0;
    
    // Multi-Dimension Spiral State
    private int currentHotspotIndex = 0;
    private int spiralX = 0;
    private int spiralZ = 0;
    private int spiralDx = 0;
    private int spiralDz = -1;
    
    private int chunksGeneratedTotal = 0;
    private int chunksGeneratedThisSession = 0;

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
        idleCooldownTicks = 200;
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
        idleCooldownTicks = 200;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || !server.isRunning()) {
            return;
        }

        int playerCount = server.getPlayerList().getPlayerCount();

        // 1. If players are online, pause immediately
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

        // 4. Tick step: Throttle to 1 chunk per 4 ticks (~5 chunks/sec)
        tickCounter++;
        if (tickCounter % 4 == 0) {
            tickPregen(server);
        }
    }

    private void startIdlePregen() {
        isRunning = true;
        chunksGeneratedThisSession = 0;
        currentHotspotIndex = 0;
        resetSpiral();
        HyperSpeedMod.LOGGER.info("[HyperSpeed Engine] 🌙 All players left. Multi-Dimension Auto-Pregeneration started in background.");
    }

    public void pause(String reason) {
        if (isRunning) {
            isRunning = false;
            HyperSpeedMod.LOGGER.info("[HyperSpeed Engine] ☀️ Instant Pause: {} (Session Chunks Pregenerated: {}). 100% server resources released.", reason, chunksGeneratedThisSession);
        }
    }

    private void resetSpiral() {
        spiralX = 0;
        spiralZ = 0;
        spiralDx = 0;
        spiralDz = -1;
    }

    private void tickPregen(MinecraftServer server) {
        if (!isRunning) return;

        List<HotspotTracker.Hotspot> spots = hotspotTracker.getHotspots();
        if (currentHotspotIndex >= spots.size()) {
            HyperSpeedMod.LOGGER.info("[HyperSpeed Engine] ✅ All multi-dimension survival zones fully pre-rendered! Total chunks: {}", chunksGeneratedTotal);
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

        // Calculate chunk coordinate using spiral
        int targetChunkX = spot.chunkX + spiralX;
        int targetChunkZ = spot.chunkZ + spiralZ;

        // Asynchronously request full chunk generation
        level.getChunkSource().getChunkFuture(targetChunkX, targetChunkZ, ChunkStatus.FULL, true)
            .whenComplete((result, throwable) -> {
                if (throwable == null && result != null) {
                    chunksGeneratedTotal++;
                    chunksGeneratedThisSession++;
                }
            });

        // Periodic maintenance every 150 chunks
        if (chunksGeneratedThisSession > 0 && chunksGeneratedThisSession % 150 == 0) {
            level.getChunkSource().save(false);
            System.gc(); // Clean garbage memory safely during idle
            HyperSpeedMod.LOGGER.info("[HyperSpeed Engine] 📦 Background Progress: {} chunks pre-rendered for [{}] in {}", chunksGeneratedThisSession, spot.name, spot.dimension.location().getPath());
        }

        // Advance spiral coordinates
        advanceSpiral(spot.radius);
    }

    private void advanceSpiral(int maxRadius) {
        if (Math.abs(spiralX) > maxRadius || Math.abs(spiralZ) > maxRadius) {
            // Next hotspot
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
