package org.phantam.fozminesproofcore.commands.subs;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.factory.VoidWorldFactory;

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
        ConfigManager config = plugin.getConfigManager();

        // Gửi tin nhắn thông báo bắt đầu tiến trình (Lấy từ messages.yml hệ thống)
        sender.sendMessage(config.getMessages().getMessage("system.prefix") + "§eĐang làm mới cấu hình hệ thống...");

        try {
            // 1. Đồng bộ nạp lại toàn bộ file config.yml và dữ liệu messages.yml đồng thời
            config.reloadAllConfigs();

            // 2. Gọi Factory kiểm tra và tái thiết lập thế giới Void chuyên dụng an toàn
            VoidWorldFactory.createVoidWorld(plugin, config.getBotWorldName());

            // 3. Làm mới kho tệp tin chứa tin nhắn ngẫu nhiên của hệ thống chat
            if (plugin.getMessageLoader() != null) {
                plugin.getMessageLoader().loadMessages();
            }

            // 4. Hủy chu kỳ Chat Task cũ ngay lập tức để giải phóng Thread chạy ngầm
            if (plugin.getChatScheduler() != null) {
                plugin.getChatScheduler().stop();
            }

            // 5. Đồng bộ nạp lại RAM Cache thực thể và tái kích hoạt luồng Chat tự động
            // Trì hoãn 1 Tick (0.05 giây) bảo vệ hàng đợi đăng ký thực thể của Spigot không bị kẹt thread
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    // Dọn dẹp RAM cũ, quét SQL và sinh lại các FakePlayer đánh dấu Active
                    if (plugin.getFakePlayerManager() != null) {
                        plugin.getFakePlayerManager().reloadSystem();
                    }

                    // Khởi động lại Scheduler điều phối Chat dựa trên cấu hình thời gian mới vừa nạp
                    if (plugin.getChatScheduler() != null) {
                        plugin.getChatScheduler().start(config.getChatConfig());
                    }

                    // Gửi thông báo thành công trích xuất từ file messages.yml an toàn
                    sender.sendMessage(config.getMessages().getMessage("system.reload-success"));

                } catch (Exception ex) {
                    sender.sendMessage(config.getMessages().getMessage("system.prefix") + "§cCó lỗi xảy ra trong tiến trình trì hoãn 1-tick!");
                    plugin.getLogger().severe("Lỗi hàng đợi trì hoãn thực thi Reload:");
                    ex.printStackTrace();
                }
            }, 1L);

        } catch (Exception e) {
            sender.sendMessage(config.getMessages().getMessage("system.prefix") + "§cĐã xảy ra lỗi nghiêm trọng khi reload hệ thống! Hãy kiểm tra Console.");
            plugin.getLogger().severe("🚨 Lỗi thực thi sub-command reload:");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
