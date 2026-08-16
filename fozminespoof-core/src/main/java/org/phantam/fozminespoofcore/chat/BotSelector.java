package org.phantam.fozminespoofcore.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.manager.FakePlayerManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Selects random online bots eligible for chatting.
 */
public class BotSelector {

    private final FakePlayerManager playerManager;
    private final Logger logger;

    public BotSelector(FakePlayerManager playerManager, Logger logger) {
        this.playerManager = playerManager;
        this.logger = (logger != null) ? logger : java.util.logging.Logger.getLogger("Minecraft");
    }

    public List<Player> selectRandomBots(int maxBotsToSelect) {
        DebugLogger.log(logger, "BotSelector: selecting up to %d bots", maxBotsToSelect);

        Collection<FakePlayerData> onlineData = playerManager.getOnlineBotsData();
        if (onlineData == null || onlineData.isEmpty()) {
            DebugLogger.log(logger, "BotSelector: no online bots in registry");
            return Collections.emptyList();
        }

        List<Player> availableBots = onlineData.stream()
                .map(data -> {
                    Player bot = playerManager.getOnlineBotEntity(data.getName());
                    if (bot == null || !bot.isOnline()) {
                        bot = Bukkit.getPlayer(data.getUuid());
                    }
                    if (bot == null || !bot.isOnline()) {
                        bot = Bukkit.getPlayerExact(data.getName());
                    }
                    return bot;
                })
                .filter(Objects::nonNull)
                .filter(p -> p.isOnline() && playerManager.isBotOnline(p.getName()))
                .collect(Collectors.toList());

        DebugLogger.log(logger, "BotSelector: found %d valid bot entities out of %d online",
                availableBots.size(), onlineData.size());

        if (availableBots.isEmpty()) {
            logger.warning("[BotSelector] No valid bot entities found.");
            return Collections.emptyList();
        }

        Collections.shuffle(availableBots);
        int limit = Math.min(maxBotsToSelect, availableBots.size());
        List<Player> selected = availableBots.subList(0, limit);

        DebugLogger.log(logger, "BotSelector: selected %d bots: %s",
                selected.size(),
                selected.stream().map(Player::getName).collect(Collectors.joining(", ")));

        return selected;
    }
}