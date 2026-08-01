package org.phantam.fozminesproofapi.database;

import org.phantam.fozminesproofapi.model.FakePlayerData;

import java.util.Collection;
import java.util.Optional;

/**
 * Data access layer for storing and retrieving fake player data.
 * <p>
 * Implementations are responsible for handling connection details and ensuring
 * thread-safety when accessing the underlying storage.
 */
public interface IFakePlayerDatabase {

    /**
     * Initialises the database connection and creates necessary tables.
     *
     * @throws IllegalStateException if the database cannot be initialised
     */
    void setup();

    /**
     * Closes the database connection and releases resources.
     * Should be called during plugin shutdown.
     */
    void close();

    /**
     * Persists or updates a fake player record in the database.
     * <p>
     * If a record with the same name/UUID exists, it will be overwritten.
     *
     * @param data the fake player data to save
     * @throws IllegalArgumentException if data is null
     * @throws RuntimeException if a database error occurs
     */
    void saveFakePlayer(FakePlayerData data);

    /**
     * Retrieves a fake player record by its name.
     *
     * @param name the player's name (case-sensitive)
     * @return an Optional containing the data if found, or empty otherwise
     * @throws IllegalArgumentException if name is null or empty
     */
    Optional<FakePlayerData> loadFakePlayer(String name);

    /**
     * Loads all fake player records currently stored in the database.
     *
     * @return a collection of all fake player data; never null
     */
    Collection<FakePlayerData> loadAllPlayers();

    /**
     * Deletes a fake player record by name.
     *
     * @param name the player's name to delete
     * @throws IllegalArgumentException if name is null or empty
     */
    void deleteFakePlayer(String name);

    /**
     * Returns the count of active fake players (where active flag is true).
     *
     * @return active bot count
     */
    int getActiveBotCount();

    /**
     * Returns the count of inactive fake players (where active flag is false).
     *
     * @return inactive bot count
     */
    int getInactiveBotCount();

    /**
     * Sends a synchronization update to a proxy server (e.g., BungeeCord).
     * <p>
     * This is used to keep the proxy informed about the current state of fake players.
     *
     * @param bungeeName  the name of the BungeeCord channel or service
     * @param name        the name of the target bot (optional)
     * @param activeCount current number of active bots
     * @param inactiveCount current number of inactive bots
     */
    void sendProxySyncData(String bungeeName, String name, int activeCount, int inactiveCount);
}