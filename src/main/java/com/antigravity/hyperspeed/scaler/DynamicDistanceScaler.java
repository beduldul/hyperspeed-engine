package com.antigravity.hyperspeed.scaler;

import com.antigravity.hyperspeed.HyperSpeedMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class DynamicDistanceScaler {
    private int currentViewDistance = 10;
    private int tickCounter = 0;

    private static final int MIN_VIEW_DISTANCE = 6;
    private static final int MAX_VIEW_DISTANCE = 12;

    public int getCurrentViewDistance() {
        return currentViewDistance;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || !server.isRunning()) return;

        int playerCount = server.getPlayerList().getPlayerCount();
        if (playerCount == 0) return; // Only scale when players are active

        tickCounter++;
        // Check every 3 seconds (60 ticks)
        if (tickCounter % 60 == 0) {
            double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
            adjustDistance(server, mspt);
        }
    }

    private void adjustDistance(MinecraftServer server, double mspt) {
        int target = currentViewDistance;

        // Strict 20ms guard: If MSPT > 30ms, ease view distance smoothly
        if (mspt > 32.0 && currentViewDistance > MIN_VIEW_DISTANCE) {
            target = currentViewDistance - 1;
        } else if (mspt < 18.0 && currentViewDistance < MAX_VIEW_DISTANCE) {
            target = currentViewDistance + 1;
        }

        if (target != currentViewDistance) {
            currentViewDistance = target;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.serverLevel().getChunkSource().setViewDistance(currentViewDistance);
            }
            HyperSpeedMod.LOGGER.info(String.format("[HyperSpeed Ultra] ⚡ Dynamic Distance: Adjusted View-Distance to %d chunks (MSPT: %.1f ms)", currentViewDistance, mspt));
        }
    }
}
