package org.phantam.fozminespoofcore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.ChatConfig;

/**
 * Listener for bot join and quit events, handling message suppression and custom broadcasts.
 * <p>
 * This listener controls whether default join/quit messages are suppressed based on the
 * {@code join-leave-message-enable} and {@code join-leave-format} configuration.
 * Additionally, it triggers join chat messages only when chat system is enabled and mode is "normal".
 * </p>
 */
public class BotJoinQuitListener implements Listener {

    private final FozmineSpoofCore plugin;

    public BotJoinQuitListener(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        if (isBot(player)) {
            boolean enable = plugin.getConfigManager().isJoinLeaveMessageEnable();
            String format = plugin.getConfigManager().getJoinLeaveFormat();

            if (!enable || "custom".equalsIgnoreCase(format)) {
                event.setJoinMessage(null);
            }

            if (plugin.getJoinChatProcessor() != null && isChatNormalMode()) {
                plugin.getJoinChatProcessor().handleBotSessionJoin(player);
            }
        } else {
            boolean isBotName = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .anyMatch(b -> b != null && b.getName() != null && b.getName().equalsIgnoreCase(name));

            if (isBotName && plugin.getConfigManager().isRankWeightEnabled() && plugin.getRankWeightManager() != null) {
                plugin.getRankWeightManager().resetRank(name);
            }

            if (plugin.getJoinChatProcessor() != null && isChatNormalMode()) {
                plugin.getJoinChatProcessor().handleRealPlayerJoin(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBotQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isBot(player)) {
            boolean enable = plugin.getConfigManager().isJoinLeaveMessageEnable();
            String format = plugin.getConfigManager().getJoinLeaveFormat();

            if (!enable || "custom".equalsIgnoreCase(format)) {
                event.setQuitMessage(null);
            }

            plugin.getFakePlayerManager().handleExternalQuit(player.getName());
        }
    }

    /**
     * Determines if a player is a bot by checking metadata or registry.
     *
     * @param player the player to check
     * @return {@code true} if the player is a fake player, {@code false} otherwise
     */
    private boolean isBot(Player player) {
        return player.hasMetadata("NPC") || plugin.getFakePlayerManager().isBotOnline(player.getName());
    }

    /**
     * Checks if the chat system is enabled and in "normal" mode.
     *
     * @return {@code true} if normal mode, {@code false} otherwise
     */
    private boolean isChatNormalMode() {
        ChatConfig chatConfig = plugin.getConfigManager().getChatConfig();
        return chatConfig != null && chatConfig.isEnabled() && !"ai".equalsIgnoreCase(chatConfig.getMode());
    }
}