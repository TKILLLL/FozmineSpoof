package org.phantam.fozminesproofcore.chat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginLogger;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.manager.FakePlayerManager;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Selects random online bots eligible for chatting.
 * <p>
 * This class is responsible for filtering active fake players and returning a random subset
 * of them as Bukkit Player objects. It uses the registry maintained by FakePlayerManager.
 */
public class BotSelector {

    private final FakePlayerManager playerManager;
    private final Logger logger;

    /**
     * Constructs a BotSelector with the given manager and logger.
     *
     * @param playerManager the manager providing access to online bots
     * @param logger        the logger to use for warnings; if null, uses Bukkit's logger
     */
    public BotSelector(FakePlayerManager playerManager, Logger logger) {
        this.playerManager = playerManager;
        this.logger = (logger != null) ? logger : java.util.logging.Logger.getLogger("Minecraft");
    }

    /**
     * Selects up to {@code maxBotsToSelect} random bots from the currently online list.
     *
     * @param maxBotsToSelect maximum number of bots to return
     * @return a list of Player objects, possibly empty; never null
     */
    public List<Player> selectRandomBots(int maxBotsToSelect) {
        Collection<FakePlayerData> onlineData = playerManager.getOnlineBotsData();
        if (onlineData == null || onlineData.isEmpty()) {
            logger.warning("[BotSelector] No bots are currently online in the registry.");
            return Collections.emptyList();
        }

        List<Player> availableBots = onlineData.stream()
                .map(data -> playerManager.getOnlineBotEntity(data.getName()))
                .filter(Objects::nonNull)
                .filter(Player::isValid)
                .collect(Collectors.toList());

        if (availableBots.isEmpty()) {
            logger.warning("[BotSelector] No valid bot entities found.");
            return Collections.emptyList();
        }

        Collections.shuffle(availableBots);
        int limit = Math.min(maxBotsToSelect, availableBots.size());
        return availableBots.subList(0, limit);
    }
}