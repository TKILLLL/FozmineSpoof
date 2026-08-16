package org.phantam.fozminespoofapi;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Core bridge interface between the plugin core and version-specific NMS implementations.
 * <p>
 * This interface defines the contract for creating, managing, and controlling fake players
 * (NPCs) across different Minecraft versions.
 */
public interface FozminespoofApi {

    /**
     * Spawns a fake player into the server world.
     *
     * @param name    the display name of the fake player
     * @param uuid    the UUID to assign (must be unique)
     * @param loc     the spawn location (world + coordinates)
     * @param hideTab if true, the fake player will not appear in the tab list
     * @return the Bukkit Player representation of the spawned fake player
     */
    Player spawnPlayer(String name, UUID uuid, Location loc, boolean hideTab);

    /**
     * Despawns and removes a fake player from the world and server's player list.
     *
     * @param uuid the UUID of the fake player to remove
     */
    void despawnPlayer(UUID uuid);

    /**
     * Updates the skin texture of an existing fake player.
     *
     * @param uuid      the UUID of the target fake player
     * @param texture   the base64 encoded texture string from Mojang API
     * @param signature the signature associated with the texture
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
     * Checks if a specific UUID belongs to an active fake player.
     *
     * @param uuid the UUID to check
     * @return true if the UUID belongs to an active bot
     */
    boolean isFakePlayer(UUID uuid);

    /**
     * Checks if a Player entity is a fake player managed by this plugin.
     *
     * @param player the Player entity to check
     * @return true if the player is a fake player
     */
    default boolean isFakePlayer(Player player) {
        return player != null && (player.hasMetadata("NPC") || isFakePlayer(player.getUniqueId()));
    }

    /**
     * Forces a resend of all spawn and tablist packets for active fake players.
     */
    void sendKeepAlivePackets();

    /**
     * Broadcasts a chat message as if it originated from the given player, using NMS packets.
     *
     * @param player  the fake player who is speaking
     * @param message the chat message to broadcast
     */
    void broadcastNMSChat(Player player, String message);

    /**
     * Broadcasts latency update packets to refresh a fake player's ping bar on clients.
     *
     * @param uuid    the UUID of the fake player
     * @param latency the new ping value in milliseconds
     */
    void updatePlayerLatency(UUID uuid, int latency);
}