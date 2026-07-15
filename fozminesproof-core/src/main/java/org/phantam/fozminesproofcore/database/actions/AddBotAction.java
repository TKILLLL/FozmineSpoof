package org.phantam.fozminesproofcore.database.actions;

import org.bukkit.Location;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import java.util.UUID;

public class AddBotAction implements org.phantam.fozminesproofapi.action.IBotAction<AddBotAction.Request, Void> {
    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;

    public AddBotAction(FozmineSproofCore plugin, IFakePlayerDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public Void execute(Request req) {
        String targetWorld = plugin.getConfigManager().getBotWorldName();

        FakePlayerData data = new FakePlayerData(
                req.name, UUID.randomUUID(), targetWorld,
                0.0, 64.0, 0.0, // Ép trục tọa độ cố định của thế giới trống
                0.0f, 0.0f, false
        );
        database.saveFakePlayer(data);
        return null;
    }

    // Record hoặc Static Class để gom tham số đầu vào cho lệnh ADD
    public static record Request(String name, Location location) {}
}
