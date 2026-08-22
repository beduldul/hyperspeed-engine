package com.antigravity.hyperspeed.scaler;

import com.antigravity.hyperspeed.HyperSpeedMod;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Stable View Distance Manager:
 * Maintains rock-solid chunk streaming without rapid view-distance fluctuations
 * that cause client-side chunk mesh tearing or invisible void holes.
 */
public class DynamicDistanceScaler {
    private int currentViewDistance = 10;

    public int getCurrentViewDistance() {
        return currentViewDistance;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        // Kept stable: No rapid dynamic setViewDistance() calls to prevent client chunk desync
    }
}
