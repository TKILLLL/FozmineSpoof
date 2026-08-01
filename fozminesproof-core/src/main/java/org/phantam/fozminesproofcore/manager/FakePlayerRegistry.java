package org.phantam.fozminesproofcore.manager;

import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for online fake players.
 * Maintains mappings from lowercase name to both the data object and the Bukkit Player entity.
 */
public class FakePlayerRegistry {

    private final Map<String, FakePlayerData> dataCache = new ConcurrentHashMap<>();
    private final Map<String, Player> entityCache = new ConcurrentHashMap<>();

    /**
     * Registers a fake player as online.
     *
     * @param data   the player data
     * @param entity the Bukkit Player entity
     */
    public void register(FakePlayerData data, Player entity) {
        String key = data.getName().toLowerCase();
        dataCache.put(key, data);
        if (entity != null) {
            entityCache.put(key, entity);
        }
    }

    /**
     * Unregisters a fake player (removes from online state).
     *
     * @param name the player name
     */
    public void unregister(String name) {
        String key = name.toLowerCase();
        dataCache.remove(key);
        entityCache.remove(key);
    }

    /**
     * Clears all registrations.
     */
    public void clearAll() {
        dataCache.clear();
        entityCache.clear();
    }

    /**
     * Returns the data for a given name, or null if not online.
     *
     * @param name the player name
     * @return the data, or null
     */
    public FakePlayerData getData(String name) {
        return dataCache.get(name.toLowerCase());
    }

    /**
     * Returns the Bukkit Player entity for a given name, or null if not online.
     *
     * @param name the player name
     * @return the Player, or null
     */
    public Player getEntity(String name) {
        return entityCache.get(name.toLowerCase());
    }

    /**
     * Returns a collection of all online player names.
     *
     * @return collection of names
     */
    public Collection<String> getOnlineNames() {
        return dataCache.keySet();
    }

    /**
     * Returns a collection of all online player data objects.
     *
     * @return collection of data
     */
    public Collection<FakePlayerData> getOnlineData() {
        return dataCache.values();
    }

    /**
     * Checks if a player is currently online (registered).
     *
     * @param name the player name
     * @return true if online
     */
    public boolean isOnline(String name) {
        return dataCache.containsKey(name.toLowerCase());
    }
}