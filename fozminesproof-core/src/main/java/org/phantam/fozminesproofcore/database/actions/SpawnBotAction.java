package org.phantam.fozminesproofcore.database.actions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.database.FakePlayerRegistry;
import java.util.Optional;

public class SpawnBotAction implements org.phantam.fozminesproofapi.action.IBotAction<String, Boolean> {
    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;
    private final FakePlayerBroadcaster broadcaster;

    public SpawnBotAction(FozmineSproofCore plugin, IFakePlayerDatabase database,
                          FakePlayerRegistry registry, FakePlayerBroadcaster broadcaster) {
        this.plugin = plugin;
        this.database = database;
        this.registry = registry;
        this.broadcaster = broadcaster;
    }

    @Override
    public Boolean execute(String name) {
        Optional<FakePlayerData> opt = database.loadFakePlayer(name);
        if (opt.isEmpty()) return false;

        FakePlayerData data = opt.get();
        data.setActive(true);
        database.saveFakePlayer(data);

        Player entity = internalNmsSpawn(data);
        registry.register(data, entity);

        broadcaster.broadcastJoin(data.getName());
        return true;
    }

    private Player internalNmsSpawn(FakePlayerData data) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null || plugin.getBridge() == null) return null;

        Location loc = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());
        return plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), loc);
    }
}
