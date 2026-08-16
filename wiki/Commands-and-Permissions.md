# 🎮 Commands & Permissions Reference

This document provides a comprehensive reference of all available administrative commands, aliases, subcommands, syntax options, and permission nodes for **FozmineSpoof**.

---

## 🔑 Permissions Hierarchy

FozmineSpoof features a clean and secure permission system designed for easy administration.

| Permission Node      | Default | Description                                                                        |
| :------------------- | :------ | :--------------------------------------------------------------------------------- |
| `fozminespoof.admin` | `op`    | Grants full administrative access to all `/spoof` subcommands and system controls. |

---

## ⚡ Main Command & Aliases

The primary entry point for managing fake players is `/spoof`.

* **Primary Command:** `/spoof`
* **Aliases:** `/fspoof`, `/fakeplayers`, `/fp`, `/fplayer`, `/fakeplayer`, `/fozminespoof`
* **Required Permission:** `fozminespoof.admin`

---

## 📋 Subcommands Directory

### 1. `/spoof add <name>`

Registers a new fake player profile into the database (**SQLite / MySQL**) and RAM cache.

* **Syntax:** `/spoof add <name>`
* **Arguments:**

    * `<name>`: The username of the bot (must be 3 to 16 alphanumeric characters and underscores `_`).
* **Behavior:**

    * Derives a deterministic offline UUID (`OfflinePlayer:<name>`).
    * Persists the record to storage with `is_active = false` (inactive/offline).
    * Automatically updates the in-memory RAM cache for instant $O(1)$ read availability.
* **Examples:**

```text
/spoof add Miner_Alex
/spoof add ShadowPvP
```

---

### 2. `/spoof spawn <name|*|amount>`

Spawns one, multiple, or all offline fake players into the server.

* **Syntax:** `/spoof spawn <name|*|amount>`
* **Arguments:**

    * `<name>`: Spawns a specific offline bot by username.
    * `<amount>`: Spawns a randomized batch of `N` offline bots from the database.
    * `*`: Queues all offline bots currently in the database for sequential login.
* **Behavior:**

    * Enforces the server slot capacity (`max-players` cap).
    * Applies human-like join cadence delays (`join-quit-interval`).
    * Dispatches `AsyncPlayerPreLoginEvent` to allow third-party validation.
    * Spawns the custom `FakeServerPlayer` entity in the isolated void world (`botworld`).
    * Fires `PlayerLoginEvent` and executes configured `/login` authentication actions.
    * Applies LuckPerms rank weight distributions and sends clientbound spawn/tablist packets.
* **Examples:**

```text
/spoof spawn Steve
/spoof spawn 5
/spoof spawn *
```

---

### 3. `/spoof despawn <name|*>`

Despawns active simulated players from the server and returns them to an offline state.

* **Syntax:** `/spoof despawn <name|*>`
* **Arguments:**

    * `<name>`: Despawns a specific active bot.
    * `*`: Queues all online bots for sequential departure.
* **Behavior:**

    * Updates the bot active state to `false` in SQLite / MySQL.
    * Fires standard `PlayerQuitEvent` for third-party plugin cleanup.
    * Broadcasts custom leave messages (if enabled).
    * Sends NMS destroy packets and removes entities from the tablist.
    * Resets transient LuckPerms ranks and cleans up memory mappings.
* **Examples:**

```text
/spoof despawn Steve
/spoof despawn *
```

---

### 4. `/spoof remove <name>`

Permanently deletes a fake player profile from the database and despawns the entity if online.

* **Syntax:** `/spoof remove <name>`
* **Arguments:**

    * `<name>`: The username of the bot to remove.
* **Behavior:**

    * Despawns the active entity from the server world.
    * Permanently executes a `DELETE` query in SQLite / MySQL.
    * Removes the bot profile from the RAM cache.
* **Example:**

```text
/spoof remove OldBot123
```

---

### 5. `/spoof list`

Displays a real-time summary of all registered fake players and their live network states.

* **Syntax:** `/spoof list`
* **Output Information:**

    * Fast $O(1)$ read directly from RAM cache.
    * Sorted alphabetically with distinct color badges for **ONLINE** (`§a`) and **OFFLINE** (`§c`).
    * Paginated if the pool exceeds 30 bots to prevent chat overflow.
* **Example Output:**

```text
=== FAKE PLAYER LIST (25) ===
Online (10): Alex, Steve, Notch, ShadowPvP, Technoblade...
Offline (15): Hunter, Alpha, Omega, Viper, Titan...
```

---

### 6. `/spoof info <name>`

Displays technical metadata and diagnostic information for a specific fake player.

* **Syntax:** `/spoof info <name>`
* **Arguments:**

    * `<name>`: The bot username to inspect.
* **Information Displayed:**

    * Display Name & Offline UUID
    * Real-Time Status (`ONLINE` / `OFFLINE`)
    * Registered World & Coordinates ($X, Y, Z$)
    * Head Rotation ($Yaw, Pitch$)
* **Example:**

```text
/spoof info Steve
```

---

### 7. `/spoof reload`

Hot-reloads all system configurations, messages, AI models, knowledge bases, and database caches.

* **Syntax:** `/spoof reload`
* **Operations Executed:**

    * Reloads `config.yml`, `messages.yml`, `chats/ai-chat-bot.yml`, and `chats/interactive-messages.yml`.
    * Verifies and auto-heals the isolated void world environment (`botworld`).
    * Resets and restarts the chat scheduler with updated intervals.
    * Updates TabList visibility states (`hide-in-tab` adjustments).
    * Auto-heals missing database records for currently active entities.
    * Reloads the AI Support Desk Knowledge Base and reassigns personality archetypes.
* **Example:**

```text
/spoof reload
```

---

## ⌨️ Tab Completion

FozmineSpoof features smart, context-aware auto-completion.

### Subcommand Completion

Typing:

```text
/spoof <TAB>
```

suggests all valid subcommands:

```text
add
spawn
despawn
remove
list
info
reload
```

### Contextual Name Suggestions

* `/spoof spawn <TAB>` suggests only **offline** bots, plus `*` and `<number>`.
* `/spoof despawn <TAB>` suggests only **online** bots and `*`.
* `/spoof info <TAB>` suggests all registered bots in the database.
* `/spoof remove <TAB>` suggests all registered bots in the database.

---

## 📌 Command Quick Reference

| Command                          | Purpose                                 | Permission           |
| :------------------------------- | :-------------------------------------- | :------------------- |
| `/spoof add <name>`              | Register a new fake player              | `fozminespoof.admin` |
| `/spoof spawn <name\|*\|amount>` | Spawn fake players                      | `fozminespoof.admin` |
| `/spoof despawn <name\|*>`       | Despawn fake players                    | `fozminespoof.admin` |
| `/spoof remove <name>`           | Permanently remove a fake player        | `fozminespoof.admin` |
| `/spoof list`                    | List registered fake players            | `fozminespoof.admin` |
| `/spoof info <name>`             | Inspect fake player information         | `fozminespoof.admin` |
| `/spoof reload`                  | Reload plugin configuration and systems | `fozminespoof.admin` |

---

## 🛡️ Administrative Notes

* All `/spoof` commands require `fozminespoof.admin`.
* The permission defaults to server operators (`op`).
* Fake player profiles may be stored in either **SQLite** or **MySQL**, depending on the configured database backend.
* Use `/spoof list` to verify the current bot pool before performing bulk spawn or despawn operations.
* Use `/spoof reload` after modifying configuration files that support runtime reloading.
