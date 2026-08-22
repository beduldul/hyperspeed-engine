package com.antigravity.hyperspeed.pregen;

import com.antigravity.hyperspeed.HyperSpeedMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;

public class IdleAutoPregen {
    private static final File STATE_FILE = new File("config/hyperspeed_pregen_state.txt");

    private final HotspotTracker hotspotTracker = new HotspotTracker();
    
    private boolean isRunning = false;
    private int idleCooldownTicks = 100; // 5 seconds after 0 players
    
    // Persistent Spiral State
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
    private final long minFreeDiskSpaceBytes = 2_000_000_000L; // 2GB safety limit

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
    public void onServerStarting(ServerStartingEvent event) {
        loadState();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        saveState();
        HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] 💾 Progress state saved safely during server shutdown.");
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (isRunning) {
            pause("Player " + event.getEntity().getName().getString() + " connected");
            saveState();
        }
        idleCooldownTicks = 100;
        HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] ☀️ Player {} joined: Background pregen paused. 100% resources allocated to gameplay.",
            event.getEntity().getName().getString());
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

        // 3. Resume pregen if not running (picks up exactly where it paused!)
        if (!isRunning) {
            startOrResumeIdlePregen();
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

    private void startOrResumeIdlePregen() {
        isRunning = true;
        chunksGeneratedThisSession = 0;
        List<HotspotTracker.Hotspot> spots = hotspotTracker.getHotspots();
        String spotName = currentHotspotIndex < spots.size() ? spots.get(currentHotspotIndex).name : "Frontier";
        HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] ⚡ Auto-Pregeneration RESUMED for [{}] at ring coordinate ({}, {}) [Total Chunks: {}].",
            spotName, spiralX, spiralZ, chunksGeneratedTotal);
    }

    public void pause(String reason) {
        if (isRunning) {
            isRunning = false;
            HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] ☀️ Instant Pause: {} (Session Chunks: {}). Saved exact frontier position ({}, {}).",
                reason, chunksGeneratedThisSession, spiralX, spiralZ);
        }
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

        // Periodic maintenance & persistent state save every 500 chunks
        if (chunksGeneratedThisSession > 0 && chunksGeneratedThisSession % progressReportThreshold < count) {
            level.getChunkSource().save(false);
            saveState();
            System.gc(); // Clean RAM safely during idle
            HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] 🚀 RESUMABLE FRONTIER: {} session chunks pre-rendered (Total: {}) for [{}] in {} (Coord: {}, {})",
                chunksGeneratedThisSession, chunksGeneratedTotal, spot.name, spot.dimension.location().getPath(), spiralX, spiralZ);
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
            saveState();
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

    private void resetSpiral() {
        spiralX = 0;
        spiralZ = 0;
        spiralDx = 0;
        spiralDz = -1;
    }

    public synchronized void saveState() {
        try {
            File parent = STATE_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("currentHotspotIndex=").append(currentHotspotIndex).append("\n");
            sb.append("spiralX=").append(spiralX).append("\n");
            sb.append("spiralZ=").append(spiralZ).append("\n");
            sb.append("spiralDx=").append(spiralDx).append("\n");
            sb.append("spiralDz=").append(spiralDz).append("\n");
            sb.append("chunksGeneratedTotal=").append(chunksGeneratedTotal).append("\n");
            for (HotspotTracker.Hotspot spot : hotspotTracker.getHotspots()) {
                sb.append("radius:").append(spot.name).append("=").append(spot.getRadius()).append("\n");
            }

            try (FileWriter fw = new FileWriter(STATE_FILE)) {
                fw.write(sb.toString());
            }
        } catch (Exception e) {
            HyperSpeedMod.LOGGER.error("[HyperSpeed Ultra] Failed to save pregen state", e);
        }
    }

    public synchronized void loadState() {
        if (!STATE_FILE.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(STATE_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("currentHotspotIndex=")) {
                    currentHotspotIndex = Integer.parseInt(line.substring(20));
                } else if (line.startsWith("spiralX=")) {
                    spiralX = Integer.parseInt(line.substring(8));
                } else if (line.startsWith("spiralZ=")) {
                    spiralZ = Integer.parseInt(line.substring(8));
                } else if (line.startsWith("spiralDx=")) {
                    spiralDx = Integer.parseInt(line.substring(9));
                } else if (line.startsWith("spiralDz=")) {
                    spiralDz = Integer.parseInt(line.substring(9));
                } else if (line.startsWith("chunksGeneratedTotal=")) {
                    chunksGeneratedTotal = Integer.parseInt(line.substring(21));
                } else if (line.startsWith("radius:")) {
                    String rest = line.substring(7);
                    int eq = rest.indexOf('=');
                    if (eq > 0) {
                        String name = rest.substring(0, eq);
                        int r = Integer.parseInt(rest.substring(eq + 1));
                        for (HotspotTracker.Hotspot spot : hotspotTracker.getHotspots()) {
                            if (spot.name.equals(name) && r > spot.getRadius()) {
                                spot.expandRadius(r - spot.getRadius());
                            }
                        }
                    }
                }
            }

            HyperSpeedMod.LOGGER.info("[HyperSpeed Ultra] 💾 RESTORED STATE: Resuming from Hotspot #{} at coordinate ({}, {}) with {} historical chunks!",
                currentHotspotIndex, spiralX, spiralZ, chunksGeneratedTotal);
        } catch (Exception e) {
            HyperSpeedMod.LOGGER.error("[HyperSpeed Ultra] Failed to load pregen state", e);
        }
    }
}
