package org.phantam.fozminespoofcore.chat;

import org.bukkit.scheduler.BukkitTask;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.ChatConfig;
import org.phantam.fozminespoofcore.config.ConfigManager;
import org.phantam.fozminespoofcore.manager.FakePlayerManager;
import org.phantam.fozminespoofcore.tasks.ChatTickerTask;

import java.util.logging.Logger;

/**
 * Manages the lifecycle of the automatic bot chat scheduler.
 * Optimized to use recursive exact-tick scheduling (0% idle CPU overhead).
 */
public class ChatScheduler {

    private final FozmineSpoofCore plugin;
    private final MessageLoader messageLoader;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;
    private final Logger logger;

    private ChatConfig chatConfig;
    private BukkitTask currentTask;

    public ChatScheduler(FozmineSpoofCore plugin, FakePlayerManager fakePlayerManager,
                         MessageLoader messageLoader, ConfigManager configManager) {
        this.plugin = plugin;
        this.messageLoader = messageLoader;
        this.logger = plugin.getLogger();

        this.botSelector = new BotSelector(fakePlayerManager, logger);
        this.chatProcessor = new BotChatProcessor(plugin, fakePlayerManager, configManager);
    }

    public void start(ChatConfig config) {
        this.chatConfig = config;
        this.stop();

        DebugLogger.log(logger, "ChatScheduler: start called, enabled=%s, mode=%s",
                config.isEnabled(), config.getMode());

        if (!chatConfig.isEnabled()) {
            logger.info("[ChatSystem] Chat system disabled, skipping random chat scheduler.");
            return;
        }

        scheduleNextCycle();
    }

    public void scheduleNextCycle() {
        if (chatConfig == null || !chatConfig.isEnabled()) return;

        long delayTicks = chatConfig.getRandomIntervalTicks();
        DebugLogger.log(logger, "ChatScheduler: Next chat cycle sleeping for %d ticks (%.2f seconds)", delayTicks, delayTicks / 20.0);

        ChatTickerTask task = new ChatTickerTask(plugin, chatConfig, botSelector, chatProcessor, messageLoader, this);
        this.currentTask = task.runTaskLater(plugin, delayTicks);
    }

    public void stop() {
        if (currentTask != null) {
            DebugLogger.log(logger, "ChatScheduler: stopping existing chat task");
            currentTask.cancel();
            currentTask = null;
        }
    }
}