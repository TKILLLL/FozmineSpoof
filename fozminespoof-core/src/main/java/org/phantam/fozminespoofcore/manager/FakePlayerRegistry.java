package org.phantam.fozminespoofcore.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
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
        Objects.requireNonNull(data, "data");
        String key = keyOf(data.getName());
        dataCache.put(key, data);
        if (entity != null) {
            entityCache.put(key, entity);
        } else {
            // A re-registration without an entity means the old entity is no longer valid.
            entityCache.remove(key);
        }
        DebugLogger.logFine(Bukkit.getLogger(), "FakePlayerRegistry: registered %s (uuid=%s)",
                data.getName(), data.getUuid());
    }

    /**
     * Unregisters a fake player (removes from online state).
     *
     * @param name the player name
     */
    public void unregister(String name) {
        if (name == null) return;
        String key = keyOf(name);
        FakePlayerData removedData = dataCache.remove(key);
        Player removedEntity = entityCache.remove(key);
        if (removedData != null) {
            DebugLogger.logFine(Bukkit.getLogger(), "FakePlayerRegistry: unregistered %s", name);
        } else {
            DebugLogger.logFine(Bukkit.getLogger(), "FakePlayerRegistry: unregister called for %s but not found", name);
        }
    }

    /**
     * Clears all registrations.
     */
    public void clearAll() {
        int dataSize = dataCache.size();
        int entitySize = entityCache.size();
        dataCache.clear();
        entityCache.clear();
        DebugLogger.log(Bukkit.getLogger(), "FakePlayerRegistry: cleared all (%d data, %d entities)", dataSize, entitySize);
    }

    /**
     * Returns the data for a given name, or null if not online.
     *
     * @param name the player name
     * @return the data, or null
     */
    public FakePlayerData getData(String name) {
        if (name == null) return null;
        FakePlayerData data = dataCache.get(keyOf(name));
        DebugLogger.logFine(Bukkit.getLogger(), "FakePlayerRegistry: getData %s -> %s",
                name, data != null ? "found" : "null");
        return data;
    }

    /**
     * Returns the Bukkit Player entity for a given name, or null if not online.
     *
     * @param name the player name
     * @return the Player, or null
     */
    public Player getEntity(String name) {
        if (name == null) return null;
        Player player = entityCache.get(keyOf(name));
        DebugLogger.logFine(Bukkit.getLogger(), "FakePlayerRegistry: getEntity %s -> %s",
                name, player != null ? "found" : "null");
        return player;
    }

    /**
     * Returns a collection of all online player names.
     *
     * @return collection of names
     */
    public Collection<String> getOnlineNames() {
        Collection<String> names = new ArrayList<>(dataCache.keySet());
        DebugLogger.logFine(Bukkit.getLogger(), "FakePlayerRegistry: getOnlineNames -> %d names", names.size());
        return names;
    }

    /**
     * Returns a collection of all online player data objects.
     *
     * @return collection of data
     */
    public Collection<FakePlayerData> getOnlineData() {
        Collection<FakePlayerData> data = new ArrayList<>(dataCache.values());
        DebugLogger.logFine(Bukkit.getLogger(), "FakePlayerRegistry: getOnlineData -> %d bots", data.size());
        return data;
    }

    /**
     * Checks if a player is currently online (registered).
     *
     * @param name the player name
     * @return true if online
     */
    public boolean isOnline(String name) {
        boolean online = name != null && dataCache.containsKey(keyOf(name));
        DebugLogger.logFine(Bukkit.getLogger(), "FakePlayerRegistry: isOnline %s -> %s", name, online);
        return online;
    }

    private static String keyOf(String name) {
        return Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
    }
}
