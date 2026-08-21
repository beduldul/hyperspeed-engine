package com.antigravity.hyperspeed;

import com.antigravity.hyperspeed.command.HyperSpeedCommands;
import com.antigravity.hyperspeed.pregen.IdleAutoPregen;
import com.antigravity.hyperspeed.entity.EntityOptimizer;
import com.antigravity.hyperspeed.entity.AntiGhostBlockHandler;
import com.antigravity.hyperspeed.scaler.DynamicDistanceScaler;
import com.antigravity.hyperspeed.streamer.VelocityLookaheadStreamer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HyperSpeedMod.MODID)
public class HyperSpeedMod {
    public static final String MODID = "hyperspeed";
    public static final Logger LOGGER = LoggerFactory.getLogger("HyperSpeed");

    private static HyperSpeedMod instance;

    private final IdleAutoPregen pregenEngine;
    private final EntityOptimizer entityOptimizer;
    private final AntiGhostBlockHandler antiGhostBlockHandler;
    private final DynamicDistanceScaler distanceScaler;
    private final VelocityLookaheadStreamer lookaheadStreamer;

    public HyperSpeedMod(IEventBus modEventBus) {
        instance = this;

        LOGGER.info("==================================================");
        LOGGER.info("[HyperSpeed Ultra] Initializing v2.0.0 Apex Edition for NeoForge 1.21.1");
        LOGGER.info("[HyperSpeed Ultra] Real-Time Lookahead Vector Streamer: ACTIVE");
        LOGGER.info("[HyperSpeed Ultra] 10k x 10k Multi-Dimension Auto-Pregen: ACTIVE");
        LOGGER.info("[HyperSpeed Ultra] Anti-Ghost-Block & Cobweb Auto-Resync: ACTIVE");
        LOGGER.info("[HyperSpeed Ultra] Adaptive MSPT Scaler & Farm Shield: ACTIVE");
        LOGGER.info("==================================================");

        this.pregenEngine = new IdleAutoPregen();
        this.entityOptimizer = new EntityOptimizer();
        this.antiGhostBlockHandler = new AntiGhostBlockHandler();
        this.distanceScaler = new DynamicDistanceScaler();
        this.lookaheadStreamer = new VelocityLookaheadStreamer();

        NeoForge.EVENT_BUS.register(this.pregenEngine);
        NeoForge.EVENT_BUS.register(this.entityOptimizer);
        NeoForge.EVENT_BUS.register(this.antiGhostBlockHandler);
        NeoForge.EVENT_BUS.register(this.distanceScaler);
        NeoForge.EVENT_BUS.register(this.lookaheadStreamer);
        NeoForge.EVENT_BUS.addListener(HyperSpeedCommands::register);
    }

    public static HyperSpeedMod getInstance() {
        return instance;
    }

    public IdleAutoPregen getPregenEngine() {
        return pregenEngine;
    }

    public DynamicDistanceScaler getDistanceScaler() {
        return distanceScaler;
    }

    public VelocityLookaheadStreamer getLookaheadStreamer() {
        return lookaheadStreamer;
    }
}
