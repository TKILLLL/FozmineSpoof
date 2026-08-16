# 🚀 Installation & Setup Guide

This guide walks you through setting up **FozmineSpoof** on standalone servers and multi-server proxy networks (**BungeeCord / Velocity / Waterfall**), integrating authentication bypass, and configuring rank weights.

---

## 📋 System Requirements & Compatibility

| Component                 | Minimum Requirement | Recommended                                     |
| :------------------------ | :------------------ | :---------------------------------------------- |
| **Java Runtime**          | Java 17             | Java 21 LTS                                     |
| **Server Software**       | Paper 1.19.4        | Paper / Purpur 1.20.x – 1.21.x                  |
| **Memory Allocation**     | 2 GB RAM            | 4+ GB RAM                                       |
| **Optional Dependencies** | —                   | **LuckPerms**, **AuthMe Reloaded** / **nLogin** |

### 🧩 Supported Minecraft Versions

FozmineSpoof provides dedicated native NMS abstraction bridges for the following server versions:

* `1.19.4`
* `1.20.1`
* `1.20.2`
* `1.20.4`
* `1.20.6`
* `1.21.1`
* `1.21.4`
* `1.21.11`

---

## 📦 Step 1: Basic Installation

### Standalone Server with SQLite

By default, FozmineSpoof operates out of the box with zero external database dependencies using high-performance **SQLite in WAL (Write-Ahead Logging) mode**.

### 1. Download

Get the latest release:

`fozminespoof-core-x.x.x.jar`

from the [GitHub Releases](https://github.com/TKILLLL/FozmineSpoof/releases) page.

### 2. Install

Place the `.jar` file into your server's `plugins/` directory:

```text
server/
└── plugins/
    └── fozminespoof-core-x.x.x.jar
```

### 3. Start the Server

Start your Minecraft server once to generate the default configuration files.

The resulting directory structure will look similar to:

```text
plugins/
└── fozminespoof-core/
    ├── config.yml
    ├── messages.yml
    └── chats/
        ├── ai-chat-bot.yml
        ├── interactive-messages.yml
        ├── join-messages.yml
        ├── random-messages.yml
        └── ai/
            ├── personalities.yml
            └── speaking_styles.yml
```

### 4. Automatic Void World Provisioning

During initialization, FozmineSpoof automatically creates and registers an isolated void world:

```text
botworld
```

Simulated entities reside in this dedicated world to prevent unnecessary chunk-rendering and entity-ticking overhead in the main survival world.

---

## 🌐 Step 2: Multi-Server Network Setup

### MySQL & Proxy Bridge

If you operate a multi-server network using **BungeeCord**, **Velocity**, or **Waterfall**, you can enable MySQL storage and proxy bridging to synchronize bot statistics across your network.

Open:

```text
plugins/fozminespoof-core/config.yml
```

and configure the `Database` section:

```yaml
Database:
  # Enable MySQL storage engine
  enable: true

  # Database table name for bot profiles
  name: "fozminespoof_players"

  # MySQL connection settings
  database: "minecraft_network"
  host: "127.0.0.1"
  port: 3306
  user: "mc_admin"
  password: "your_secure_password"

  bridging-setting:
    # Enable proxy synchronization
    enable-proxy: true

    # Unique identifier for this server node
    bungee_name: "survival-server-01"

    # Synchronization interval
    update-interval: "2-3"
```

> 💡 **How it works:**
> FozmineSpoof maintains an atomic proxy synchronization table using the configured node identifier. The proxy companion system can use this information to synchronize bot statistics across the network, including TabList and server status information.

### ⚠️ Security Recommendation

Never commit real MySQL credentials to a public GitHub repository.

Use a dedicated database user with only the permissions required by FozmineSpoof.

---

## 🔐 Step 3: Authentication Gate Bypass

### AuthMe / nLogin

If your server runs in offline/cracked mode with an authentication plugin such as **AuthMe**, **nLogin**, or **ORA**, simulated players may need to execute login or registration commands after joining.

Open:

```text
plugins/fozminespoof-core/config.yml
```

and configure:

```yaml
Fakeplayer-setting:
  join-actions:
    fakeplayer:
      # Enable automated player command execution
      enable: true

      commands:
        - "login MySecureBotPassword123"
        - "register MySecureBotPassword123 MySecureBotPassword123"
```

### Console Commands

You can also execute commands from the server console when a bot joins.

For example:

```yaml
Fakeplayer-setting:
  join-actions:
    console:
      enable: true

      commands:
        - "lp user %fakeplayer_name% permission set fozminespoof.bot true"
```

> ⚠️ **Important:** Replace example passwords with credentials appropriate for your server environment. Do not expose real credentials in public configuration files or repositories.

---

## 🎖️ Step 4: Configuring LuckPerms Rank Weights

FozmineSpoof can integrate with **LuckPerms** to distribute ranks, prefixes, and suffixes among simulated players.

Open:

```text
plugins/fozminespoof-core/config.yml
```

and configure:

```yaml
Fakeplayer-setting:
  rank-weight:
    enable: true

    # Weight distribution per rank.
    # Keys MUST exactly match your LuckPerms group names.
    default: 60
    vip: 25
    mvp: 15
```

### 📊 How the Distribution Works

The total weight is:

```text
60 + 25 + 15 = 100
```

Therefore:

* `default` → **60%**
* `vip` → **25%**
* `mvp` → **15%**

For example:

```text
VIP probability = 25 / (60 + 25 + 15)
                = 25%
```

### 🔒 Transient Assignment

Ranks are assigned non-destructively through transient permissions and are automatically cleared when the simulated player leaves.

---

## 📈 Step 5: Dynamic Player Scaling & Peak Hours

FozmineSpoof can dynamically adjust the simulated player population based on the number of real players currently online.

### 1. Proportional Scaling

Configure the following in `config.yml`:

```yaml
Fakeplayer-setting:
  # Guaranteed baseline bots online
  base-amount: 10

  # Scaling percentage based on real players
  #
  # Formula:
  # Total Bots = base-amount + (RealPlayers * percent-rate / 100)
  percent-rate: 10
```

### Example

If:

```text
base-amount = 10
real players = 40
percent-rate = 10
```

Then:

```text
Total Bots
= 10 + (40 × 10 / 100)
= 10 + 4
= 14
```

**Result: 14 simulated players.**

---

### 2. Peak Hours & Traffic Fluctuations

You can configure additional bot scaling during specific time windows:

```yaml
fluctuations:
  enable: true

  # Timezone used for calculating active hours
  timezone: "Asia/Ho_Chi_Minh"

  # Active peak windows
  active-hours:
    - "12:00-14:00"
    - "18:00-23:30"

  # Scaling configuration during peak hours
  base-amount: 25
  percent-rate: 50
```

Supported timezone examples include:

```text
Asia/Ho_Chi_Minh
America/New_York
UTC
Europe/London
```

---

## 🎭 Step 6: Anti-Detection & Plugin Masking

FozmineSpoof includes built-in packet and command interceptors that can mask the plugin identity when players execute commands such as:

```text
/plugins
/pl
/ver
/about
/version
```

Configure the masking system in `config.yml`:

```yaml
Plugin-settings:
  fake-plugin-infomation:
    enable: true

    name: "FozmineSpawner"
    version: "6.67.7"

    authors:
      - phantam

    description: "High performance mob spawner management plugin"

    command: "spawner"
```

This allows the server administrator to present an alternative plugin identity when plugin information is requested.

---

# ✅ Verification & Testing

After completing the configuration, use the following commands to verify the installation.

---

## 1. Check Status & Version

```text
/spoof list
```

Displays the registered simulated players and their current online/offline states.

---

## 2. Spawn Test Bots

```text
/spoof spawn 3
```

Spawns three simulated players using the configured join cadence and lifecycle settings.

---

## 3. Inspect Bot Data

```text
/spoof info Steve
```

Displays technical information about the selected simulated player, including:

* Display name
* UUID
* Current status
* World
* X / Y / Z coordinates
* Yaw
* Pitch

---

## 4. Hot Reload

```text
/spoof reload
```

Reloads the configured plugin systems without requiring a complete server restart.

Depending on the enabled features, this may reload:

* `config.yml`
* `messages.yml`
* Chat configuration
* AI configuration
* Knowledge-base mappings
* Personality mappings
* Runtime settings

---

# ❓ Troubleshooting Checklist

### Bots are not showing on the TabList

Check:

```yaml
Fakeplayer-setting:
  hide-in-tab: false
```

Make sure the value is set to:

```yaml
false
```

if you want simulated players to appear in the TabList.

---

### AuthMe kicks bots with `Login timeout`

Check:

```yaml
Fakeplayer-setting:
  join-actions:
    fakeplayer:
      enable: true
      commands:
        - "login YourPassword"
```

Make sure the configured commands match the authentication plugin and account requirements on your server.

---

### `Unsupported Minecraft version` appears during startup

Make sure your Paper, Purpur, or compatible server version is included in the supported version matrix.

Currently documented versions include:

```text
1.19.4
1.20.1
1.20.2
1.20.4
1.20.6
1.21.1
1.21.4
1.21.11
```

---

## 🧪 Recommended Installation Test

For a fresh installation, the recommended testing order is:

```text
1. Start the server
        ↓
2. Confirm FozmineSpoof loads successfully
        ↓
3. Run /spoof list
        ↓
4. Run /spoof spawn 1
        ↓
5. Verify the bot appears
        ↓
6. Test authentication actions
        ↓
7. Test LuckPerms rank assignment
        ↓
8. Test /spoof info <name>
        ↓
9. Test /spoof despawn <name>
        ↓
10. Test /spoof reload
```

If all steps complete successfully, the basic FozmineSpoof installation is ready for production configuration.
