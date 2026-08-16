# 🧠 Neural AI Chat Simulation Engine

FozmineSpoof includes an advanced AI Chat Engine designed to simulate natural Minecraft conversations, provide automated player assistance, and generate contextual bot-to-bot interactions.

The engine supports modern Large Language Models (LLMs), multilingual conversations, configurable personalities, private messaging, intelligent chat interactions, and multiple security layers.

---

## 📑 Table of Contents

1. [Overview & Architecture](#1-overview--architecture)
2. [Provider Setup & Hyperparameters](#2-provider-setup--hyperparameters)

    * [OpenAI](#openai)
    * [Google Gemini](#google-gemini)
    * [Local / Self-Hosted LLMs](#local--self-hosted-llms)
3. [Multilingual & Regional Slang Engine](#3-multilingual--regional-slang-engine)
4. [Output Sanitization & Anti-AI Leak](#4-output-sanitization--anti-ai-leak)
5. [Interaction Modes](#5-interaction-modes)

    * [Mode A: Player-to-AI Interaction](#mode-a-player-to-ai-interaction)
    * [Mode B: Ambient Bot-to-Bot Chatter](#mode-b-ambient-bot-to-bot-chatter)
    * [Mode C: In-Game Support Desk](#mode-c-in-game-support-desk)
6. [AI Personalities & Speaking Styles](#6-ai-personalities--speaking-styles)
7. [Private Messaging Simulation](#7-private-messaging-simulation)
8. [Chat Tab Completion & Smart Starters](#8-chat-tab-completion--smart-starters)
9. [Security Shield & Anti-Jailbreak Safeguards](#9-security-shield--anti-jailbreak-safeguards)
10. [Cost & Performance Best Practices](#10-cost--performance-best-practices)

---

# 1. Overview & Architecture

The AI Chat Engine is designed around asynchronous processing so external AI requests do not block the Minecraft server's main thread.

The engine handles:

* AI API requests
* Prompt construction
* Personality injection
* Language detection
* Conversation context
* Response sanitization
* Rate limiting
* Output filtering
* Conversation memory

These operations should be performed asynchronously using Java's non-blocking HTTP facilities.

> **Performance Note:** AI processing is designed to avoid blocking the main Minecraft thread. Actual server impact still depends on provider latency, request frequency, response processing, and server hardware.

## Enabling AI Mode

Open:

```text
plugins/fozminespoof-core/config.yml
```

and configure:

```yaml
chat-system:
  enable: true
  mode: "ai"
```

The AI-specific configuration is stored in:

```text
plugins/fozminespoof-core/chats/ai-chat-bot.yml
```

---

# 2. Provider Setup & Hyperparameters

FozmineSpoof can be configured to communicate with different AI providers.

The exact model names available depend on the provider and API version being used.

---

## OpenAI

**Recommended for:**

* Fast conversational responses
* Natural roleplay
* Multi-turn conversations
* General-purpose Minecraft assistance
* Production deployments

Example configuration:

```yaml
ai-settings:
  model: "GPT"
  api-key: "sk-proj-your-openai-api-key-here"

  providers:
    gpt:
      model-name: "gpt-4o-mini"
      max-tokens: 64
      temperature: 0.45
      presence-penalty: 1.2
      frequency-penalty: 1.5
```

### Parameters

| Option              | Description                                                     |
| ------------------- | --------------------------------------------------------------- |
| `model`             | Selects the configured AI provider.                             |
| `api-key`           | API credential used to authenticate requests.                   |
| `model-name`        | Model identifier sent to the provider.                          |
| `max-tokens`        | Maximum response length.                                        |
| `temperature`       | Controls response randomness.                                   |
| `presence-penalty`  | Encourages the model to introduce different topics and wording. |
| `frequency-penalty` | Reduces repetitive wording.                                     |

### Recommended Response Length

Minecraft chat usually benefits from short responses.

For example:

```yaml
max-tokens: 64
```

can help prevent unnecessarily long AI messages.

> **Security:** Never publish a real API key in documentation, Git repositories, screenshots, or public configuration files.

---

## Google Gemini

Gemini can be used as an alternative AI provider.

Example:

```yaml
ai-settings:
  model: "GEMINI"
  api-key: "AIzaSy-your-gemini-api-key-here"

  providers:
    gemini:
      model-name: "gemini-1.5-flash"
      max-tokens: 64
      temperature: 0.45
```

### Recommended Use Cases

Gemini can be suitable for:

* Fast conversational responses
* Lightweight chat interactions
* Multilingual conversations
* High-frequency short messages

> **Note:** Model availability and API behavior can change over time. Always use a model currently supported by your configured Gemini API endpoint.

---

## Local / Self-Hosted LLMs

FozmineSpoof can also communicate with local AI servers that expose an OpenAI-compatible API.

This is useful for:

* Offline deployments
* Private environments
* Reduced third-party API dependency
* Internal testing
* Custom AI infrastructure

Example:

```yaml
ai-settings:
  model: "CUSTOM"
  api-key: ""

  providers:
    custom-local:
      api-url: "http://localhost:11434/v1"
      model-name: "qwen2.5:7b"
      max-tokens: 64
      temperature: 0.45
```

Common local platforms may include:

* Ollama
* LM Studio
* vLLM
* Other OpenAI-compatible inference servers

The endpoint must support the API format expected by the FozmineSpoof AI provider implementation.

---

# 3. Multilingual & Regional Slang Engine

The language engine can detect the language used by a player and instruct the AI to respond in the same language.

Example:

```yaml
language-settings:
  mode: "auto"
  default-language: "en"

  language-hints:
    en: "Use casual English gamer slang such as lol, bro, brb, ngl and smh."
    vi: "Dùng tiếng Việt tự nhiên khi chat game, có thể sử dụng teen-code nhẹ và cách nói thân thiện."
    es: "Chatea en español casual de gamer."
    zh: "使用自然的简体中文网络游戏聊天用语."
    de: "Verwende natürlichen deutschen Gamer-Slang."
```

## Language Modes

### Automatic Detection

```yaml
mode: "auto"
```

The engine determines the language from the incoming message.

### Fixed Language

A fixed language can be used when all AI responses should follow one language.

```yaml
mode: "fixed"
default-language: "en"
```

### Default Language

```yaml
default-language: "en"
```

Used when the language cannot be reliably detected.

---

# 4. Output Sanitization & Anti-AI Leak

The output-sanitization layer modifies AI responses to better match the configured speaking style.

Example:

```yaml
output-sanitization:
  force-lowercase: true
  force-no-punctuation: true
  override-by-speaking-style: true
  disable-sanitization-for-help: true
  non-ascii-handling: "auto-detect"
  timeout-message: "sorry bro i has to sleep now!!"
```

## Configuration

| Option                          | Description                                        |
| ------------------------------- | -------------------------------------------------- |
| `force-lowercase`               | Converts responses to lowercase.                   |
| `force-no-punctuation`          | Removes or minimizes punctuation.                  |
| `override-by-speaking-style`    | Allows personality settings to control formatting. |
| `disable-sanitization-for-help` | Keeps more formal formatting for the support bot.  |
| `non-ascii-handling`            | Controls handling of non-ASCII characters.         |
| `timeout-message`               | Message displayed when an AI request times out.    |

> **Important:** Output sanitization should not be treated as a guarantee that an AI response will never identify itself as an AI. Filtering and prompting can reduce unwanted disclosures, but they cannot guarantee perfect behavior.

---

# 5. Interaction Modes

FozmineSpoof supports multiple AI interaction modes.

---

## Mode A: Player-to-AI Interaction

Allows real players to communicate with active fake players through public chat.

Example:

```yaml
interaction-modes:
  player-to-ai:
    enabled: true
    response-chance: 0.15
    name-similarity-threshold: 0.78
```

### Name Matching

The similarity threshold allows the system to recognize minor spelling mistakes.

For example:

```text
Player types:
@Stevee

Bot name:
Steve
```

A sufficiently high similarity score may allow the engine to determine that the player intended to address `Steve`.

### Response Chance

```yaml
response-chance: 0.15
```

represents a 15% response probability after a valid interaction is detected.

This prevents every bot from responding to every message.

---

## Mode B: Ambient Bot-to-Bot Chatter

Allows fake players to initiate conversations with other fake players.

```yaml
interaction-modes:
  ai-to-ai:
    enabled: true
    initiate-chance: 0.05
    response-chance: 0.4

    initiation-prompt: |-
      [CONTEXT & ROLE]
      You are a real human Minecraft player named {listener}
      interacting on a live server with {selectedTarget}.

      [TASK]
      Start a short and natural Minecraft conversation.

      [TOPICS]
      Mining, building, diamonds, mobs, farming, crafting.

      [CONSTRAINTS]
      Maximum 5 words.
      Lowercase only.
      No punctuation.
      Use a casual gamer style.
```

### `initiate-chance`

Controls how frequently a bot attempts to start a conversation.

### `response-chance`

Controls the probability that the target bot responds to an AI-initiated conversation.

### Recommended Approach

Keep these values relatively low.

High autonomous-chat probabilities can cause excessive messages and make the conversation appear repetitive rather than natural.

---

## Mode C: In-Game Support Desk

A dedicated AI support bot can answer questions based on a configured knowledge base.

Example:

```yaml
interaction-modes:
  ai-help:
    enabled: true
    bot-name: "FozmineBot"
    response-chance: 1.0
    tag-prefix: "@"
    response-format: "&b@{bot} &8- &f{message}"

    knowledge-base:
      "server name": "Fozmine Studio"
      "gameplay": "Custom survival with bespoke RPG mechanics"
      "store": "Visit the official server store for ranks, cosmetics, and perks"
      "rules": "No cheating, no toxic behavior, no scamming"
      "discord": "Join the official community Discord"
```

A player can interact with the support bot using a message such as:

```text
@FozmineBot how do i get ranks?
```

The engine can then use the configured knowledge base and language settings to generate a response.

### Recommended Knowledge Base

Keep the knowledge base focused on verified server information:

* Server name
* Gameplay
* Commands
* Rules
* Store information
* Discord/community information
* Frequently asked questions
* Server-specific mechanics

---

# 6. AI Personalities & Speaking Styles

Each fake player can be assigned a persistent personality profile.

A personality can be composed of three major components:

1. Personality archetype
2. Speaking style
3. Current in-game context

---

## Personality Archetypes

Example personality categories include:

### Miners & Ore Collectors

Possible personalities:

* Diamond hunter
* Cave explorer
* Strip miner
* Resource collector
* Fortune-enchantment enthusiast

### Builders & Architects

Possible personalities:

* Medieval builder
* Modern architect
* Cottage builder
* Redstone builder
* Landscape designer

### PvP Players

Possible personalities:

* Axe fighter
* Bow specialist
* Shield user
* Arena enthusiast
* Competitive duelist

### Redstone Engineers

Possible personalities:

* Farm builder
* Piston-door designer
* Automation enthusiast
* Storage-system builder
* Redstone debugger

### Nomads & Casual Players

Possible personalities:

* Biome explorer
* Lost wanderer
* Casual survival player
* Dirt-house survivor
* Adventure-focused player

---

## Speaking Styles

Speaking styles determine how a personality communicates.

### Capitalization

Examples:

* Always lowercase
* Standard capitalization
* Random capitalization
* Capitalizes important words

### Punctuation

Examples:

* No punctuation
* Uses `...`
* Uses `!!`
* Frequently uses question marks
* Minimal punctuation

### Gamer Slang

Examples:

```text
dia
neth
rs
xp
vil
obby
tbh
ngl
fr
bruh
```

### Emoticons

Examples:

```text
:)
xD
:3
:P
OwO
UwU
```

---

## Current In-Game Situation

The engine can inject contextual information into the AI prompt.

Examples:

```text
Mining for diamonds in a deep cave
Building a base near spawn
Exploring the Nether
Farming crops
Preparing for PvP
Trading with villagers
```

This allows conversations to reflect what the fake player is supposedly doing.

---

# 7. Private Messaging Simulation

Players can communicate privately with active fake players using common Minecraft messaging commands.

Supported aliases may include:

```text
/msg
/tell
/w
/whisper
/pm
/m
/emsg
/etell
/ewhisper
/t
```

## Private Message Formatting

Example:

```yaml
chat-format:
  method: "normal"

  private-message:
    incoming-format: "&7[{bot} -> me] &f{message}"
    outgoing-format: "&7[me -> {bot}] &f{message}"
```

### Conversation Memory

Bots can maintain temporary player-specific conversation history.

Example:

```yaml
conversation-expiry: "60-90s"
max-responses-per-session: "2-3"
```

The conversation context expires after the configured period, helping prevent unlimited memory growth.

Example interaction:

```text
Player:
hey steve

Bot:
yo whats up

Player:
where do u get diamonds

Bot:
usually caves below deepslate lol
```

The bot can use the previous messages as short-term context when generating the next response.

---

# 8. Chat Tab Completion & Smart Starters

FozmineSpoof can provide intelligent chat suggestions.

## Bot Tag Completion

Typing:

```text
@
```

or:

```text
@partialName
```

can display matching online fake players.

The support bot can optionally be prioritized:

```text
@FozmineBot
```

## Question Starters

After selecting a bot:

```text
@FozmineBot 
```

pressing `<TAB>` can provide common starters such as:

```text
how
what
where
when
why
can
help
tell
```

This makes interacting with AI bots easier for players.

---

# 9. Security Shield & Anti-Jailbreak Safeguards

The AI engine includes configurable safeguards designed to reduce:

* Excessive API usage
* Prompt injection
* Oversized requests
* Unwanted AI-related output
* Repeated requests
* Potentially sensitive administrative queries

Example:

```yaml
security-safeguards:
  abort-api-on-violation: true
  max-input-length: 80

  rate-limiting:
    max-requests-per-minute: 2

    warn:
      enabled: true
      message: "&e&l[AI] &cSlow down! You must wait &6{time}s &cto ask again."

  output-filtration:
    block-code-blocks: true

    block-sensitive-words:
      - "as an ai"
      - "openai"
      - "language model"
      - "cannot fulfill"
      - "gemini"
      - "chatgpt"
      - "assistant"

  input-blacklist:
    message: "you cant use {word}"
    regex: true

    block-blacklist-words:
      - "code"
      - "script"
      - "java"
      - "python"
      - "make plugin"
      - "op"
      - "admin"
      - "rcon"
      - "sudo"
      - "database"
      - "password"
      - "system prompt"
      - "ignore everything"
      - "act as"
      - "you are now"
      - "bypass"
      - "jailbreak"
```

## API Request Limits

```yaml
max-input-length: 80
```

limits the maximum size of incoming player messages sent to the AI provider.

This helps control:

* Token consumption
* Abuse
* Extremely large prompts
* Unexpected request sizes

## Rate Limiting

```yaml
rate-limiting:
  max-requests-per-minute: 2
```

limits how frequently a player can trigger AI requests.

This is especially important when using paid API providers.

## Output Filtering

The output filter can remove responses containing configured phrases.

Example:

```yaml
block-sensitive-words:
  - "as an ai"
  - "language model"
  - "chatgpt"
```

This can reduce obvious AI-related responses.

> **Important:** Keyword filtering is a defensive layer, not a complete security boundary. Do not rely on it as the only protection for sensitive data or administrative functionality.

## Input Filtering

The input firewall can block selected requests before they are sent to the AI provider.

Potential categories include:

* Programming requests
* Administrative commands
* Database requests
* Password requests
* Prompt-injection attempts
* Jailbreak attempts

---

# 10. Cost & Performance Best Practices

AI features can generate external API traffic, so configuration should prioritize short responses and controlled request frequency.

## 1. Keep Responses Short

A small response limit is usually appropriate for Minecraft chat.

Example:

```yaml
max-tokens: 64
```

Short responses:

* Reduce API usage
* Reduce latency
* Improve readability
* Better match normal Minecraft chat

> Actual response latency depends on the provider, network connection, model, server hardware, and current API load. Do not assume a fixed sub-500 ms response time.

---

## 2. Use an Appropriate Model

For lightweight chat interactions, choose a model that provides a good balance between:

* Response quality
* Latency
* Cost
* Context requirements

The optimal model can change as providers release new models.

---

## 3. Limit Requests When Nobody Is Online

Use:

```yaml
chat-system:
  min-real-players: 1
```

This prevents unnecessary AI activity when the server has no real players.

For larger networks, this can significantly reduce unnecessary requests.

---

## 4. Keep Autonomous Chat Probabilities Low

For bot-to-bot conversations:

```yaml
initiate-chance: 0.05
response-chance: 0.4
```

provides conservative behavior.

Avoid extremely high probabilities unless the server has a specific use case for frequent automated conversations.

---

## 5. Use Conversation Expiration

Temporary conversation memory should expire automatically.

Example:

```yaml
conversation-expiry: "60-90s"
```

This prevents unnecessary memory growth and keeps conversations focused on recent context.

---

## 6. Protect API Credentials

Never commit real API credentials to public repositories.

Recommended practices:

* Use environment variables where supported.
* Keep production keys outside version control.
* Rotate exposed credentials immediately.
* Use separate development and production credentials.
* Restrict provider-side API permissions where possible.

---

# 🔧 Recommended Production Baseline

A conservative production configuration can look like:

```yaml
chat-system:
  enable: true
  mode: "ai"
  min-real-players: 1

ai-settings:
  model: "GPT"

  providers:
    gpt:
      model-name: "gpt-4o-mini"
      max-tokens: 64
      temperature: 0.45
      presence-penalty: 1.2
      frequency-penalty: 1.5

interaction-modes:
  player-to-ai:
    enabled: true
    response-chance: 0.15
    name-similarity-threshold: 0.78

  ai-to-ai:
    enabled: true
    initiate-chance: 0.05
    response-chance: 0.4

  ai-help:
    enabled: true
    bot-name: "FozmineBot"
    response-chance: 1.0

security-safeguards:
  abort-api-on-violation: true
  max-input-length: 80

  rate-limiting:
    max-requests-per-minute: 2
```

This configuration prioritizes short responses, controlled API usage, and conservative autonomous behavior.

---

# 📌 Quick Reference

| Feature            | Configuration                           |
| ------------------ | --------------------------------------- |
| Enable AI          | `chat-system.mode: "ai"`                |
| AI provider        | `ai-settings.model`                     |
| AI model           | `providers.*.model-name`                |
| Response length    | `max-tokens`                            |
| Creativity         | `temperature`                           |
| Language           | `language-settings`                     |
| Output formatting  | `output-sanitization`                   |
| Player interaction | `interaction-modes.player-to-ai`        |
| Bot-to-bot chat    | `interaction-modes.ai-to-ai`            |
| Support bot        | `interaction-modes.ai-help`             |
| Private messages   | Private messaging configuration         |
| Temporary memory   | `conversation-expiry`                   |
| API rate limiting  | `security-safeguards.rate-limiting`     |
| Input protection   | `security-safeguards.input-blacklist`   |
| Output filtering   | `security-safeguards.output-filtration` |

---

# ✅ Final Notes

Before enabling the AI Chat Engine in production:

1. Configure and protect the AI provider credentials.
2. Verify the selected model is available through the provider.
3. Test the AI engine with a small number of real players.
4. Configure conservative rate limits.
5. Keep AI responses short.
6. Verify multilingual responses.
7. Test player-to-AI interactions.
8. Test private messaging.
9. Test the support bot knowledge base.
10. Monitor API usage and server performance.
11. Review generated messages regularly.
12. Keep the plugin and provider integrations updated.

The AI Chat Engine should be treated as an asynchronous external service rather than a replacement for deterministic server logic. Core gameplay, permissions, economy, authentication, and administrative operations should remain controlled by deterministic plugin code.
