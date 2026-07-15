package org.phantam.fozminesproofcore.database;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.database.actions.*;

import java.util.Collection;

public class FakePlayerManager {

    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;

    // Đóng gói các lớp thực thi hành động độc lập
    private final AddBotAction addAction;
    private final SpawnBotAction spawnAction;
    private final DespawnBotAction despawnAction;
    private final RemoveBotAction removeAction;
    private final ReloadSystemAction reloadAction;

    public FakePlayerManager(FozmineSproofCore plugin, IFakePlayerDatabase database) {
        this.database = database;
        this.registry = new FakePlayerRegistry();
        FakePlayerBroadcaster broadcaster = new FakePlayerBroadcaster(plugin.getConfigManager());

        // Đóng gói phụ thuộc (Dependency Injection thủ công)
        this.addAction = new AddBotAction(plugin, database);
        this.spawnAction = new SpawnBotAction(plugin, database, registry, broadcaster);
        this.despawnAction = new DespawnBotAction(plugin, database, registry, broadcaster);
        this.removeAction = new RemoveBotAction(database, this.despawnAction);
        this.reloadAction = new ReloadSystemAction(plugin, database, registry, this.despawnAction);
    }

    // --- Ủy quyền thực thi qua các Command Classes biệt lập ---

    public void addBot(String name, Location loc) {
        addAction.execute(new AddBotAction.Request(name, loc));
    }

    public boolean spawnBot(String name) {
        return spawnAction.execute(name);
    }

    public boolean despawnBot(String name) {
        return despawnAction.execute(name);
    }

    public boolean removeBot(String name) {
        return removeAction.execute(name);
    }

    public void reloadSystem() {
        reloadAction.execute(null);
    }

    // --- Các hàm truy vấn dữ liệu từ Registry / Database ---

    public Player getOnlineBotEntity(String name) {
        return registry.getEntity(name);
    }

    public Collection<FakePlayerData> getAllDatabaseBots() {
        return database.loadAllPlayers();
    }

    public Collection<FakePlayerData> getOnlineBotsData() {
        return registry.getOnlineData();
    }

    public boolean isBotOnline(String name) {
        return registry.isOnline(name);
    }
}
