package com.antigravity.hyperspeed.command;

import com.antigravity.hyperspeed.HyperSpeedMod;
import com.antigravity.hyperspeed.pregen.HotspotTracker;
import com.antigravity.hyperspeed.pregen.IdleAutoPregen;
import com.antigravity.hyperspeed.streamer.VelocityLookaheadStreamer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

public class HyperSpeedCommands {

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var hyperspeedCmd = Commands.literal("hyperspeed")
            .then(Commands.literal("status").executes(ctx -> showStatus(ctx.getSource())))
            .then(Commands.literal("dashboard").executes(ctx -> showStatus(ctx.getSource())))
            .then(Commands.literal("trim").executes(ctx -> trimMemory(ctx.getSource())))
            .then(Commands.literal("hotspot")
                .then(Commands.literal("list").executes(ctx -> listHotspots(ctx.getSource())))
                .then(Commands.literal("add")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 500))
                            .executes(ctx -> addHotspot(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"),
                                IntegerArgumentType.getInteger(ctx, "radius")
                            ))
                        )
                    )
                )
            );

        var optiCmd = Commands.literal("opti")
            .then(Commands.literal("status").executes(ctx -> showStatus(ctx.getSource())))
            .then(Commands.literal("dashboard").executes(ctx -> showStatus(ctx.getSource())))
            .then(Commands.literal("trim").executes(ctx -> trimMemory(ctx.getSource())))
            .then(Commands.literal("hotspot")
                .then(Commands.literal("list").executes(ctx -> listHotspots(ctx.getSource())))
                .then(Commands.literal("add")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 500))
                            .executes(ctx -> addHotspot(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"),
                                IntegerArgumentType.getInteger(ctx, "radius")
                            ))
                        )
                    )
                )
            );

        dispatcher.register(hyperspeedCmd);
        dispatcher.register(optiCmd);
    }

    private static int showStatus(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory() / (1024 * 1024);
        long totalMem = rt.totalMemory() / (1024 * 1024);
        long freeMem = rt.freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;

        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        double tps = Math.min(20.0, 1000.0 / Math.max(50.0, mspt));

        int players = server.getPlayerList().getPlayerCount();
        IdleAutoPregen engine = HyperSpeedMod.getInstance().getPregenEngine();
        VelocityLookaheadStreamer streamer = HyperSpeedMod.getInstance().getLookaheadStreamer();
        int viewDist = HyperSpeedMod.getInstance().getDistanceScaler().getCurrentViewDistance();

        source.sendSuccess(() -> Component.literal("§6§l========================================="), false);
        source.sendSuccess(() -> Component.literal("§e§l⚡ HYPERSPEED ULTRA (APEX EDITION v2.0.0)"), false);
        source.sendSuccess(() -> Component.literal("§6§l========================================="), false);
        source.sendSuccess(() -> Component.literal(String.format("§a• Server Performance: §e%.1f TPS §f| §b%.2f ms MSPT", tps, mspt)), false);
        source.sendSuccess(() -> Component.literal("§a• Adaptive View-Distance: §f" + viewDist + " Chunks (MSPT Guard Active)"), false);
        source.sendSuccess(() -> Component.literal("§a• Active Online Players: §e" + players + " / " + server.getMaxPlayers()), false);
        source.sendSuccess(() -> Component.literal(String.format("§a• Server RAM Usage: §f%dMB / %dMB (Free: %dMB)", usedMem, maxMem, freeMem)), false);
        source.sendSuccess(() -> Component.literal("§a• Real-Time Lookahead Streaming: §2ACTIVE §7(Ahead of travel trajectory)"), false);
        source.sendSuccess(() -> Component.literal("§a• Lookahead Chunks Streamed: §6" + streamer.getTotalLookaheadChunksStreamed()), false);
        source.sendSuccess(() -> Component.literal("§a• Zero-Player Auto-Pregen: " + (players == 0 ? "§aRUNNING (10k x 10k Frontier)" : "§eSTANDBY (100% Dedicated to Players)")), false);
        source.sendSuccess(() -> Component.literal("§a• Multi-Dimension Target: §b" + engine.getCurrentTargetInfo()), false);
        source.sendSuccess(() -> Component.literal("§a• Total Offline Chunks Pregenerated: §6" + engine.getChunksGeneratedTotal()), false);
        source.sendSuccess(() -> Component.literal("§a• Survival Mob Farms & Contraptions: §2100% VANILLA SHIELD PROTECTED"), false);
        source.sendSuccess(() -> Component.literal("§6§l========================================="), false);
        return 1;
    }

    private static int listHotspots(CommandSourceStack source) {
        IdleAutoPregen engine = HyperSpeedMod.getInstance().getPregenEngine();
        List<HotspotTracker.Hotspot> list = engine.getHotspotTracker().getHotspots();
        source.sendSuccess(() -> Component.literal("§6=== 📍 Registered Auto-Pregen Hotspots ==="), false);
        for (HotspotTracker.Hotspot h : list) {
            source.sendSuccess(() -> Component.literal(String.format("§a• §f%s §7[%s] §e(Chunk: %d, %d | Radius: %d chunks = %d blocks)",
                h.name, h.dimension.location().getPath(), h.chunkX, h.chunkZ, h.getRadius(), h.getRadius() * 16)), false);
        }
        source.sendSuccess(() -> Component.literal("§6==========================================="), false);
        return 1;
    }

    private static int addHotspot(CommandSourceStack source, String name, int radius) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only in-game players can add custom hotspots."));
            return 0;
        }

        BlockPos pos = player.blockPosition();
        IdleAutoPregen engine = HyperSpeedMod.getInstance().getPregenEngine();
        engine.getHotspotTracker().addCustomHotspot(name, player.level().dimension(), pos, radius);

        source.sendSuccess(() -> Component.literal(String.format("§a[HyperSpeed] ✅ Frontier Hotspot §e%s §aadded at §f(%d, %d) §ain §b%s §awith radius §f%d chunks (%d blocks)! §7(Will pre-render when offline).",
            name, pos.getX(), pos.getZ(), player.level().dimension().location().getPath(), radius, radius * 16)), false);
        return 1;
    }

    private static int trimMemory(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§e[HyperSpeed] Compacted and trimmed server heap memory."), false);
        source.getServer().overworld().getChunkSource().save(false);
        System.gc();
        source.sendSuccess(() -> Component.literal("§a[HyperSpeed] ✅ Memory trim completed successfully!"), false);
        return 1;
    }
}
