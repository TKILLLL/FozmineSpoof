package org.phantam.fozminespoofapi;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Core bridge interface between the plugin core and version-specific NMS implementations.
 * <p>
 * This interface defines the contract for creating, managing, and controlling fake players
 * (NPCs) across different Minecraft versions. Each version module must implement this
 * interface to provide version-appropriate NMS operations.
 */
public interface FozminespoofApi {

    /**
     * Spawns a fake player into the server world.
     * <p>
     * The spawned player will appear as a normal player to others, with a configurable
     * name, UUID, and spawn location. The implementation must handle skin retrieval
     * and packet broadcasting automatically.
     *
     * @param name    the display name of the fake player
     * @param uuid    the UUID to assign (must be unique)
     * @param loc     the spawn location (world + coordinates)
     * @param hideTab if true, the fake player will not appear in the tab list
     * @return the Bukkit Player representation of the spawned fake player
     * @throws IllegalArgumentException if any parameter is null or invalid
     * @throws IllegalStateException   if spawning fails due to internal error
     */
    Player spawnPlayer(String name, UUID uuid, Location loc, boolean hideTab);

    /**
     * Despawns and removes a fake player from the world and server's player list.
     * <p>
     * After this call, the player entity is discarded and all associated packets are
     * cleaned up. Any subsequent references to the player's Bukkit entity may be invalid.
     *
     * @param uuid the UUID of the fake player to remove
     */
    void despawnPlayer(UUID uuid);

    /**
     * Updates the skin texture of an existing fake player.
     * <p>
     * This method may trigger a complete respawn of the player entity to apply the new skin.
     *
     * @param uuid      the UUID of the target fake player
     * @param texture   the base64 encoded texture string from Mojang API
     * @param signature the signature associated with the texture (can be empty/null if not provided)
     * @param hideTab   if true, the fake player will not appear in the tab list
     */
    void updatePlayerSkin(UUID uuid, String texture, String signature, boolean hideTab);

    /**
     * Returns the number of currently active (spawned) fake players.
     *
     * @return active fake player count
     */
    int getFakePlayersCount();

    /**
     * Forces a resend of all spawn and tablist packets for active fake players.
     * <p>
     * This is typically called periodically to prevent client-side desyncs
     * when players log in/out or change worlds.
     */
    void sendKeepAlivePackets();

    /**
     * Broadcasts a chat message as if it originated from the given player,
     * using NMS-level chat packets.
     * <p>
     * This allows the fake player to appear in chat logs and triggers other plugins'
     * chat formatting (e.g., LPC, EssentialsX) if the message is properly constructed.
     *
     * @param player  the fake player who is "speaking"
     * @param message the chat message to broadcast (may include color codes)
     */
    void broadcastNMSChat(Player player, String message);
}