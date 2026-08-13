package org.phantam.fozminespoofcore.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.AiConfig;

/**
 * Manages the creation and removal of the AI helper bot (e.g., @FozmineBot).
 * The bot is spawned/despawned based on ai-help.enabled configuration.
 */
public class AiHelperBotManager {

    private final FozmineSpoofCore plugin;
    private final AiConfig aiConfig;
    private String helperBotName;
    private boolean isSpawned;

    public AiHelperBotManager(FozmineSpoofCore plugin, AiConfig aiConfig) {
        this.plugin = plugin;
        this.aiConfig = aiConfig;
        this.helperBotName = aiConfig.getAiHelpBotName();
        this.isSpawned = false;
    }

    /**
     * Checks the configuration and spawns or removes the helper bot accordingly.
     * Should be called on reload and startup.
     */
    public void updateHelperBot() {
        boolean shouldBeEnabled = aiConfig.isAiHelpEnabled() && aiConfig.isEnabled();
        String currentName = aiConfig.getAiHelpBotName();

        // If the bot name changed, remove old one first
        if (!currentName.equals(helperBotName) && isSpawned) {
            removeHelperBot();
        }
        helperBotName = currentName;

        if (shouldBeEnabled && !isSpawned) {
            spawnHelperBot();
        } else if (!shouldBeEnabled && isSpawned) {
            removeHelperBot();
        }
    }

    private void spawnHelperBot() {
        if (helperBotName == null || helperBotName.isEmpty()) {
            DebugLogger.log(plugin.getLogger(), "AiHelperBotManager: helper bot name is empty, cannot spawn.");
            return;
        }

        // Check if bot already exists in database
        var existing = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .filter(d -> d.getName().equalsIgnoreCase(helperBotName))
                .findFirst();

        if (existing.isPresent() && plugin.getFakePlayerManager().isBotOnline(existing.get().getName())) {
            // Already online
            isSpawned = true;
            DebugLogger.log(plugin.getLogger(), "AiHelperBotManager: helper bot %s is already online.", helperBotName);
            return;
        }

        // If not in database, add it
        if (existing.isEmpty()) {
            Location loc = getSpawnLocation();
            if (loc == null) {
                DebugLogger.log(plugin.getLogger(), "AiHelperBotManager: cannot determine spawn location.");
                return;
            }
            // Add bot to database (inactive)
            plugin.getFakePlayerManager().addBot(helperBotName, loc);
            DebugLogger.log(plugin.getLogger(), "AiHelperBotManager: added helper bot %s to database.", helperBotName);
        }

        // Spawn the bot
        plugin.getFakePlayerManager().spawnBotAsync(helperBotName, success -> {
            if (success) {
                isSpawned = true;
                DebugLogger.log(plugin.getLogger(), "AiHelperBotManager: helper bot %s spawned successfully.", helperBotName);
            } else {
                DebugLogger.log(plugin.getLogger(), "AiHelperBotManager: failed to spawn helper bot %s.", helperBotName);
            }
        });
    }

    private void removeHelperBot() {
        if (helperBotName == null || helperBotName.isEmpty()) return;

        // Despawn and remove from database
        if (plugin.getFakePlayerManager().isBotOnline(helperBotName)) {
            plugin.getFakePlayerManager().despawnBot(helperBotName);
            DebugLogger.log(plugin.getLogger(), "AiHelperBotManager: despawned helper bot %s.", helperBotName);
        }
        // Remove from database (permanent)
        plugin.getFakePlayerManager().removeBot(helperBotName);
        DebugLogger.log(plugin.getLogger(), "AiHelperBotManager: removed helper bot %s from database.", helperBotName);
        isSpawned = false;
    }

    private Location getSpawnLocation() {
        String worldName = plugin.getConfigManager().getBotWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return world != null ? world.getSpawnLocation() : null;
    }

    public boolean isHelperBotSpawned() {
        return isSpawned;
    }

    public String getHelperBotName() {
        return helperBotName;
    }
}