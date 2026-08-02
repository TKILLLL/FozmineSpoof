package org.phantam.fozminespoofcore.chat;

import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.ChatConfig;
import org.phantam.fozminespoofcore.config.ConfigManager;
import org.phantam.fozminespoofcore.manager.FakePlayerManager;
import org.phantam.fozminespoofcore.tasks.ChatTickerTask;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.util.logging.Logger;

/**
 * Manages the lifecycle of the automatic bot chat scheduler.
 */
public class ChatScheduler {

    private final JavaPlugin plugin;
    private final MessageLoader messageLoader;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;
    private final ConfigManager configManager;
    private final Logger logger;

    private ChatConfig chatConfig;
    private ChatTickerTask tickerTask;

    public ChatScheduler(FozmineSpoofCore plugin, FakePlayerManager fakePlayerManager,
                         MessageLoader messageLoader, ConfigManager configManager) {
        this.plugin = plugin;
        this.messageLoader = messageLoader;
        this.configManager = configManager;
        this.logger = plugin.getLogger();

        this.botSelector = new BotSelector(fakePlayerManager, logger);
        this.chatProcessor = new BotChatProcessor(plugin, fakePlayerManager, configManager);
    }

    public void start(ChatConfig config) {
        this.chatConfig = config;
        this.stop();

        DebugLogger.log(logger, "ChatScheduler: start called, enabled=%s", config.isEnabled());

        if (!chatConfig.isEnabled()) {
            logger.warning("[ChatSystem] Chat system is disabled in config.yml.");
            return;
        }

        this.tickerTask = new ChatTickerTask(plugin, chatConfig, botSelector, chatProcessor, messageLoader);

        long firstCycleTicks = tickerTask.getTicksUntilNextChat();
        DebugLogger.log(logger, "ChatScheduler: first cycle in %.2f seconds", firstCycleTicks / 20.0);

        tickerTask.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (tickerTask != null) {
            DebugLogger.log(logger, "ChatScheduler: stopping existing ticker task");
            tickerTask.cancel();
            tickerTask = null;
        }
    }
}