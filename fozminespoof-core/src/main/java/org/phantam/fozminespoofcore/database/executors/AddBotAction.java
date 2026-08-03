package org.phantam.fozminespoofcore.database.executors;

import org.bukkit.Location;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Executes the addition of a new fake player to the database.
 * The bot is created with an offline-mode UUID based on its name.
 */
public class AddBotAction implements org.phantam.fozminespoofapi.action.IBotAction<AddBotAction.Request, Void> {

    private final FozmineSpoofCore plugin;
    private final IFakePlayerDatabase database;

    public AddBotAction(FozmineSpoofCore plugin, IFakePlayerDatabase database) {
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
    public record Request(String name, Location location) {
    }
}