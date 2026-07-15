package org.phantam.fozminesproofcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.commands.subs.*;
import org.phantam.fozminesproofcore.config.MessageManager;

import java.util.*;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final FozmineSproofCore plugin;

    // TỐI ƯU HIỆU NĂNG: Sử dụng Map với khóa chữ thường để truy xuất lệnh ngay lập tức với tốc độ O(1)
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public CommandManager(FozmineSproofCore plugin) {
        this.plugin = plugin;

        // Đăng ký tập trung toàn bộ hệ thống lệnh con (Sub-commands)
        this.registerSubCommand(new AddCommand(plugin));
        this.registerSubCommand(new SpawnCommand(plugin));
        this.registerSubCommand(new DespawnCommand(plugin));
        this.registerSubCommand(new RemoveCommand(plugin));
        this.registerSubCommand(new ListCommand(plugin));
        this.registerSubCommand(new InfoCommand(plugin));
        this.registerSubCommand(new ReloadCommand(plugin));
    }

    /**
     * Hàm phụ trợ gom khóa chữ thường để đăng ký lệnh an toàn vùng nhớ
     */
    private void registerSubCommand(SubCommand cmd) {
        this.subCommands.put(cmd.getName().toLowerCase(), cmd);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager msgManager = plugin.getConfigManager().getMessages();

        // Trường hợp người dùng gõ lệnh trống: /sproof -> Tự động in bảng trợ giúp hướng dẫn
        if (args.length == 0) {
            this.sendHelpMessage(sender, msgManager);
            return true;
        }

        // Lấy trực tiếp lớp thực thi lệnh con từ Map chỉ trong 1 thao tác (Tốc độ tối đa)
        String subName = args[0].toLowerCase();
        SubCommand sub = this.subCommands.get(subName);

        // Trường hợp gõ sai tên sub-command
        if (sub == null) {
            sender.sendMessage(msgManager.getMessage("system.unknown-command"));
            return true;
        }

        // Kiểm tra phân quyền an toàn bảo mật hệ thống
        if (!sender.hasPermission(sub.getPermission())) {
            sender.sendMessage(msgManager.getMessage("system.no-permission"));
            return true;
        }

        // Ủy quyền thực thi cho lớp con biệt lập xử lý
        try {
            sub.execute(sender, args);
        } catch (Exception e) {
            sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§cĐã xảy ra lỗi cục bộ khi thực thi lệnh này!");
            plugin.getLogger().severe("Lỗi thực thi lệnh con '/sproof " + subName + "': " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        String input = args[0].toLowerCase();

        // Xử lý Gợi ý cấp 1: Hiển thị danh sách các tên lệnh con (/sproof [Gợi ý tại đây])
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (SubCommand sub : this.subCommands.values()) {
                if (sub.getName().toLowerCase().startsWith(input) && sender.hasPermission(sub.getPermission())) {
                    completions.add(sub.getName());
                }
            }
            return completions;
        }

        // Xử lý Gợi ý cấp 2+: Chuyển tiếp (Delegate) quyền gợi ý tham số sâu hơn cho chính lớp SubCommand đó tự lo
        SubCommand sub = this.subCommands.get(input);
        if (sub != null && sender.hasPermission(sub.getPermission())) {
            return sub.tabComplete(sender, args);
        }

        return Collections.emptyList();
    }

    /**
     * Dựng bảng menu hướng dẫn tự động hóa đa ngôn ngữ trích xuất trực tiếp từ file messages.yml
     */
    private void sendHelpMessage(CommandSender sender, MessageManager msgManager) {
        // Chỉ in tiêu đề dạng text thô, không kèm prefix hệ thống để giao diện cân đối, vuông vắn
        sender.sendMessage(msgManager.getOnlyMessage("commands.help.header"));

        for (SubCommand sub : this.subCommands.values()) {
            if (sender.hasPermission(sub.getPermission())) {
                // Rút chuỗi mô tả động tương ứng với tên của sub-command trong file cấu hình ngôn ngữ
                String descPath = "commands.help.list." + sub.getName().toLowerCase();
                String description = msgManager.getOnlyMessage(descPath);

                // Nếu lỡ quên chưa cấu hình dòng dịch trong file, lấy tạm mô tả mặc định cứng trong mã nguồn Java
                if (description.startsWith("§cMissing")) {
                    description = sub.getDescription();
                }

                // Ghép nối cấu trúc hiển thị chuẩn hóa
                String helpLine = msgManager.getOnlyMessage("commands.help.format")
                        .replace("%syntax%", sub.getSyntax())
                        .replace("%description%", description);

                sender.sendMessage(helpLine);
            }
        }
        sender.sendMessage(msgManager.getOnlyMessage("commands.help.footer"));
    }
}
