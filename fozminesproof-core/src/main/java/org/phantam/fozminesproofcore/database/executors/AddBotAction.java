package org.phantam.fozminesproofcore.database.executors;

import org.bukkit.Location;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Executes the addition of a new fake player to the database.
 * The bot is created with an offline-mode UUID based on its name.
 */
public class AddBotAction implements org.phantam.fozminesproofapi.action.IBotAction<AddBotAction.Request, Void> {

    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;

    public AddBotAction(FozmineSproofCore plugin, IFakePlayerDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public Void execute(Request request) {
        DebugLogger.log(plugin.getLogger(), "AddBotAction: adding bot '%s'", request.name());

        String worldName = plugin.getConfigManager().getBotWorldName();

        // Generate a deterministic UUID based on the name (offline-mode style)
        UUID uuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + request.name()).getBytes(StandardCharsets.UTF_8)
        );

        DebugLogger.logFine(plugin.getLogger(), "AddBotAction: generated UUID %s for %s", uuid, request.name());

        FakePlayerData data = new FakePlayerData.Builder()
                .name(request.name())
                .uuid(uuid)
                .world(worldName)
                .location(0.0, 64.0, 0.0, 0.0f, 0.0f)
                .active(false)
                .build();

        database.saveFakePlayer(data);

        DebugLogger.log(plugin.getLogger(), "AddBotAction: successfully added bot '%s'", request.name());

        return null;
    }

    /**
     * Request object containing the bot name and spawn location.
     * (Location is currently unused but kept for future extensions.)
     */
    public record Request(String name, Location location) {}
}