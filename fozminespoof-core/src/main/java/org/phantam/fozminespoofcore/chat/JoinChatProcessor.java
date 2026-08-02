package org.phantam.fozminespoofcore.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.ChatConfig;
import org.phantam.fozminespoofcore.config.JoinMessageConfig;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class JoinChatProcessor {

    private final FozmineSpoofCore plugin;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;

    public JoinChatProcessor(FozmineSpoofCore plugin, BotSelector botSelector, BotChatProcessor chatProcessor) {
        this.plugin = plugin;
        this.botSelector = botSelector;
        this.chatProcessor = chatProcessor;
    }

    /**
     * Xử lý khi Người chơi thật kết nối vào server (Gửi lời chào từ các bot)
     */
    public void handleRealPlayerJoin(Player realPlayer) {
        if (realPlayer == null || !plugin.isEnabled()) return;

        JoinMessageConfig config = plugin.getJoinMessageConfig();
        if (config == null) return;

        boolean isNew = !realPlayer.hasPlayedBefore();
        boolean enabled = isNew ? config.isNewPlayerGreetingsEnabled() : config.isPlayerGreetingsEnabled();
        if (!enabled) return;

        int maxBurst = isNew ? config.getNewPlayerGreetingsMaxBurst() : config.getPlayerGreetingsMaxBurst();
        List<String> phrases = isNew ? config.getNewPlayerGreetingsPhrases() : config.getPlayerGreetingsPhrases();

        if (phrases == null || phrases.isEmpty() || maxBurst <= 0) return;

        List<Player> speakingBots = botSelector.selectRandomBots(maxBurst);
        if (speakingBots.isEmpty()) return;

        long delayTicks = 20L;
        for (Player bot : speakingBots) {
            String randomPhrase = phrases.get(ThreadLocalRandom.current().nextInt(phrases.size()));
            String formattedMessage = randomPhrase.replace("[name]", realPlayer.getName()).replace("%name%", realPlayer.getName());

            long currentDelay = delayTicks;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (realPlayer.isOnline() && bot.isOnline()) {
                    chatProcessor.processChatAsync(bot, formattedMessage, plugin.getConfigManager().getChatConfig());
                }
            }, currentDelay);

            // Giãn cách ngẫu nhiên 1.0s - 2.5s giữa các bot gửi tin chào
            delayTicks += ThreadLocalRandom.current().nextLong(20L, 50L);
        }
    }

    /**
     * Xử lý khi Bot vừa kết nối/spawn vào server (Tự động chào xã giao)
     */
    public void handleBotSessionJoin(Player bot) {
        if (bot == null || !plugin.isEnabled()) return;

        int totalOnline = Bukkit.getOnlinePlayers().size();
        int botOnline = plugin.getFakePlayerManager().getOnlineBotsData().size();
        int realPlayers = Math.max(0, totalOnline - botOnline);

        ChatConfig chatConfig = plugin.getConfigManager().getChatConfig();
        if (chatConfig != null && realPlayers < chatConfig.getMinRealPlayers()) {
            return;
        }

        JoinMessageConfig config = plugin.getJoinMessageConfig();
        if (config == null || !config.isSessionJoinChatsEnabled()) return;

        List<String> phrases = config.getSessionJoinChatsPhrases();
        if (phrases == null || phrases.isEmpty()) return;

        long delayTicks = ThreadLocalRandom.current().nextLong(30L, 70L);
        String randomPhrase = phrases.get(ThreadLocalRandom.current().nextInt(phrases.size()));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (bot.isOnline() && plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
                chatProcessor.processChatAsync(bot, randomPhrase, plugin.getConfigManager().getChatConfig());
            }
        }, delayTicks);
    }
}