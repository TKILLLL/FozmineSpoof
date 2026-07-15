package org.phantam.fozminesproofcore.chat;

import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.database.FakePlayerManager;
import java.util.*;

public class BotSelector {
    private final FakePlayerManager fakePlayerManager;

    public BotSelector(FakePlayerManager fakePlayerManager) {
        this.fakePlayerManager = fakePlayerManager;
    }

    /**
     * Lấy ra danh sách các Bot ngẫu nhiên đủ điều kiện để nói chuyện
     */
    public List<Player> selectRandomBots(int maxBotsToSelect) {
        Collection<FakePlayerData> onlineData = fakePlayerManager.getOnlineBotsData();
        List<Player> availableBots = new ArrayList<>();

        for (FakePlayerData data : onlineData) {
            Player botPlayer = fakePlayerManager.getOnlineBotEntity(data.getName());
            if (botPlayer != null && botPlayer.isOnline()) {
                availableBots.add(botPlayer);
            }
        }

        if (availableBots.isEmpty()) {
            return Collections.emptyList();
        }

        Collections.shuffle(availableBots);
        int limit = Math.min(maxBotsToSelect, availableBots.size());
        return availableBots.subList(0, limit);
    }
}
