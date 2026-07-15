package org.phantam.fozminesproofcore.database;

import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakePlayerRegistry {
    private final Map<String, FakePlayerData> onlineDataCache = new ConcurrentHashMap<>();
    private final Map<String, Player> onlineEntityCache = new ConcurrentHashMap<>();

    public void register(FakePlayerData data, Player entity) {
        String key = data.getName().toLowerCase();
        this.onlineDataCache.put(key, data);
        if (entity != null) {
            this.onlineEntityCache.put(key, entity);
        }
    }

    public void unregister(String name) {
        String key = name.toLowerCase();
        this.onlineDataCache.remove(key);
        this.onlineEntityCache.remove(key);
    }

    public void clearAll() {
        this.onlineDataCache.clear();
        this.onlineEntityCache.clear();
    }

    public FakePlayerData getData(String name) {
        return this.onlineDataCache.get(name.toLowerCase());
    }

    public Player getEntity(String name) {
        return this.onlineEntityCache.get(name.toLowerCase());
    }

    public Collection<String> getOnlineNames() {
        return this.onlineDataCache.keySet();
    }

    public Collection<FakePlayerData> getOnlineData() {
        return this.onlineDataCache.values();
    }

    public boolean isOnline(String name) {
        return this.onlineDataCache.containsKey(name.toLowerCase());
    }
}
