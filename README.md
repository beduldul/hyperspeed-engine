# ⚡ HyperSpeed Engine Ultra (NeoForge 1.21.1)

![NeoForge 1.21.1](https://img.shields.io/badge/NeoForge-1.21.1-blue.svg)
![Version](https://img.shields.io/badge/Version-2.0.0_Apex_Edition-green.svg)
![License](https://img.shields.io/badge/License-MIT-orange.svg)

**HyperSpeed Engine Ultra** is an advanced, high-performance optimization and chunk streaming mod built for NeoForge 1.21.1 Minecraft servers and clients.

Designed to eliminate exploration lag, chunk generation delay, rubberbanding, and ghost block desyncs when players travel thousands of blocks at high speed across multiple dimensions.

---

## 🌟 Key Features

### 1. 🚀 Predictive Lookahead Vector Chunk Streamer
* Tracks player velocity and look vector $(\Delta X, \Delta Z)$ every 0.4s.
* Dynamically projects an asynchronous forward cone 4–14 chunks ahead along your flight/travel path.
* Chunks are generated, lit, and resident in RAM **before** the player arrives, eliminating chunk border stalls and visual blinking.

### 2. 🌌 Multi-Dimension 10k x 10k Frontier Auto-Pregenerator
* When **0 players are online**, the engine automatically pre-renders a massive **10,000 x 10,000 block frontier** (radius 312 chunks = 5,000 blocks in all directions) around:
  - Overworld Base (`133, -198`) & World Spawn (`0, 0`)
  - Nether Expressway Hub (`16, -24`)
  - The End Dragon Realm (`0, 0`)
* Safely flushes region files and performs gentle memory compaction every 150 chunks.
* **Instant 0ms Pause:** Instantly stops the exact tick any player connects.

### 3. 🛡️ Anti-Ghost Block & Cobweb Resync Guard
* Automatically intercepts and cleans desynced client-side ghost blocks (such as cobwebs in mob farms).
* Guarantees immediate block update synchronization between server and client.

### 4. ⚡ Adaptive MSPT Guard
* Real-time server MSPT monitor that dynamically scales view-distance during heavy contraption loads to maintain a rock-solid **20.0 TPS**.

### 5. 🛡️ 100% Survival & Mob Farm Protection Shield
* Spawners, iron farms, villager trading halls, and Create mechanical contraptions remain 100% untouched and run at full vanilla tick speed.

---

## 🎮 In-Game Commands

| Command | Description |
|---|---|
| `/opti dashboard` | View real-time TPS, MSPT, RAM usage, and pregeneration metrics |
| `/opti status` | Quick status overview |
| `/opti trim` | Manually flush chunk cache and trim server memory |
| `/opti hotspot add <name> <radius>` | Add custom location for offline pregeneration |
| `/opti hotspot list` | List all registered pregeneration hotspots |

---

## 📦 Installation

1. Download `hyperspeed-2.0.0-neoforge-1.21.1.jar` from the **[Releases](https://github.com/beduldul/hyperspeed-engine/releases)** tab.
2. Place the `.jar` file into your Minecraft `.minecraft/mods` directory (client and server).
3. Requires **NeoForge 1.21.1**.
