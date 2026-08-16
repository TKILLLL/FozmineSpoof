# ⚙️ Configuration Details Reference

This document provides a comprehensive reference for all configuration options available in `config.yml`, `messages.yml`, and the specialized configuration files located in the `chats/` directory.

---

## 📑 Table of Contents

1. [`config.yml` — Main Configuration](#1-configyml--main-configuration)

    * [Database & Storage Engine](#database--storage-engine)
    * [Proxy Bridging Matrix](#proxy-bridging-matrix)
    * [Plugin Settings & Brand Masking](#plugin-settings--brand-masking)
    * [Fake Player Lifecycle & Population Scaling](#fake-player-lifecycle--population-scaling)
    * [Rank Weight Distribution (LuckPerms)](#rank-weight-distribution-luckperms)
    * [Automated Join Actions (AuthMe / nLogin Bypass)](#automated-join-actions-authme--nlogin-bypass)
    * [Dynamic Peak Hours (Traffic Fluctuations)](#dynamic-peak-hours-traffic-fluctuations)
    * [Chat System Core & Translation](#chat-system-core--translation)
2. [`messages.yml` — Localization & Colors](#2-messagesyml--localization--colors)
3. [`chats/interactive-messages.yml` — Keyword Engine](#3-chatsinteractive-messagesyml--keyword-engine)
4. [`chats/join-messages.yml` — Greeting System](#4-chatsjoin-messagesyml--greeting-system)
5. [`chats/random-messages.yml` — Ambient Chat Pool](#5-chatsrandom-messagesyml--ambient-chat-pool)

---

# 1. `config.yml` — Main Configuration

## Database & Storage Engine

Controls how fake-player data is persisted.

```yaml
Database:
  # Storage engine:
  #   false = SQLite
  #   true  = MySQL
  #
  # SQLite is recommended for standalone servers.
  # MySQL is recommended for multi-server networks.
  enable: false

  # Database table used to store fake-player data.
  name: "fozminespoof_players"

  # MySQL connection settings.
  # These options are only used when enable: true.
  database: "minecraft_db"
  host: "127.0.0.1"
  port: 3306
  user: "root"
  password: "your_secure_password"
```

### Options

| Option     | Type    | Description                                                  |
| ---------- | ------- | ------------------------------------------------------------ |
| `enable`   | Boolean | Enables MySQL when `true`; otherwise SQLite is used.         |
| `name`     | String  | Name of the database table used for fake-player persistence. |
| `database` | String  | MySQL database/schema name.                                  |
| `host`     | String  | MySQL server hostname or IP address.                         |
| `port`     | Integer | MySQL connection port.                                       |
| `user`     | String  | MySQL username.                                              |
| `password` | String  | MySQL password.                                              |

---

## Proxy Bridging Matrix

The proxy bridge allows multiple Minecraft servers to exchange fake-player information through a supported proxy environment such as BungeeCord, Waterfall, or Velocity.

```yaml
bridging-setting:
  # Enable proxy synchronization.
  enable-proxy: false

  # Unique identifier for this backend server.
  bungee_name: "survival-server-01"

  # Synchronization interval in seconds.
  # Supports fixed values and ranges.
  update-interval: "2-3"
```

### Options

| Option            | Type    | Description                                       |
| ----------------- | ------- | ------------------------------------------------- |
| `enable-proxy`    | Boolean | Enables synchronization with the proxy layer.     |
| `bungee_name`     | String  | Unique identifier of the current server/node.     |
| `update-interval` | String  | Frequency used to synchronize player information. |

> **Tip:** Each backend server should use a unique `bungee_name`.

---

## Plugin Settings & Brand Masking

General plugin settings and optional plugin-brand masking.

```yaml
Plugin-settings:
  # Dedicated world used to contain fake-player entities.
  botworld: "botworld"

  # Plugin information masking.
  fake-plugin-infomation:
    enable: true
    name: "FozmineSpawner"
    version: "6.67.7"
    authors:
      - phantam
    description: "High performance mob spawner management plugin"
    command: "spawner"
```

### `botworld`

The dedicated bot world is used to isolate fake-player entities from the main gameplay worlds.

This can help reduce unnecessary interaction between fake players and the main server environment.

### `fake-plugin-infomation`

Controls the plugin information presented through common Bukkit/Spigot commands.

When enabled, the plugin can mask information returned by commands such as:

* `/plugins`
* `/pl`
* `/version`
* `/ver`
* `/about`

| Option        | Type    | Description                                          |
| ------------- | ------- | ---------------------------------------------------- |
| `enable`      | Boolean | Enables plugin information masking.                  |
| `name`        | String  | Displayed plugin name.                               |
| `version`     | String  | Displayed plugin version.                            |
| `authors`     | List    | Authors displayed by the masked information.         |
| `description` | String  | Displayed plugin description.                        |
| `command`     | String  | Command information presented by the masking system. |

---

## Fake Player Lifecycle & Population Scaling

Controls fake-player visibility, connection messages, session duration, and automatic population scaling.

```yaml
Fakeplayer-setting:
  # Show fake players in the TabList.
  # false = visible
  # true  = hidden
  hide-in-tab: false

  # Enable join/leave messages.
  join-leave-message-enable: true

  # Message handling mode.
  # normal = server/default broadcast handling
  # custom = use custom messages below
  join-leave-format: "normal"

  join-message: "&8[&a+&8] &e%fakeplayer_name%"
  leave-message: "&8[&c-&8] &e%fakeplayer_name%"

  # Delay between fake-player join/quit operations.
  join-quit-interval: "1-5"

  # Fake-player session lifetime.
  lifetime-interval: "1800-3600"

  # Minimum number of fake players.
  base-amount: 10

  # Additional fake players based on real-player count.
  percent-rate: 10
```

### TabList Visibility

```yaml
hide-in-tab: false
```

* `false` — Fake players are visible in the TabList.
* `true` — Fake players are hidden from the TabList.

### Join & Leave Messages

```yaml
join-leave-message-enable: true
```

Controls whether fake-player connection and disconnection messages are broadcast.

```yaml
join-leave-format: "normal"
```

Available modes:

* `normal` — Uses the server's standard broadcast behavior.
* `custom` — Uses the configured `join-message` and `leave-message`.

### Join/Leave Templates

```yaml
join-message: "&8[&a+&8] &e%fakeplayer_name%"
leave-message: "&8[&c-&8] &e%fakeplayer_name%"
```

Available placeholder:

* `%fakeplayer_name%` — Fake-player username.

### Join/Quit Interval

```yaml
join-quit-interval: "1-5"
```

Defines the delay between consecutive fake-player connection or disconnection events.

A range such as `1-5` randomly selects a value between 1 and 5 seconds.

### Lifetime Interval

```yaml
lifetime-interval: "1800-3600"
```

Defines how long a fake player remains connected before naturally disconnecting.

The default range represents:

* `1800` seconds = 30 minutes
* `3600` seconds = 60 minutes

### Population Scaling

```yaml
base-amount: 10
percent-rate: 10
```

The target fake-player population is calculated using:

$$
\text{Target Bots} =
\text{base-amount}
+
\left(
\text{Real Players}
\times
\frac{\text{percent-rate}}{100}
\right)
$$

For example:

* Base amount: `10`
* Real players: `20`
* Percentage rate: `10%`

$$
10 + (20 \times 0.10) = 12
$$

Therefore, the target population is **12 fake players**.

---

## Rank Weight Distribution (LuckPerms)

Allows fake players to receive LuckPerms groups according to configurable probabilities.

```yaml
rank-weight:
  enable: true

  # LuckPerms group names.
  default: 50
  vip: 10
  mvp: 5
```

### Weight Calculation

The probability of selecting a group is calculated as:

$$
P(X) =
\frac{\text{Weight of X}}
{\text{Total Weight}}
$$

For the example above:

* `default` = 50
* `vip` = 10
* `mvp` = 5
* Total = 65

Therefore:

```text
default = 50 / 65 = 76.92%
vip     = 10 / 65 = 15.38%
mvp     = 5 / 65  = 7.69%
```

> **Important:** Group names must match the corresponding LuckPerms group names exactly.

---

## Automated Join Actions (AuthMe / nLogin Bypass)

Allows commands to be executed automatically when fake players connect.

```yaml
join-actions:
  # Commands executed by the fake player.
  fakeplayer:
    enable: true
    commands:
      - "login defaultpassword123"
      - "register defaultpassword123 defaultpassword123"

  # Commands executed by the server console.
  console:
    enable: false
    commands:
      - "lp user %fakeplayer_name% permission set fozminespoof.bot true"
```

### Fake-Player Commands

Commands under:

```yaml
join-actions:
  fakeplayer:
```

are executed as the fake player.

Example:

```yaml
commands:
  - "login defaultpassword123"
```

### Console Commands

Commands under:

```yaml
join-actions:
  console:
```

are executed from the server console.

Available placeholder:

* `%fakeplayer_name%`

Example:

```yaml
commands:
  - "lp user %fakeplayer_name% permission set fozminespoof.bot true"
```

> **Security:** Avoid using predictable passwords on production servers. Prefer dedicated bot accounts or authentication-compatible configuration where possible.

---

## Dynamic Peak Hours (Traffic Fluctuations)

Simulates changes in server population during configurable peak hours.

```yaml
fluctuations:
  enable: true

  # IANA timezone.
  timezone: "Asia/Ho_Chi_Minh"

  # Peak activity periods.
  active-hours:
    - "12:00-14:00"
    - "18:00-23:00"

  # Population configuration during peak periods.
  base-amount: 20
  percent-rate: 50
```

### Timezone

```yaml
timezone: "Asia/Ho_Chi_Minh"
```

Uses an IANA timezone identifier.

Examples:

```text
Asia/Ho_Chi_Minh
Asia/Tokyo
Europe/London
America/New_York
UTC
```

### Active Hours

```yaml
active-hours:
  - "12:00-14:00"
  - "18:00-23:00"
```

Defines the periods during which increased population settings are active.

Overnight ranges are also supported, for example:

```yaml
- "22:00-02:00"
```

### Peak Population

```yaml
base-amount: 20
percent-rate: 50
```

These values determine the fake-player population while the server is inside an active peak window.

---

## Chat System Core & Translation

Controls automated fake-player chat behavior and optional message translation.

```yaml
chat-system:
  enable: true

  # normal = deterministic chat engine
  # ai     = AI/LLM-based simulation engine
  mode: "normal"

  # Minimum real players required before bots chat.
  min-real-players: 1

  # Translation language.
  translation-target: "en"

  # Translation provider.
  translation-provider: "google"
  translation-api-key: ""

  # Random chat interval.
  interval-minutes: "5-15"

  # Number of bots that may speak.
  bots-per-interval: "1-2"

  # Delay between bot messages.
  delay-between-bots-seconds: "2-5"

  # Optional custom chat format.
  message-format:
    enable: false
    chat-format: "&7[&a%fakeplayer_name%&7]&f: %fakeplayer_message%"
```

### Chat Modes

```yaml
mode: "normal"
```

Supported modes:

* `normal` — Uses predefined keyword, greeting, and random-message systems.
* `ai` — Uses the AI chat system configured by the corresponding AI configuration.

### Minimum Real Players

```yaml
min-real-players: 1
```

Prevents automated bot chat from starting until the configured number of real players are online.

### Translation

```yaml
translation-target: "en"
```

Examples:

```text
en = English
vi = Vietnamese
es = Spanish
none = Disabled
```

Translation providers may include:

```yaml
translation-provider: "google"
```

Depending on the implementation, supported providers can include:

* `google`
* `gcloud`
* `deepl`
* `none`

The API key is configured using:

```yaml
translation-api-key: ""
```

### Chat Interval

```yaml
interval-minutes: "5-15"
```

Defines the random interval between ambient bot-chat cycles.

### Bots Per Interval

```yaml
bots-per-interval: "1-2"
```

Defines how many fake players may speak during each cycle.

### Delay Between Bots

```yaml
delay-between-bots-seconds: "2-5"
```

Adds a randomized delay between individual bot messages.

### Custom Chat Format

```yaml
message-format:
  enable: false
  chat-format: "&7[&a%fakeplayer_name%&7]&f: %fakeplayer_message%"
```

Available placeholders:

* `%fakeplayer_name%`
* `%fakeplayer_message%`

---

# 2. `messages.yml` — Localization & Colors

The `messages.yml` file contains configurable plugin messages, command responses, status indicators, and other localized text.

## Color Support

Messages support standard Minecraft legacy color codes:

```text
&0 &1 &2 &3 &4 &5 &6 &7 &8 &9
&a &b &c &d &e &f
&k &l &m &n &o &r
```

Hexadecimal colors are also supported:

```text
&#RRGGBB
```

or:

```text
#RRGGBB
```

Example:

```text
&#3B82F6
```

### Common Placeholders

| Placeholder          | Description                      |
| -------------------- | -------------------------------- |
| `%fakeplayer_name%`  | Fake-player username.            |
| `%world%`            | Current world name.              |
| `%x%`                | X coordinate.                    |
| `%y%`                | Y coordinate.                    |
| `%z%`                | Z coordinate.                    |
| `%yaw%`              | Yaw rotation.                    |
| `%pitch%`            | Pitch rotation.                  |
| `%uuid%`             | Fake-player UUID.                |
| `%status_formatted%` | Formatted online/offline status. |

### Example

```yaml
system:
  prefix: "&#3B82F6&lFozmineSpoof &#00F2FE▸ &r"
  no-permission: "&#EF4444 You do not have permission to execute this command!"
  reload-success: "&#10B981 Configuration reloaded and fake player system synchronized successfully."

bot:
  spawn-success: "&#10B981 Successfully spawned fake player &#F59E0B%fakeplayer_name%&#10B981 into world!"
  despawn-success: "&#F59E0B Successfully despawned fake player &#F59E0B%fakeplayer_name%&#F59E0B."
  status-online: "&#10B981● ONLINE"
  status-offline: "&#64748B○ OFFLINE"
```

---

# 3. `chats/interactive-messages.yml` — Keyword Engine

The interactive-message engine is used when:

```yaml
chat-system:
  mode: "normal"
```

It allows fake players to react to real-player messages based on configured triggers.

The engine can support:

* Exact or wildcard matching
* Fuzzy matching
* Levenshtein distance
* Regular expressions
* Response probabilities
* Global cooldowns
* Per-player cooldowns
* Burst limits
* Active-hour restrictions
* Typing delays

## Configuration Fields

| Field                  | Type    | Description                                                                         |
| ---------------------- | ------- | ----------------------------------------------------------------------------------- |
| `triggers`             | List    | Words, phrases, wildcard patterns, or regex patterns that activate the interaction. |
| `chance`               | Double  | Probability of triggering a response, from `0.0` to `1.0`.                          |
| `cooldowns.global`     | Integer | Global cooldown before another bot can respond.                                     |
| `cooldowns.per-player` | Integer | Cooldown before the same player can trigger the interaction again.                  |
| `max-burst`            | Integer | Maximum number of bots responding to one trigger.                                   |
| `delay-range`          | String  | Initial response delay range in seconds.                                            |
| `active-hours`         | String  | Time window during which the interaction is active.                                 |
| `typing-speed-range`   | String  | Simulated typing speed range.                                                       |
| `pause-between-words`  | String  | Additional pause between words.                                                     |
| `use-regex`            | Boolean | Enables regular-expression matching when `true`.                                    |
| `fuzzy-threshold`      | Double  | Similarity threshold used for fuzzy matching.                                       |
| `replies`              | List    | Pool of randomized responses.                                                       |

### Example: Economy Helper

```yaml
chat-interactions:
  shop-helper:
    use-regex: true

    triggers:
      - "\\b(?:how|where)\\s+sell\\b"
      - "sell\\s+items"
      - "\\bhow\\s+to\\s+(?:sell|trade|make\\s+money)\\b"

    chance: 0.9

    cooldowns:
      global: 10
      per-player: 20

    max-burst: 1

    delay-range: "2.0-3.5"

    active-hours: "00:00-23:59"

    replies:
      - "Type /shop to sell your items quickly [name]!"
      - "Use /shop bro"
```

### Trigger Matching

When:

```yaml
use-regex: true
```

the entries in `triggers` are treated as regular expressions.

When regex mode is disabled, the engine can use the configured wildcard/fuzzy matching behavior.

### Chance

```yaml
chance: 0.9
```

represents a 90% probability that the interaction will respond after a valid trigger is detected.

### Cooldowns

```yaml
cooldowns:
  global: 10
  per-player: 20
```

`global` prevents the interaction from being triggered too frequently across the server.

`per-player` prevents the same player from repeatedly triggering the interaction within the configured period.

### Burst Limit

```yaml
max-burst: 1
```

Controls how many fake players can respond to a single interaction.

### Response Placeholders

Supported response placeholders may include:

```text
[name]
[bot]
```

These can be replaced with the corresponding real-player or fake-player names.

---

# 4. `chats/join-messages.yml` — Greeting System

Controls automated greetings when players join the server.

The system supports different greeting categories for:

* First-time players
* Returning players
* Fake-player sessions

## New Player Greetings

```yaml
new-player-greetings:
  enabled: true
  max-burst: 3
  delay: 60
  phrases:
    - "welcome [name]!"
    - "Welcome to the server [name]!"
    - "Hey [name], welcome!"
```

These messages are intended for players joining the server for the first time.

The `[name]` placeholder represents the joining player's name.

## Existing Player Greetings

The `player-greetings` section can be used for returning players.

Example structure:

```yaml
player-greetings:
  enabled: true
  max-burst: 2
  delay: 60
  phrases:
    - "Welcome back [name]!"
    - "Hey [name], welcome back!"
```

## Session Join Chats

```yaml
session-join-chats:
  enabled: true
  max-burst: 2
  delay: 120
  phrases:
    - "hey guys"
    - "wassup folks"
    - "yo everyone"
```

These messages simulate casual greetings from fake players when they join a session.

### Configuration Fields

| Field       | Type    | Description                                |
| ----------- | ------- | ------------------------------------------ |
| `enabled`   | Boolean | Enables or disables the greeting category. |
| `max-burst` | Integer | Maximum number of bots that may respond.   |
| `delay`     | Integer | Delay before the greeting is sent.         |
| `phrases`   | List    | Randomized greeting messages.              |

---

# 5. `chats/random-messages.yml` — Ambient Chat Pool

Contains the messages used during regular ambient chat cycles.

These messages are designed to resemble natural Minecraft player conversations.

```yaml
random-messages:
  - "Been mining for 3 hours straight and haven't found a single diamond."
  - "Just hit the jackpot - 8 diamond ore vein! Time to make an enchanting table."
  - "Working on a medieval castle and can't get the towers to look right."
  - "Building a massive automatic storage system. Getting the redstone working finally."
  - "Just survived a raid on my village. My armor is almost broken though."
```

## Message Guidelines

For the most natural results, ambient messages should:

* Avoid repeating the same sentence too frequently.
* Use a mixture of short and long messages.
* Reference common Minecraft activities.
* Include different play styles and interests.
* Avoid making every bot sound identical.
* Mix casual conversation with gameplay-related comments.

### Example Categories

Suitable message themes include:

* Mining
* Building
* Redstone
* Farming
* Villagers
* Enchanting
* Nether exploration
* End exploration
* PvP
* Economy
* Trading
* Server events
* Casual conversation

---

# 🔧 Configuration Best Practices

## 1. Use Ranges for Natural Behavior

Instead of fixed values:

```yaml
join-quit-interval: "3"
```

prefer:

```yaml
join-quit-interval: "2-5"
```

Randomized ranges help prevent all fake players from behaving identically.

## 2. Avoid Excessive Population Scaling

A high `percent-rate` can create a large number of fake players on busy servers.

For example:

```yaml
percent-rate: 10
```

is considerably more conservative than:

```yaml
percent-rate: 100
```

## 3. Use Dedicated Database Storage for Networks

For a single server, SQLite is usually sufficient:

```yaml
Database:
  enable: false
```

For a multi-server network, MySQL can be used:

```yaml
Database:
  enable: true
```

## 4. Keep Chat Cooldowns Reasonable

Very low cooldowns can make fake-player conversations appear artificial.

For example:

```yaml
cooldowns:
  global: 10
  per-player: 20
```

provides a reasonable separation between automated responses.

## 5. Use Realistic Message Pools

Avoid having every fake player use the same vocabulary or sentence structure.

A larger message pool with varied phrasing produces more convincing ambient activity.

---

# 📌 Quick Configuration Reference

| Configuration              | Purpose                                           |
| -------------------------- | ------------------------------------------------- |
| `Database`                 | Database and persistence settings.                |
| `bridging-setting`         | Proxy/network synchronization.                    |
| `Plugin-settings`          | General plugin and information-masking settings.  |
| `Fakeplayer-setting`       | Fake-player lifecycle and population management.  |
| `rank-weight`              | LuckPerms rank probability distribution.          |
| `join-actions`             | Automated authentication and permission commands. |
| `fluctuations`             | Peak-hour population scaling.                     |
| `chat-system`              | Automated chat and translation configuration.     |
| `messages.yml`             | Plugin localization and message formatting.       |
| `interactive-messages.yml` | Keyword-triggered fake-player responses.          |
| `join-messages.yml`        | Automated join greetings.                         |
| `random-messages.yml`      | Ambient fake-player chat pool.                    |

---

# ✅ Final Notes

Always validate YAML indentation before starting the server.

A malformed YAML file can prevent the configuration from loading correctly.

Recommended workflow:

1. Back up the original configuration.
2. Edit one configuration section at a time.
3. Validate the YAML syntax.
4. Restart or reload the plugin.
5. Check the console for configuration errors.
6. Test fake-player spawning and chat behavior.
7. Verify database connectivity if MySQL is enabled.

For production networks, keep database credentials secure and avoid exposing authentication passwords or API keys inside publicly shared configuration files.
