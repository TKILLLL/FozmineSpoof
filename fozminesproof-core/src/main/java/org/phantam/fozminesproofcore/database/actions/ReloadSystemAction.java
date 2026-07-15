package org.phantam.fozminesproofcore.database.actions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.database.FakePlayerRegistry;
import java.util.HashSet;

public class ReloadSystemAction implements org.phantam.fozminesproofapi.action.IBotAction<Void, Void> {
    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;
    private final DespawnBotAction despawnAction;

    public ReloadSystemAction(FozmineSproofCore plugin, IFakePlayerDatabase database,
                              FakePlayerRegistry registry, DespawnBotAction despawnAction) {
        this.plugin = plugin;
        this.database = database;
        this.registry = registry;
        this.despawnAction = despawnAction;
    }

    @Override
    public Void execute(Void unused) {
        // Thu hồi an toàn toàn bộ bot đang chạy
        for (String botName : new HashSet<>(registry.getOnlineNames())) {
            despawnAction.execute(botName);
        }
        registry.clearAll();

        // Tải lại các bot Active
        for (FakePlayerData data : database.loadAllPlayers()) {
            if (data.isActive()) {
                Player entity = internalNmsSpawn(data);
                registry.register(data, entity);
            }
        }
        return null;
    }

    private Player internalNmsSpawn(FakePlayerData data) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null || plugin.getBridge() == null) return null;

        Location loc = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());
        return plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), loc);
    }
}
