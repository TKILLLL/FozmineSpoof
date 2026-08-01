package org.phantam.fozminesproofcore.database.executors;

import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.manager.FakePlayerRegistry;
import org.phantam.fozminesproofcore.utils.DebugLogger;

import java.util.Optional;
import java.util.logging.Level;

/**
 * Reloads bot data from the database and updates the registry without respawning.
 * Useful for refreshing metadata such as location or skin after a database change.
 */
public class ReloadSystemAction implements org.phantam.fozminesproofapi.action.IBotAction<Void, Void> {

    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;

    public ReloadSystemAction(FozmineSproofCore plugin, IFakePlayerDatabase database,
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
            Optional<FakePlayerData> freshData = database.loadFakePlayer(botName);

            if (freshData.isPresent()) {
                FakePlayerData newData = freshData.get();
                Player entity = registry.getEntity(botName);

                if (entity != null) {
                    // Update registry with fresh data while keeping the same entity
                    registry.register(newData, entity);
                    plugin.getLogger().log(Level.FINE,
                            "[ReloadSystemAction] Refreshed data for bot '" + botName + "'");
                    DebugLogger.logFine(plugin.getLogger(), "ReloadSystemAction: refreshed %s", botName);
                    refreshed++;
                }
            } else {
                plugin.getLogger().log(Level.WARNING,
                        "[ReloadSystemAction] Bot '" + botName + "' missing in database during reload");
                DebugLogger.log(plugin.getLogger(), "ReloadSystemAction: %s missing in DB", botName);
            }
        }

        DebugLogger.log(plugin.getLogger(), "ReloadSystemAction: reload complete, %d bots refreshed", refreshed);
        return null;
    }
}