package org.phantam.fozminespoofcore.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.ChatConfig;
import org.phantam.fozminespoofcore.config.JoinMessageConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class JoinChatProcessor {

    private final FozmineSpoofCore plugin;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;

    private final Queue<UUID> pendingSessionBots = new ConcurrentLinkedQueue<>();
    private boolean isSessionFlushTaskScheduled = false;

    private long globalSessionChatCooldown = 0L;

    private final Map<UUID, Long> playerGreetingCooldowns = new ConcurrentHashMap<>();

    private final List<String> recentPhrasesCache = Collections.synchronizedList(new LinkedList<>());
    private static final int MAX_CACHE_SIZE = 10;

    public JoinChatProcessor(FozmineSpoofCore plugin, BotSelector botSelector, BotChatProcessor chatProcessor) {
        this.plugin = plugin;
        this.botSelector = botSelector;
        this.chatProcessor = chatProcessor;
    }

    /**
     * Xử lý khi Real Player kết nối vào Server.
     */
    public void handleRealPlayerJoin(Player realPlayer) {
        if (realPlayer == null || !plugin.isEnabled()) return;
        var chatConfig = plugin.getConfigManager().getChatConfig();
        if (chatConfig == null || !chatConfig.isEnabled() || "ai".equalsIgnoreCase(chatConfig.getMode())) {
            return;
        }

        if (getRealPlayerCount() < chatConfig.getMinRealPlayers()) {
            return;
        }

        JoinMessageConfig joinConfig = plugin.getJoinMessageConfig();
        if (joinConfig == null) return;

        boolean isNewPlayer = !realPlayer.hasPlayedBefore();
        boolean enabled = isNewPlayer
                ? joinConfig.isNewPlayerGreetingsEnabled()
                : joinConfig.isPlayerGreetingsEnabled();

        if (enabled) {
            if (!isPlayerOnCooldown(realPlayer.getUniqueId())) {
                processPlayerGreetings(realPlayer, isNewPlayer, joinConfig, chatConfig);
                double delaySeconds = isNewPlayer
                        ? joinConfig.getNewPlayerGreetingsDelay()
                        : joinConfig.getPlayerGreetingsDelay();
                setPlayerCooldown(realPlayer.getUniqueId(), delaySeconds);
            } else {
                DebugLogger.log(plugin.getLogger(), "JoinChatProcessor: Skip greeting for %s (Cooldown active)", realPlayer.getName());
            }
        }
    }

    /**
     * Xử lý khi Bot vừa Spawn/Session Join vào Server từ Lifecycle.
     */
    public void handleBotSessionJoin(Player bot) {
        if (bot == null || !plugin.isEnabled()) return;
        var chatConfig = plugin.getConfigManager().getChatConfig();
        if (chatConfig == null || !chatConfig.isEnabled() || "ai".equalsIgnoreCase(chatConfig.getMode())) {
            return;
        }

        if (getRealPlayerCount() < chatConfig.getMinRealPlayers()) {
            return;
        }

        if (System.currentTimeMillis() < globalSessionChatCooldown) {
            return;
        }

        JoinMessageConfig joinConfig = plugin.getJoinMessageConfig();
        if (joinConfig == null || !joinConfig.isSessionJoinChatsEnabled()) return;

        pendingSessionBots.add(bot.getUniqueId());
        tryScheduleSessionFlush();
    }

    public void handleBotQuit(Player bot) {
        if (bot != null) {
            pendingSessionBots.remove(bot.getUniqueId());
        }
    }

    private void processPlayerGreetings(Player realPlayer, boolean isNewPlayer, JoinMessageConfig joinConfig, ChatConfig chatConfig) {
        int maxBurst = isNewPlayer ? joinConfig.getNewPlayerGreetingsMaxBurst() : joinConfig.getPlayerGreetingsMaxBurst();
        List<String> rawPhrases = isNewPlayer ? joinConfig.getNewPlayerGreetingsPhrases() : joinConfig.getPlayerGreetingsPhrases();

        if (rawPhrases == null || rawPhrases.isEmpty() || maxBurst <= 0) return;

        List<Player> speakingBots = botSelector.selectRandomBots(maxBurst);
        if (speakingBots.isEmpty()) return;

        String realPlayerName = realPlayer.getName();
        UUID realPlayerUuid = realPlayer.getUniqueId();
        long accumDelayTicks = 20L;

        List<String> uniquePhrases = getUniquePhrasesPool(rawPhrases, speakingBots.size());

        for (int i = 0; i < speakingBots.size(); i++) {
            Player bot = speakingBots.get(i);
            String chosenPhrase = uniquePhrases.get(i);

            String formattedMessage = chosenPhrase.replace("[name]", realPlayerName).replace("%name%", realPlayerName);
            markPhraseAsUsed(chosenPhrase);

            final long scheduledDelay = accumDelayTicks;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player target = Bukkit.getPlayer(realPlayerUuid);
                if (target == null || !target.isOnline()) return;
                if (bot == null || !bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) return;

                chatProcessor.processChatAsync(bot, formattedMessage, chatConfig);
            }, scheduledDelay);

            accumDelayTicks += ThreadLocalRandom.current().nextLong(20L, 50L);
        }
    }

    private void tryScheduleSessionFlush() {
        if (isSessionFlushTaskScheduled) return;
        if (pendingSessionBots.isEmpty()) return;

        isSessionFlushTaskScheduled = true;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            isSessionFlushTaskScheduled = false;
            executeSessionFlush();
        }, 60L);
    }

    private void executeSessionFlush() {
        if (pendingSessionBots.isEmpty()) return;

        ChatConfig chatConfig = plugin.getConfigManager().getChatConfig();
        JoinMessageConfig joinConfig = plugin.getJoinMessageConfig();

        if (getRealPlayerCount() < chatConfig.getMinRealPlayers()) {
            pendingSessionBots.clear();
            return;
        }
        if (System.currentTimeMillis() < globalSessionChatCooldown) {
            pendingSessionBots.clear();
            return;
        }

        List<String> rawPhrases = joinConfig.getSessionJoinChatsPhrases();
        if (rawPhrases == null || rawPhrases.isEmpty()) {
            pendingSessionBots.clear();
            return;
        }

        int maxBurst = joinConfig.getSessionJoinChatsMaxBurst();
        List<UUID> toProcess = new ArrayList<>();

        UUID botUuid;
        while ((botUuid = pendingSessionBots.poll()) != null && toProcess.size() < maxBurst) {
            toProcess.add(botUuid);
        }

        pendingSessionBots.clear();

        if (toProcess.isEmpty()) return;

        double delaySecs = joinConfig.getSessionJoinChatsDelay();
        globalSessionChatCooldown = System.currentTimeMillis() + (long) (delaySecs * 1000L);

        long accumDelayTicks = 10L;
        for (UUID uuid : toProcess) {
            Player bot = Bukkit.getPlayer(uuid);
            if (bot != null && bot.isOnline() && plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
                dispatchBotSessionChat(bot, rawPhrases, chatConfig, accumDelayTicks);
                accumDelayTicks += ThreadLocalRandom.current().nextLong(30L, 60L);
            }
        }
    }

    private void dispatchBotSessionChat(Player bot, List<String> rawPhrases, ChatConfig chatConfig, long delayTicks) {
        String chosenPhrase = selectNonRepeatingPhrase(rawPhrases);
        markPhraseAsUsed(chosenPhrase);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (bot == null || !bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) return;
            chatProcessor.processChatAsync(bot, chosenPhrase, chatConfig);
        }, delayTicks);
    }

    private boolean isPlayerOnCooldown(UUID uuid) {
        Long expireTime = playerGreetingCooldowns.get(uuid);
        if (expireTime == null) return false;
        if (System.currentTimeMillis() < expireTime) return true;
        playerGreetingCooldowns.remove(uuid);
        return false;
    }

    private void setPlayerCooldown(UUID uuid, double delaySeconds) {
        playerGreetingCooldowns.put(uuid, System.currentTimeMillis() + (long) (delaySeconds * 1000.0));
    }

    private List<String> getUniquePhrasesPool(List<String> rawPhrases, int requiredCount) {
        List<String> pool = new ArrayList<>();
        List<String> available = new ArrayList<>(rawPhrases);
        available.removeIf(recentPhrasesCache::contains);
        if (available.isEmpty()) available = new ArrayList<>(rawPhrases);
        Collections.shuffle(available);

        for (int i = 0; i < requiredCount; i++) {
            if (available.isEmpty()) {
                available = new ArrayList<>(rawPhrases);
                Collections.shuffle(available);
            }
            pool.add(available.remove(0));
        }
        return pool;
    }

    private String selectNonRepeatingPhrase(List<String> rawPhrases) {
        List<String> available = new ArrayList<>(rawPhrases);
        available.removeIf(recentPhrasesCache::contains);
        if (available.isEmpty()) available = rawPhrases;
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    private void markPhraseAsUsed(String phrase) {
        synchronized (recentPhrasesCache) {
            recentPhrasesCache.add(phrase);
            if (recentPhrasesCache.size() > MAX_CACHE_SIZE) {
                recentPhrasesCache.remove(0);
            }
        }
    }

    private int getRealPlayerCount() {
        int totalOnline = Bukkit.getOnlinePlayers().size();
        int botOnline = plugin.getFakePlayerManager().getOnlineBotsData().size();
        return Math.max(0, totalOnline - botOnline);
    }
}