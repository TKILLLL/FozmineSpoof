---

# 🏗️ FozmineSpoof Architecture Documentation



Welcome to the internal architecture guide for **FozmineSpoof**. This document explains the high-level design, threading model, data flow, and multi-version NMS bridge implementation.

---

## 📌 1. High-Level Overview



FozmineSpoof is designed around a **Performance-First Architecture**. It maintains high server TPS by completely isolating NPC entity processing from standard Minecraft player ticking routines.

```text
+-----------------------------------------------------------------------+
|                           Minecraft Server                            |
+-----------------------------------------------------------------------+
                                    |
            +-----------------------+-----------------------+
            |                                               |
  [ Real Player Events ]                         [ Proxy Network Sync ]
            |                                               |
            v                                               v
+-----------------------------------------------------------------------+
|                     FozmineSpoof Core Subsystems                      |
|                                                                       |
| +--------------------+   +-------------------+   +--------------+     |
| | FakePlayerManager  |   |  BotLifecycleMgr  |   | Chat Engine  |     |
| | (RAM Cache O(1))   |   |  (Auto-Scaling)   |   | (AI / Config)|     |
| +--------------------+   +-------------------+   +--------------+     |
+-----------------------------------------------------------------------+
            |                                               |
            v                                               v
+------------------------------+   +------------------------------------+
|  Database Layer (HikariCP)   |   | Versioned NMS Bridge Abstraction   |
|  (MySQL / SQLite WAL Mode)   |   | (:fozminespoof-v1_X_X)             |
+------------------------------+   +------------------------------------+

```

---

## 🧩 2. Module Breakdown



| Module | Responsibility |
| --- | --- |
| `:fozminespoof-api` | Public interfaces (`FozminespoofApi`, `IBotAction`, `FakePlayerData`). No NMS dependencies.

|
| `:fozminespoof-core` | Business logic, commands, AI chat processor, lifecycle manager, DB managers, event listeners.

|
| `:fozminespoof-v1_X_X` | Version-specific NMS implementation of `FozminespoofApi`. Directly accesses Minecraft NMS code.

|

---

## ⚡ 3. Threading & Performance Model



1. **Zero-Tick NPC Entities (`FakeServerPlayer`):**

* Overrides `ServerPlayer#tick()` to bypass hunger, potion, AI pathfinding, and movement ticks.


* Eliminates CPU overhead per bot.




2. **RAM Caching (`ConcurrentHashMap`):**

* All bot data is pre-cached in memory during startup (`FakePlayerManager`).


* Reading bot data operates at **O(1)** without disk I/O.




3. **Asynchronous I/O Pipeline:**

* Database queries, AI completions, and translation HTTP calls run exclusively on Bukkit Async Workers or `CompletableFuture`.


* Main thread is only used for Bukkit event dispatching and entity spawning/despawning.





---

## 🔌 4. Developer API Hooking

External plugins can interact with FozmineSpoof via `FozmineSpoofProvider`:

```java
if (FozmineSpoofProvider.isRegistered()) {
    FozminespoofApi api = FozmineSpoofProvider.get();
    
    // Spawn a bot dynamically
    Player bot = api.spawnPlayer("BotName", uuid, location, false);
}
```