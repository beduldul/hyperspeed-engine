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

        int playerCount = server.getPlayerList().getPlayerCount();
        if (playerCount == 0) return;

        tickCounter++;
        // Run lookahead cone sweep every 20 ticks (1.0s) smoothly
        if (tickCounter % 20 != 0) return;

        // Clear cache if large
        if (recentlyRequestedChunks.size() > 2000) {
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

        // Only stream ahead if moving actively (sprinting, riding, elytra, boat)
        if (speedSq > 0.01) {
            double len = Math.sqrt(speedSq);
            dirX = velocity.x / len;
            dirZ = velocity.z / len;
        } else {
            return; // If stationary or walking slowly, vanilla chunk sending already covers it
        }

        int playerChunkX = player.blockPosition().getX() >> 4;
        int playerChunkZ = player.blockPosition().getZ() >> 4;

        // Project forward lookahead: 3 to 8 chunks ahead along velocity vector
        int[] lookaheadDistances = {3, 5, 7};
        int[] lateralOffsets = {-1, 0, 1};

        for (int dist : lookaheadDistances) {
            for (int lat : lateralOffsets) {
                int targetChunkX = (int) Math.round(playerChunkX + (dirX * dist) - (dirZ * lat));
                int targetChunkZ = (int) Math.round(playerChunkZ + (dirZ * dist) + (dirX * lat));

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
