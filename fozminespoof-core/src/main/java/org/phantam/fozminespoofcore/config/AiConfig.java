package org.phantam.fozminespoofcore.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.io.File;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiConfig {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File file;

    private String modelProvider;
    private String apiKey;

    // Language Settings
    private String langMode;
    private String defaultLanguage;
    private Map<String, String> languageHints = new HashMap<>();

    // Sanitization
    private boolean forceLowercase;
    private boolean forceNoPunctuation;
    private boolean overrideBySpeakingStyle;
    private boolean disableSanitizationForHelp;
    private String nonAsciiHandling;
    private String timeoutMessage;

    // Chat Format & PM
    private String chatFormatMethod;
    private String chatFormat;
    private boolean privateMessageEnabled;
    private String pmIncomingFormat;
    private String pmOutgoingFormat;

    // Interaction Modes
    private boolean playerToAiEnabled;
    private double playerToAiChance;
    private double nameSimilarityThreshold;

    private boolean aiToAiEnabled;
    private double aiToAiInitiateChance;
    private double aiToAiResponseChance;
    private String aiToAiInitiationPrompt;

    private boolean aiHelpEnabled;
    private String aiHelpBotName;
    private double aiHelpResponseChance;
    private String aiHelpTagPrefix;
    private String aiHelpMinecraftPrompt;
    private String aiHelpPluginPrompt;

    // Prompt Engineering
    private String systemRule;

    // Conditions
    private boolean answerInSameWorld;
    private int maxHearingDistance;
    private String timeZone;
    private String activeHours;

    // Timing
    private String typingDelayStr;
    private int cooldownReceiverSec;
    private int cooldownSenderSec;
    private int conversationExpirySec;
    private int maxResponsesPerSession;
    private boolean closeOnNewPlayerMention;

    // Providers
    private ProviderConfig gptConfig;
    private ProviderConfig geminiConfig;
    private CustomProviderConfig customConfig;

    // Security
    private boolean abortApiOnViolation;
    private int maxInputLength;
    private int rateLimitMaxPerMin;
    private boolean rateLimitWarnEnabled;
    private String rateLimitWarnMessage;
    private boolean rateLimitWarnActionBar;
    private boolean rateLimitPunishmentEnabled;
    private List<String> rateLimitPunishmentCommands;

    private boolean blockCodeBlocks;
    private List<String> blockSensitiveWords;

    // Input Blacklist
    private String inputBlacklistMessage;
    private boolean inputBlacklistRegex;
    private List<String> inputBlacklistWords;

    public AiConfig(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.file = new File(plugin.getDataFolder(), "chats/ai-chat-bot.yml");
        this.reload();
    }

    public void reload() {
        try {
            ensureDefaultFileExists();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            modelProvider = config.getString("ai-settings.model", "GPT").toUpperCase();
            apiKey = config.getString("ai-settings.api-key", "");

            // Language Settings
            langMode = config.getString("ai-settings.language-settings.mode", "auto");
            if (langMode == null || langMode.isBlank()) langMode = "auto";

            defaultLanguage = config.getString("ai-settings.language-settings.default-language", "en");
            if (defaultLanguage == null || defaultLanguage.isBlank()) defaultLanguage = "en";

            languageHints.clear();
            if (config.isConfigurationSection("ai-settings.language-settings.language-hints")) {
                var sec = config.getConfigurationSection("ai-settings.language-settings.language-hints");
                for (String k : sec.getKeys(false)) {
                    languageHints.put(k, sec.getString(k));
                }
            }

            // Sanitization
            forceLowercase = config.getBoolean("ai-settings.output-sanitization.force-lowercase", true);
            forceNoPunctuation = config.getBoolean("ai-settings.output-sanitization.force-no-punctuation", true);
            overrideBySpeakingStyle = config.getBoolean("ai-settings.output-sanitization.override-by-speaking-style", true);
            disableSanitizationForHelp = config.getBoolean("ai-settings.output-sanitization.disable-sanitization-for-help", true);
            nonAsciiHandling = config.getString("ai-settings.output-sanitization.non-ascii-handling", "auto-detect");
            timeoutMessage = config.getString("ai-settings.output-sanitization.timeout-message", "sorry bro i has to sleep now!!");

            // Chat Format & PM Settings
            chatFormatMethod = config.getString("ai-settings.chat-format.method", "normal");
            chatFormat = config.getString("ai-settings.chat-format.chat-format", "{bot}: &7{message}");
            privateMessageEnabled = config.getBoolean("ai-settings.chat-format.private-message.enabled", true);
            pmIncomingFormat = config.getString("ai-settings.chat-format.private-message.incoming-format", "&7[{bot} -> me] &f{message}");
            pmOutgoingFormat = config.getString("ai-settings.chat-format.private-message.outgoing-format", "&7[me -> {bot}] &f{message}");

            // Interaction Modes
            playerToAiEnabled = config.getBoolean("ai-settings.interaction-modes.player-to-ai.enabled", true);
            playerToAiChance = config.getDouble("ai-settings.interaction-modes.player-to-ai.response-chance", 0.15);
            nameSimilarityThreshold = config.getDouble("ai-settings.interaction-modes.player-to-ai.name-similarity-threshold", 0.78);

            aiToAiEnabled = config.getBoolean("ai-settings.interaction-modes.ai-to-ai.enabled", false);
            aiToAiInitiateChance = config.getDouble("ai-settings.interaction-modes.ai-to-ai.initiate-chance", 0.05);
            aiToAiResponseChance = config.getDouble("ai-settings.interaction-modes.ai-to-ai.response-chance", 0.4);
            aiToAiInitiationPrompt = config.getString("ai-settings.interaction-modes.ai-to-ai.initiation-prompt", "");

            aiHelpEnabled = config.getBoolean("ai-settings.interaction-modes.ai-help.enabled", true);
            aiHelpBotName = config.getString("ai-settings.interaction-modes.ai-help.bot-name", "FozmineBot");
            aiHelpResponseChance = config.getDouble("ai-settings.interaction-modes.ai-help.response-chance", 1.0);
            aiHelpTagPrefix = config.getString("ai-settings.interaction-modes.ai-help.tag-prefix", "@");
            aiHelpMinecraftPrompt = config.getString("ai-settings.interaction-modes.ai-help.topics.minecraft.system-instruction", "");
            aiHelpPluginPrompt = config.getString("ai-settings.interaction-modes.ai-help.topics.plugin.system-instruction", "");

            // Prompt Engineering
            systemRule = config.getString("ai-settings.prompt-engineering.system-rule", "");

            // Conditions
            answerInSameWorld = config.getBoolean("ai-settings.conditions.answer-in-same-world", false);
            maxHearingDistance = config.getInt("ai-settings.conditions.max-hearing-distance", -1);
            timeZone = config.getString("ai-settings.conditions.time-zone", "Asia/Ho_Chi_Minh");
            activeHours = config.getString("ai-settings.conditions.active-hours", "06:00-02:00");

            // Timing
            typingDelayStr = config.getString("ai-settings.timing.typing-delay", "1-3");
            cooldownReceiverSec = parseRangeMin("ai-settings.timing.cooldowns.receiver", config, 15);
            cooldownSenderSec = parseRangeMin("ai-settings.timing.cooldowns.sender", config, 20);
            conversationExpirySec = parseRangeMin("ai-settings.timing.cooldowns.conversation-expiry", config, 60);
            maxResponsesPerSession = parseRangeMin("ai-settings.timing.max-responses-per-session", config, 3);
            closeOnNewPlayerMention = config.getBoolean("ai-settings.timing.close-on-new-player-mention", true);

            // Providers
            gptConfig = new ProviderConfig(
                    config.getString("ai-settings.providers.gpt.model-name", "gpt-4o-mini"),
                    config.getInt("ai-settings.providers.gpt.max-tokens", 64),
                    config.getDouble("ai-settings.providers.gpt.temperature", 0.45),
                    config.getDouble("ai-settings.providers.gpt.presence-penalty", 1.2),
                    config.getDouble("ai-settings.providers.gpt.frequency-penalty", 1.5)
            );

            geminiConfig = new ProviderConfig(
                    config.getString("ai-settings.providers.gemini.model-name", "gemini-1.5-flash"),
                    config.getInt("ai-settings.providers.gemini.max-tokens", 64),
                    config.getDouble("ai-settings.providers.gemini.temperature", 0.45),
                    0.0, 0.0
            );

            customConfig = new CustomProviderConfig(
                    config.getString("ai-settings.providers.custom-local.api-url", "http://localhost:1234/v1"),
                    config.getString("ai-settings.providers.custom-local.model-name", "your-local-model"),
                    config.getInt("ai-settings.providers.custom-local.max-tokens", 64),
                    config.getDouble("ai-settings.providers.custom-local.temperature", 0.45)
            );

            // Security
            abortApiOnViolation = config.getBoolean("ai-settings.security-safeguards.abort-api-on-violation", true);
            maxInputLength = config.getInt("ai-settings.security-safeguards.max-input-length", 80);
            rateLimitMaxPerMin = config.getInt("ai-settings.security-safeguards.rate-limiting.max-requests-per-minute", 2);

            rateLimitWarnEnabled = config.getBoolean("ai-settings.security-safeguards.rate-limiting.warn.enabled", true);
            rateLimitWarnMessage = config.getString("ai-settings.security-safeguards.rate-limiting.warn.message", "&e&l[AI] &cSlow down!");
            rateLimitWarnActionBar = config.getBoolean("ai-settings.security-safeguards.rate-limiting.warn.action-bar", false);

            rateLimitPunishmentEnabled = config.getBoolean("ai-settings.security-safeguards.rate-limiting.punishment.command.enabled", false);
            rateLimitPunishmentCommands = config.getStringList("ai-settings.security-safeguards.rate-limiting.punishment.command.execute");

            blockCodeBlocks = config.getBoolean("ai-settings.security-safeguards.output-filtration.block-code-blocks", true);
            blockSensitiveWords = config.getStringList("ai-settings.security-safeguards.output-filtration.block-sensitive-words");

            inputBlacklistMessage = config.getString("ai-settings.security-safeguards.input-blacklist.message", "you cant use {word}");
            inputBlacklistRegex = config.getBoolean("ai-settings.security-safeguards.input-blacklist.regex", true);
            inputBlacklistWords = config.getStringList("ai-settings.security-safeguards.input-blacklist.block-blacklist-words");

            DebugLogger.log(plugin.getLogger(), "AiConfig: reloaded. Model=%s, PM Enabled=%b", modelProvider, privateMessageEnabled);

        } catch (Exception e) {
            plugin.getLogger().severe("[AiConfig] Failed to load chats/ai-chat-bot.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureDefaultFileExists() {
        File folder = new File(plugin.getDataFolder(), "chats");
        if (!folder.exists()) folder.mkdirs();
        if (!file.exists()) plugin.saveResource("chats/ai-chat-bot.yml", false);
    }

    private int parseRangeMin(String path, YamlConfiguration config, int def) {
        String val = config.getString(path, String.valueOf(def));
        if (val.contains("-")) {
            try { return Integer.parseInt(val.split("-")[0].trim()); } catch (Exception ignored) {}
        } else {
            try { return Integer.parseInt(val.trim()); } catch (Exception ignored) {}
        }
        return def;
    }

    public boolean isInActiveHours() {
        if (activeHours == null || activeHours.isBlank()) return true;
        try {
            ZoneId zone = ZoneId.of(timeZone != null ? timeZone : "Asia/Ho_Chi_Minh");
            LocalTime now = LocalTime.now(zone);
            String[] parts = activeHours.split("-");
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());

            if (start.isBefore(end)) return !now.isBefore(start) && now.isBefore(end);
            else return !now.isBefore(start) || now.isBefore(end);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isEnabled() {
        if (configManager == null) return false;
        ChatConfig chatConfig = configManager.getChatConfig();
        if (chatConfig == null || !chatConfig.isEnabled()) return false;
        return "ai".equalsIgnoreCase(chatConfig.getMode());
    }

    // Getters
    public String getModelProvider() { return modelProvider; }
    public String getApiKey() { return apiKey; }
    public String getLangMode() { return langMode != null ? langMode : "auto"; }
    public String getDefaultLanguage() { return defaultLanguage != null ? defaultLanguage : "en"; }
    public String getLanguageHint(String lang) {
        if (languageHints == null || languageHints.isEmpty()) return "Use casual gamer slang.";
        return languageHints.getOrDefault(lang, languageHints.getOrDefault("en", "Use casual gamer slang."));
    }

    public boolean isForceLowercase() { return forceLowercase; }
    public boolean isForceNoPunctuation() { return forceNoPunctuation; }
    public boolean isOverrideBySpeakingStyle() { return overrideBySpeakingStyle; }
    public boolean isDisableSanitizationForHelp() { return disableSanitizationForHelp; }
    public String getNonAsciiHandling() { return nonAsciiHandling; }
    public String getTimeoutMessage() { return timeoutMessage; }

    public String getChatFormatMethod() { return chatFormatMethod; }
    public String getChatFormat() { return chatFormat; }
    public boolean isPrivateMessageEnabled() { return privateMessageEnabled; }
    public String getPmIncomingFormat() { return pmIncomingFormat; }
    public String getPmOutgoingFormat() { return pmOutgoingFormat; }

    public boolean isPlayerToAiEnabled() { return playerToAiEnabled; }
    public double getPlayerToAiChance() { return playerToAiChance; }
    public double getNameSimilarityThreshold() { return nameSimilarityThreshold; }

    public boolean isAiToAiEnabled() { return aiToAiEnabled; }
    public double getAiToAiInitiateChance() { return aiToAiInitiateChance; }
    public double getAiToAiResponseChance() { return aiToAiResponseChance; }
    public String getAiToAiInitiationPrompt() { return aiToAiInitiationPrompt; }

    public boolean isAiHelpEnabled() { return aiHelpEnabled; }
    public String getAiHelpBotName() { return aiHelpBotName; }
    public double getAiHelpResponseChance() { return aiHelpResponseChance; }
    public String getAiHelpTagPrefix() { return aiHelpTagPrefix; }
    public String getAiHelpMinecraftPrompt() { return aiHelpMinecraftPrompt; }
    public String getAiHelpPluginPrompt() { return aiHelpPluginPrompt; }

    public String getSystemRule() { return systemRule; }
    public boolean isAnswerInSameWorld() { return answerInSameWorld; }
    public int getMaxHearingDistance() { return maxHearingDistance; }
    public String getTypingDelayStr() { return typingDelayStr; }
    public int getCooldownReceiverSec() { return cooldownReceiverSec; }
    public int getCooldownSenderSec() { return cooldownSenderSec; }
    public int getConversationExpirySec() { return conversationExpirySec; }
    public int getMaxResponsesPerSession() { return maxResponsesPerSession; }
    public boolean isCloseOnNewPlayerMention() { return closeOnNewPlayerMention; }

    public ProviderConfig getGptConfig() { return gptConfig; }
    public ProviderConfig getGeminiConfig() { return geminiConfig; }
    public CustomProviderConfig getCustomConfig() { return customConfig; }

    public boolean isAbortApiOnViolation() { return abortApiOnViolation; }
    public int getMaxInputLength() { return maxInputLength; }
    public int getRateLimitMaxPerMin() { return rateLimitMaxPerMin; }

    public boolean isRateLimitWarnEnabled() { return rateLimitWarnEnabled; }
    public String getRateLimitWarnMessage() { return rateLimitWarnMessage; }
    public boolean isRateLimitWarnActionBar() { return rateLimitWarnActionBar; }

    public boolean isRateLimitPunishmentEnabled() { return rateLimitPunishmentEnabled; }
    public List<String> getRateLimitPunishmentCommands() { return rateLimitPunishmentCommands; }

    public boolean isBlockCodeBlocks() { return blockCodeBlocks; }
    public List<String> getBlockSensitiveWords() { return Collections.unmodifiableList(blockSensitiveWords); }

    public String getInputBlacklistMessage() { return inputBlacklistMessage; }
    public boolean isInputBlacklistRegex() { return inputBlacklistRegex; }
    public List<String> getInputBlacklistWords() { return Collections.unmodifiableList(inputBlacklistWords); }

    public record ProviderConfig(String modelName, int maxTokens, double temperature, double presencePenalty, double frequencyPenalty) {}
    public record CustomProviderConfig(String apiUrl, String modelName, int maxTokens, double temperature) {}
}