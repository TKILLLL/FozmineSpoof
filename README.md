Here is a professional, commercial-grade **`README.md`** file written in English, tailored for your **FozmineSpoof** project.

---

# 🤖 FozmineSpoof Core

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19.4%20to%201.21.11-brightgreen?style=for-the-badge&logo=minecraft)
![Java Version](https://img.shields.io/badge/Java-17%20%7C%2021-orange?style=for-the-badge&logo=java)
![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Spigot%20%7C%20BungeeCord%20%7C%20Velocity-blue?style=for-the-badge)
![AI Powered](https://img.shields.io/badge/AI-GPT--4o%20%7C%20Gemini%20%7C%20Local%20LLM-purple?style=for-the-badge&logo=openai)

**FozmineSpoof** is a commercial-grade, ultra-realistic FakePlayer (NPC / Bot Spoof) management solution for Minecraft Paper and Spigot servers. Engineered with custom version-isolated NMS Bridges, a zero-TPS-impact architecture, and a cutting-edge **Multilingual AI Chat Engine**, FozmineSpoof makes fake players indistinguishable from real human players.

---

## ✨ Key Features

### 🧠 Next-Gen Multilingual AI Chat Engine
* **LLM Integration:** Direct asynchronous support for **OpenAI (GPT-4o-mini)**, **Google Gemini 1.5**, and **Custom Local Models** (Ollama, LM Studio).
* **200+ Minecraft Personalities:** Bots are assigned distinct player archetypes (e.g., *y11 strip miner*, *aesthetic base designer*, *pvp tryhard*, *ancient debris seeker*).
* **200+ Gamer Typing Styles:** Simulates human typing habits (all-caps, missing punctuation, slang, emojis, typos with `*corrections`, net-speak).
* **Auto Language Detection:** Detects the player's language (English, Vietnamese, Spanish, German, Chinese, etc.) and responds in authentic local gamer slang.
* **Anti-Jailbreak & Security:** Multi-layered input/output filters, prompt injection shielding, character length limits, and rate-limiting.
* **Interactive AI Help (`@FozmineBot`):** Dedicated assistant bot that answers vanilla Minecraft questions and custom server commands.

### ⚡ Ultra-Low TPS Impact Architecture
* **Custom NMS Entities:** Overrides heavy `ServerPlayer` tick loops (bypassing pathfinding, potion ticks, and hunger processing).
* **Isolated Void World (`botworld`):** Entities are maintained in an isolated void environment to keep server performance at 20.0 TPS even with hundreds of active bots.
* **Multi-Version Bridge Adapter:** Dynamic reflection-based loading for Paper/Spigot versions **1.19.4, 1.20.1, 1.20.2, 1.20.4, 1.20.6, 1.21.1, 1.21.4, and 1.21.11+**.

### 📊 Dynamic Scaling & Network Bridging
* **Peak Hours / Fluctuations:** Automatically scales bot population based on time zones, peak hours, and real player count ratios.
* **High-Performance Database Engine:** Built-in **SQLite with WAL Mode** for zero disk-I/O locks, plus **MySQL HikariCP connection pooling**.
* **Proxy Bridging:** Synchronizes active/inactive counts across BungeeCord, Waterfall, or Velocity networks.

### 🎭 Realism & Brand Masking
* **TabList Customization:** Configurable TabList visibility and dynamic display names.
* **Rank Weight Distribution:** Automatically assigns groups/ranks via **LuckPerms**, **GroupManager**, **Vault**, **PEX**, or **UltraPermissions**.
* **Brand Interceptor:** Intercepts `/plugins` and `/pl` commands to mask system plugins under custom aliases (e.g., `FozmineSpawner`).

---

## 🛠️ Requirements & Compatibility

| Component | Minimum Requirement |
| :--- | :--- |
| **Java Version** | Java 17 or higher (Java 21 recommended for 1.20.6+) |
| **Server Software** | Paper, Purpur, Spigot, or Folia |
| **Supported MC Versions** | `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.4`, `1.21.11+` |
| **Database (Optional)** | SQLite (Built-in) or MySQL 5.7+ / MariaDB 10.3+ |

---

## 🚀 Quick Start & Installation

1. Download the latest `fozminespoof-core.jar` from the release build.
2. Drop `fozminespoof-core.jar` into your server's `plugins/` folder.
3. Start the server to generate the configuration files inside `plugins/fozminespoof-core/`.
4. Open `chats/ai-chat-bot.yml`:
    * Set `enabled: true`.
    * Choose your provider (`GPT`, `GEMINI`, or `CUSTOM`).
    * Enter your API Key in `api-key`.
5. Execute `/spoof reload` in-game or via console.

---

## 📂 Configuration Overview

When installed, FozmineSpoof creates the following directory structure:

```text
plugins/fozminespoof-core/
├── config.yml                      # Global lifecycle, database & fluctuation settings
├── messages.yml                    # System prefixes & command output messages
├── chats/
│   ├── ai-chat-bot.yml             # AI model providers, prompts, rate-limits & security
│   ├── ai/
│   │   ├── personalities.yml       # 200+ Minecraft player archetypes
│   │   └── speaking_styles.yml     # 200+ gamer typing patterns
│   ├── interactive-messages.yml    # Keyword-triggered static chat responses
│   ├── join-messages.yml           # Automated join/quit greeting chats
│   └── random-messages.yml         # Offline chat message pool
```

---

## 💻 Commands & Permissions

Permission Node: **`fozminespoof.admin`** (Default: OP)

| Subcommand | Syntax | Description |
| :--- | :--- | :--- |
| **Add** | `/spoof add <name>` | Registers a new inactive fake player entry into the database. |
| **Spawn** | `/spoof spawn <name\|*\|amount>` | Spawns specified bot(s) or bulk-spawns baseline allocation. |
| **Despawn** | `/spoof despawn <name\|*>` | Hides and despawns active fake player entities. |
| **Remove** | `/spoof remove <name>` | Permanently deletes a fake player from database & world. |
| **List** | `/spoof list` | Displays a summary of registered online and offline bots. |
| **Info** | `/spoof info <name>` | Shows status, location, world, and UUID of a bot. |
| **Reload** | `/spoof reload` | Hot-reloads configuration files, RAM cache, and AI engine. |

**Command Aliases:** `/fspoof`, `/fakeplayers`, `/fp`, `/fplayer`, `/fakeplayer`, `/fozminespoof`

---

## 🔌 Developer API Usage

Other plugins can interact with FozmineSpoof via the `fozminespoof-api` module.

### Gradle Dependency
```kotlin
dependencies {
    compileOnly(project(":fozminespoof-api"))
}
```

### Hooking into FozminespoofApi
```java
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.FozminespoofApi;

public class MyPluginHook {

    public void spawnBotExample(String botName, UUID uuid, Location location) {
        FozminespoofApi api = (FozminespoofApi) Bukkit.getPluginManager().getPlugin("fozminespoof-core");

        if (api != null) {
            // Spawn fake player entity
            Player fakePlayer = api.spawnPlayer(botName, uuid, location, false);

            // Broadcast NMS-level chat
            api.broadcastNMSChat(fakePlayer, "&aHello everyone!");
        }
    }
}
```

---

## 🏗️ Building from Source

This project uses Gradle Multi-Module with `paperweight`.

```bash
# Clone the repository
git clone https://github.com/TKILLLL/FozmineSpoof.git
cd FozmineSpoof

# Build the shadowed jar
./gradlew shadowJar
```

The compiled output jar will be located at:
`fozminespoof-core/build/libs/fozminespoof-core.jar`

---

## 👨‍💻 Author & License

* **Developer:** `phantam`
* **Project Module:** `FozmineSpoof Core`
* **License:** Commercial License. All rights reserved.

---
*Created for Minecraft Server Owners seeking high performance, complete realism, and zero compromises.*