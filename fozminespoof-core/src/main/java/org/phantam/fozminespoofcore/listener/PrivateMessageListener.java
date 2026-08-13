package org.phantam.fozminespoofcore.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.ai.AiChatProcessor;
import org.phantam.fozminespoofcore.config.AiConfig;

import java.util.Arrays;
import java.util.List;

/**
 * Listens to private message commands (/msg, /tell, /w, /whisper, /pm, /m)
 * directed at AI bots and handles private response generation.
 */
public class PrivateMessageListener implements Listener {

    private final FozmineSpoofCore plugin;
    private final AiConfig aiConfig;
    private final AiChatProcessor aiProcessor;

    private static final List<String> PM_COMMANDS = Arrays.asList(
            "/msg", "/tell", "/w", "/whisper", "/pm", "/m", "/emsg", "/etell", "/ewhisper"
    );

    public PrivateMessageListener(FozmineSpoofCore plugin, AiConfig aiConfig, AiChatProcessor aiProcessor) {
        this.plugin = plugin;
        this.aiConfig = aiConfig;
        this.aiProcessor = aiProcessor;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!aiConfig.isEnabled() || !aiConfig.isPrivateMessageEnabled()) {
            return;
        }

        String commandLine = event.getMessage();
        String lowerCmd = commandLine.toLowerCase();

        String matchedPrefix = null;
        for (String cmd : PM_COMMANDS) {
            if (lowerCmd.startsWith(cmd + " ")) {
                matchedPrefix = cmd;
                break;
            }
        }

        if (matchedPrefix == null) {
            return;
        }

        // Parse `/msg BotName Hello message here`
        String body = commandLine.substring(matchedPrefix.length()).trim();
        int spaceIdx = body.indexOf(' ');
        if (spaceIdx == -1) {
            return; // Chỉ nhập tên, không có nội dung tin nhắn
        }

        String targetBotName = body.substring(0, spaceIdx).trim();
        String rawMessage = body.substring(spaceIdx + 1).trim();

        if (rawMessage.isEmpty()) {
            return;
        }

        // Kiểm tra xem targetBotName có phải là bot đang online không
        Player bot = plugin.getFakePlayerManager().getOnlineBotEntity(targetBotName);
        if (bot == null || !bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
            return; // Không phải bot AI, để server xử lý bình thường
        }

        Player sender = event.getPlayer();
        event.setCancelled(true); // Chặn lệnh vanilla/plugin khác xử lý

        // Hiển thị tin nhắn riêng chiều đi cho người chơi [me -> BotName] msg
        String outgoingFormatted = aiConfig.getPmOutgoingFormat()
                .replace("{bot}", bot.getName())
                .replace("{message}", rawMessage);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', outgoingFormatted));

        DebugLogger.log(plugin.getLogger(), "PrivateMessageListener: PM intercepted from %s to %s: '%s'",
                sender.getName(), bot.getName(), rawMessage);

        boolean isHelpMode = bot.getName().equalsIgnoreCase(aiConfig.getAiHelpBotName());

        // Gọi AI xử lý với flag isPrivateMsg = true
        aiProcessor.processPlayerToAiChatAsync(sender, bot, rawMessage, isHelpMode, true);
    }
}