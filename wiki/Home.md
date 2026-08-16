# 🌟 FozmineSpoof - Ultra-Realistic FakePlayer & AI Simulation Engine

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19.4%20--%201.21.x-brightgreen.svg)](https://papermc.io)
[![Java Version](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur%20%7C%20Spigot-blue.svg)](https://papermc.io)
[![AI Engines](https://img.shields.io/badge/AI%20Engines-OpenAI%20%7C%20Gemini%20%7C%20Local%20LLM-purple.svg)](https://openai.com)
[![License](https://img.shields.io/badge/License-Commercial%20Enterprise-red.svg)](#)

Welcome to the official documentation for **FozmineSpoof** — the next-generation, high-performance fake player (NPC) and server ecosystem simulation solution designed for modern Minecraft Paper/Spigot networks.

---

## 🚀 Key Features

### ⚡ 1. Peak Performance & Zero-Lag Architecture (0% Idle CPU Overhead)
* **Isolated Void World:** Automatically provisions and maintains an isolated void world (`botworld`) to hold simulated player entities without ticking unnecessary survival chunks.
* **Non-Ticking NPC Entities:** Custom NMS entity abstraction (`FakeServerPlayer`) suppresses heavy player tick loops, mob target recalculations, physics, potion effects, and hunger.
* **O(1) In-Memory Cache:** All bot states, registry mappings, and online statuses are managed concurrently in RAM, eliminating disk I/O bottlenecks.

### 🧠 2. Neural AI Engine & Advanced Chat Simulation
* **Multi-Provider AI Support:** Out-of-the-box integration with **OpenAI GPT** (`gpt-4o-mini`, `gpt-4o`), **Google Gemini** (`gemini-1.5-flash`), and self-hosted **Local LLMs** (`Ollama`, `LM Studio`, `vLLM`).
* **200 Personalities & 200 Speaking Styles:** Every bot is randomly assigned an authentic Minecraft archetype (Strip Miner, Builder, PvP Tryhard, Casual Nomad) along with human typing quirks (typos, abbreviations, teen slang, emojis).
* **24/7 In-Game Support Desk:** Dedicated AI help agent (default: `@FozmineBot`) autonomously resolves player inquiries regarding store links, gameplay, rules, and server features based on your custom *Knowledge Base*.
* **Ambient AI-to-AI Chatter:** Bots can initiate organic conversations and reply to each other in public chat to keep channels active and lively.
* **Real-Time Multilingual Translation:** Integrated support for Google Translate (free), Google Cloud Translation v3, and DeepL API.

### 🔄 3. Dynamic Lifecycle & Traffic Fluctuations (Peak Hours)
* **Dynamic Player Scaling:** Intelligently scales active bot counts based on real human player activity via configurable proportional ratios.
* **Peak Hour Scheduling:** Automatically triggers realistic traffic surges during customizable peak windows (e.g., `12:00-14:00`, `18:00-23:00`) matching your server's timezone.
* **Randomized Session Durations:** Bots maintain randomized session lifetimes (e.g., 30–60 minutes) before logging out naturally and cycling in new identities.

### 🛡️ 4. Enterprise Integrations & Stealth Anti-Detection
* **Authentication Gate Bypass:** Automatically dispatches `/login` and `/register` commands on join for plugins such as **AuthMe**, **nLogin**, and **ORA**.
* **LuckPerms Weight Distribution:** Automatically assigns permissions, ranks, prefixes, and suffixes to simulated players based on weighted distributions.
* **Brand Masking & Interception:** Blocks and spoofs outputs for `/plugins`, `/pl`, `/version`, and `/about` with realistic fake plugin metadata.
* **Proxy Network Matrix:** Seamlessly synchronizes global network player counts across BungeeCord, Velocity, and Waterfall proxies using MySQL.

---

## 🗺️ Wiki Navigation

Explore the sections below to get started and configure advanced modules:

| Section | Description |
| :--- | :--- |
| 📖 **[[Setup-Guide]]** | Step-by-step setup guide for standalone servers and multi-server proxy networks. |
| 🎮 **[[Commands-and-Permissions]]** | Complete directory of `/spoof` subcommands and permission nodes. |
| ⚙️ **[[Configuration-Details]]** | In-depth breakdown of `config.yml`, `messages.yml`, and `interactive-messages.yml`. |
| 🤖 **[[AI-Chat-Engine]]** | Guide for configuring OpenAI, Gemini, Local LLMs, prompts, and anti-jailbreak safeguards. |
| 📊 **[[Architecture-Diagrams]]** | Visual Mermaid diagrams illustrating system architecture, lifecycles, and chat workflows. |

---

## ⚡ Quick Start

1. Download the latest `.jar` release and place it into your server's `plugins/` directory (Paper/Purpur 1.19.4 – 1.21.x).
2. Start the server to generate default configuration files and assets.
3. Open `plugins/fozminespoof-core/config.yml` to customize your baseline bot count (`base-amount`) and auto-login credentials (`join-actions`).
4. Run `/spoof spawn 5` in-game or from the console to instantly spawn 5 simulated players!

---

*Developed and maintained by **Phantam**. For bug reports and feature requests, visit [GitHub Issues](https://github.com/TKILLLL/FozmineSpoof/issues).*