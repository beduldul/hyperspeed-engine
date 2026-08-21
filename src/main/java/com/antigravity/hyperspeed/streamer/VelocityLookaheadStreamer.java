package com.antigravity.hyperspeed.streamer;

import com.antigravity.hyperspeed.HyperSpeedMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;

public class VelocityLookaheadStreamer {
    private int tickCounter = 0;
    private long totalLookaheadChunksStreamed = 0;
    private final Set<Long> recentlyRequestedChunks = new HashSet<>();

    public long getTotalLookaheadChunksStreamed() {
        return totalLookaheadChunksStreamed;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || !server.isRunning()) return;

        tickCounter++;
        // Run lookahead cone sweep every 8 ticks (0.4s) for high responsiveness
        if (tickCounter % 8 != 0) return;

        // Clear cache if large
        if (recentlyRequestedChunks.size() > 5000) {
            recentlyRequestedChunks.clear();
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            predictAndStreamAhead(player);
        }
    }

    private void predictAndStreamAhead(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level == null) return;

        Vec3 velocity = player.getDeltaMovement();
        double speedSq = velocity.x * velocity.x + velocity.z * velocity.z;

        double dirX;
        double dirZ;

        // If moving fast (sprinting, riding horse/boat, elytra, train)
        if (speedSq > 0.005) {
            double len = Math.sqrt(speedSq);
            dirX = velocity.x / len;
            dirZ = velocity.z / len;
        } else {
            // Use view direction vector
            Vec3 look = player.getViewVector(1.0f);
            double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
            if (hLen < 0.001) return;
            dirX = look.x / hLen;
            dirZ = look.z / hLen;
        }

        // Perpendicular vector for cone width
        double perpX = -dirZ;
        double perpZ = dirX;

        int playerChunkX = player.blockPosition().getX() >> 4;
        int playerChunkZ = player.blockPosition().getZ() >> 4;

        // Project forward lookahead cone: distance from 4 to 14 chunks ahead
        int[] lookaheadDistances = {4, 6, 8, 10, 12, 14};
        int[] lateralOffsets = {-1, 0, 1};

        for (int dist : lookaheadDistances) {
            for (int lat : lateralOffsets) {
                int targetChunkX = (int) Math.round(playerChunkX + (dirX * dist) + (perpX * lat));
                int targetChunkZ = (int) Math.round(playerChunkZ + (dirZ * dist) + (perpZ * lat));

                long chunkKey = (((long) targetChunkX) << 32) | (targetChunkZ & 0xFFFFFFFFL);
                if (recentlyRequestedChunks.add(chunkKey)) {
                    // Non-blocking async request to generate/load chunk ahead of time
                    level.getChunkSource().getChunkFuture(targetChunkX, targetChunkZ, ChunkStatus.FULL, true)
                        .whenComplete((res, err) -> {
                            if (err == null && res != null) {
                                totalLookaheadChunksStreamed++;
                            }
                        });
                }
            }
        }
    }
}
