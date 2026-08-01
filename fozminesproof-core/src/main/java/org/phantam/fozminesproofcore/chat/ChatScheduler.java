package org.phantam.fozminesproofcore.chat;

import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.ChatConfig;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.manager.FakePlayerManager;
import org.phantam.fozminesproofcore.tasks.ChatTickerTask;

import java.util.logging.Logger;

/**
 * Manages the lifecycle of the automatic bot chat scheduler.
 * <p>
 * Starts and stops the periodic chat ticker based on configuration.
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

    /**
     * Starts the chat scheduler with the given configuration.
     * Cancels any existing scheduler before starting a new one.
     *
     * @param config the chat configuration to use
     */
    public void start(ChatConfig config) {
        this.chatConfig = config;
        this.stop();

        if (!chatConfig.isEnabled()) {
            logger.warning("[ChatSystem] Chat system is disabled in config.yml.");
            return;
        }

        this.tickerTask = new ChatTickerTask(plugin, chatConfig, botSelector, chatProcessor, messageLoader);

        logger.info("[ChatSystem] Chat system activated! First cycle in "
                + (tickerTask.getTicksUntilNextChat() / 20) + " seconds.");

        tickerTask.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Stops the current chat scheduler safely.
     */
    public void stop() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }
}