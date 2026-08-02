package org.phantam.fozminesproofcore.chat;

import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.manager.FakePlayerManager;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.util.*;
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
                .map(data -> playerManager.getOnlineBotEntity(data.getName()))
                .filter(Objects::nonNull)
                .filter(Player::isValid)
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