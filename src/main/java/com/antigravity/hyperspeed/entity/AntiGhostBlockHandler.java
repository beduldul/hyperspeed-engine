package com.antigravity.hyperspeed.entity;

import com.antigravity.hyperspeed.HyperSpeedMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class AntiGhostBlockHandler {

    /**
     * When a player attacks any entity (especially in mob farms),
     * proactively clears and resyncs any ghost cobwebs or obstructions around the target.
     */
    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        ServerLevel level = player.serverLevel();
        BlockPos targetPos = target.blockPosition();

        // Check target pos and block above
        BlockPos[] positionsToCheck = {targetPos, targetPos.above()};
        for (BlockPos pos : positionsToCheck) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.COBWEB)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                player.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
            }
        }
    }

    /**
     * When a player breaks or left-clicks any block (like a cobweb or ghost block),
     * immediately forces a block update packet to guarantee client-server synchronization.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) event.getLevel();
            BlockPos pos = event.getPos();
            BlockState state = level.getBlockState(pos);
            
            if (state.is(Blocks.COBWEB)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                player.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
            }
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            BlockPos pos = event.getPos();
            BlockState state = level.getBlockState(pos);
            
            // If server sees AIR but client hit it (classic ghost block), resync immediately!
            if (state.isAir()) {
                player.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
            } else if (state.is(Blocks.COBWEB)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                player.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
            }
        }
    }
}
