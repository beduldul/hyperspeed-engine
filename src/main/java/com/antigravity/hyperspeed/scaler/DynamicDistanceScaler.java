package com.antigravity.hyperspeed.scaler;

import com.antigravity.hyperspeed.HyperSpeedMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class DynamicDistanceScaler {
    private int tickCounter = 0;
    private int currentViewDistance = 10;
    private final int minViewDistance = 6;
    private final int maxViewDistance = 12;

    public int getCurrentViewDistance() {
        return currentViewDistance;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || !server.isRunning()) return;

        tickCounter++;
        // Check every 60 ticks (3 seconds)
        if (tickCounter % 60 != 0) return;

        PlayerList playerList = server.getPlayerList();
        if (playerList.getPlayerCount() == 0) return;

        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;

        // If server is experiencing load (MSPT > 42ms), dynamically scale down view distance
        if (mspt > 42.0 && currentViewDistance > minViewDistance) {
            currentViewDistance--;
            playerList.setViewDistance(currentViewDistance);
            HyperSpeedMod.LOGGER.info("[HyperSpeed Engine] ⚡ Adaptive MSPT Guard: Scaled View-Distance to {} chunks (MSPT: {:.1f}ms) to prevent lag.", currentViewDistance, mspt);
        } 
        // If server is running cold (MSPT < 25ms), scale back up to max
        else if (mspt < 25.0 && currentViewDistance < maxViewDistance) {
            currentViewDistance++;
            playerList.setViewDistance(currentViewDistance);
        }
    }
}
