package org.phantam.fozminespoofcore.database.executors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.manager.FakePlayerRegistry;

import java.util.Optional;
import java.util.logging.Level;

/**
 * Reloads bot data from the database and updates the registry without respawning.
 * Features Auto-Healing for missing database entries.
 */
public class ReloadSystemAction implements org.phantam.fozminespoofapi.action.IBotAction<Void, Void> {

    private final FozmineSpoofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;

    public ReloadSystemAction(FozmineSpoofCore plugin, IFakePlayerDatabase database,
                              FakePlayerRegistry registry) {
        this.plugin = plugin;
        this.database = database;
        this.registry = registry;
    }

    @Override
    public Void execute(Void unused) {
        DebugLogger.log(plugin.getLogger(), "ReloadSystemAction: starting system reload");

        int refreshed = 0;
        for (String botName : registry.getOnlineNames()) {
            if (botName == null) continue;

            FakePlayerData cachedData = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .filter(b -> b != null && b.getName() != null && b.getName().equalsIgnoreCase(botName))
                    .findFirst()
                    .orElse(null);

            if (cachedData == null) {
                Optional<FakePlayerData> opt = database.loadFakePlayer(botName);
                if (opt.isPresent()) {
                    cachedData = opt.get();
                }
            }

            if (cachedData == null) {
                cachedData = registry.getData(botName);
                if (cachedData != null) {
                    final FakePlayerData dataToSave = cachedData.withActive(true);
                    plugin.getFakePlayerManager().updateCache(dataToSave);
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.saveFakePlayer(dataToSave));
                    DebugLogger.log(plugin.getLogger(), "ReloadSystemAction: auto-healed missing DB record for %s", botName);
                }
            }

            if (cachedData != null) {
                Player entity = registry.getEntity(botName);
                if (entity != null) {
                    registry.register(cachedData, entity);
                    plugin.getFakePlayerManager().updateCache(cachedData);
                    refreshed++;
                }
            } else {
                plugin.getLogger().log(Level.WARNING,
                        "[ReloadSystemAction] Bot '" + botName + "' could not be resolved during reload");
            }
        }

        DebugLogger.log(plugin.getLogger(), "ReloadSystemAction: reload complete, %d bots refreshed", refreshed);
        return null;
    }
}