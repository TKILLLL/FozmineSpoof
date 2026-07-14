package org.phantam.fozminesproofCore.commands.subs;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofCore.FozmineSproofCore;
import java.util.Collections;
import java.util.List;

public class ReloadCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public ReloadCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "Reload lại toàn bộ cấu hình plugin và danh sách bot";
    }

    @Override
    public String getSyntax() {
        return "/sproof reload";
    }

    @Override
    public String getPermission() {
        return "fozminesproof.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "[FozmineSproof] Đang làm mới cấu hình hệ thống...");

        try {
            // 1. Tải lại file dữ liệu config.yml tập trung vào bộ nhớ RAM thông qua ConfigManager
            this.plugin.getConfigManager().reload();

            // 2. KIỂM TRA VÀ TÁI KHỞI TẠO THẾ GIỚI VOID MỚI (Nếu có thay đổi tên trong file config)
            // Bước này bảo đảm thế giới luôn luôn khả dụng trước khi Manager nạp bot lên tọa độ hiển thị
            this.plugin.getConfigManager().createVoidWorld();

            // 3. Làm mới luồng Runtime Cache của FakePlayer, dọn bộ nhớ RAM và nạp lại các bot Active từ SQL
            if (this.plugin.getFakePlayerManager() != null) {
                this.plugin.getFakePlayerManager().reloadSystem();
            }

            sender.sendMessage(ChatColor.GREEN + "[FozmineSproof] Làm mới file cấu hình, kiểm tra thế giới và danh sách bot thành công!");
            sender.sendMessage(ChatColor.GRAY + "Hệ thống PAPI đã tự động đồng bộ danh sách cấu hình biến mới.");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "[FozmineSproof] Đã xảy ra lỗi nghiêm trọng khi reload config! Hãy kiểm tra Console.");
            this.plugin.getLogger().severe("Lỗi thực thi sub-command reload:");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
