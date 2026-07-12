package org.phantam.fozminesproofCore.commands.subs;

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
        return "Reload lại toàn bộ cấu hình plugin";
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
        // Cho phép cả Console và Player thực thi, không giới hạn thực thể gửi
        sender.sendMessage("[FozmineSproof] Đang làm mới cấu hình...");

        try {
            // Tải lại file config.yml tập trung thông qua ConfigManager đã thiết lập ở Core
            this.plugin.getConfigManager().reload();

            sender.sendMessage("[FozmineSproof] Làm mới file cấu hình thành công!");
            sender.sendMessage("Hệ thống PAPI đã tự động đồng bộ danh sách biến mới.");

        } catch (Exception e) {
            sender.sendMessage("[FozmineSproof] Đã xảy ra lỗi khi reload config! Hãy kiểm tra Console.");
            this.plugin.getLogger().severe("Lỗi thực thi sub-command reload:");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
