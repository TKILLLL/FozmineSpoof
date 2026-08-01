package org.phantam.fozminesproofcore.chat;

import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.ChatConfig;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.manager.FakePlayerManager;
import org.phantam.fozminesproofcore.tasks.ChatTickerTask;
import org.phantam.fozminesproofapi.utils.DebugLogger;

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

    public ChatScheduler(FozmineSproofCore plugin, FakePlayerManager fakePlayerManager,
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

        int firstCycleSeconds = tickerTask.getTicksUntilNextChat() / 20;
        DebugLogger.log(logger, "ChatScheduler: first cycle in %d seconds", firstCycleSeconds);

        tickerTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (tickerTask != null) {
            DebugLogger.log(logger, "ChatScheduler: stopping existing ticker task");
            tickerTask.cancel();
            tickerTask = null;
        }
    }
}