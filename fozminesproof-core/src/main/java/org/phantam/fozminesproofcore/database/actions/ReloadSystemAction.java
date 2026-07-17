package org.phantam.fozminesproofcore.database.actions;

import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.database.FakePlayerRegistry;
import java.util.Optional;

public class ReloadSystemAction implements org.phantam.fozminesproofapi.action.IBotAction<Void, Void> {
    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;

    // Khóa Constructor sạch sẽ, không phụ thuộc vào DespawnBotAction
    public ReloadSystemAction(FozmineSproofCore plugin, IFakePlayerDatabase database, FakePlayerRegistry registry) {
        this.plugin = plugin;
        this.database = database;
        this.registry = registry;
    }

    @Override
    public Void execute(Void unused) {
        // Vòng lặp quét qua toàn bộ tên Bot hiện đang online trên RAM
        for (String botName : registry.getOnlineNames()) {
            // Tải dữ liệu mới nhất của Bot đó từ Database lên
            Optional<FakePlayerData> freshData = database.loadFakePlayer(botName);

            if (freshData.isPresent()) {
                FakePlayerData newData = freshData.get();
                // Lấy thực thể Player Entity hiện tại đang đứng im trong game ra
                Player currentEntity = registry.getEntity(botName);

                if (currentEntity != null) {
                    // Đẩy dữ liệu mới (newData) đè lên dữ liệu cũ trong Registry,
                    // giữ nguyên thực thể Player (currentEntity) không cho biến mất
                    registry.register(newData, currentEntity);
                }
            }
        }
        return null;
    }
}