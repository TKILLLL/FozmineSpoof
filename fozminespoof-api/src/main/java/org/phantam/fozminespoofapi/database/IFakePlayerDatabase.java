package org.phantam.fozminespoofapi.database;

import org.phantam.fozminespoofapi.model.FakePlayerData;

import java.util.Collection;
import java.util.Optional;

/**
 * Data access layer for storing and retrieving fake player data.
 * <p>
 * Implementations are responsible for handling connection details and ensuring
 * thread-safety when accessing the underlying storage.
 * </p>
 *
 * @author Phantam
 * @version 2.0.0
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
     * @throws RuntimeException         if a database error occurs
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
     * Loads all inactive fake players (where active flag is false).
     *
     * @return a collection of inactive fake player data; never null
     */
    Collection<FakePlayerData> loadInactivePlayers();

    /**
     * Sends a synchronization update to a proxy server (e.g., BungeeCord).
     * <p>
     * This method updates or inserts a record in the proxy sync table, allowing
     * the proxy to know the current bot counts for this specific server node.
     * </p>
     *
     * @param bungeeName      the name of the BungeeCord channel or service (used as table prefix)
     * @param serverNodeName  the unique identifier of this server node (e.g., "survival-01")
     * @param activeCount     current number of active bots on this node
     * @param inactiveCount   current number of inactive bots on this node
     */
    void sendProxySyncData(String bungeeName, String serverNodeName, int activeCount, int inactiveCount);

    /**
     * Saves multiple fake player records in a batch operation.
     *
     * @param players the collection of fake player data to save
     */
    void saveFakePlayers(Collection<FakePlayerData> players);
}