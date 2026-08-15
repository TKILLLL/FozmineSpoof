package org.phantam.fozminespoofcore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.AiConfig;
import org.phantam.fozminespoofcore.config.ChatConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Listener for tab completion in chat.
 * <p>
 * - Suggests bot names when typing '@' or '@partialName'.
 * - Prioritizes the AI Help Bot (e.g., FozmineBot).
 * - Suggests common question starters after tagging a bot and pressing space.
 * </p>
 */
public class ChatTabCompleteListener implements Listener {

    private final FozmineSpoofCore plugin;
    private static final List<String> QUESTION_STARTERS = Arrays.asList(
            "how", "what", "where", "when", "why", "who", "which",
            "can", "does", "is", "are", "do", "did", "has", "have",
            "help", "tell", "show", "give", "find", "get"
    );

    public ChatTabCompleteListener(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player)) return;

        AiConfig aiConfig = plugin.getAiConfig();
        ChatConfig chatConfig = plugin.getConfigManager().getChatConfig();

        boolean aiEnabled = aiConfig != null && aiConfig.isEnabled();
        boolean chatEnabled = chatConfig != null && chatConfig.isEnabled();

        // Chỉ chạy khi hệ thống Chat hoặc hệ thống AI đang hoạt động
        if (!aiEnabled && !chatEnabled) {
            return;
        }

        String buffer = event.getBuffer();

        // Bỏ qua nếu người chơi đang gõ lệnh (/spoof, /msg,...)
        if (buffer.startsWith("/")) return;

        // Tìm ký tự '@' cuối cùng trong đoạn chat
        int atIndex = buffer.lastIndexOf('@');
        if (atIndex == -1) return;

        String afterAt = buffer.substring(atIndex);
        int spaceAfterAt = afterAt.indexOf(' ');
        String mentionPart = (spaceAfterAt == -1) ? afterAt : afterAt.substring(0, spaceAfterAt);

        // Trường hợp 1: Đang gõ tên Bot (Chưa có khoảng trắng sau @botname, VD: "@", "@F", "hello @F")
        if (spaceAfterAt == -1) {
            String partial = mentionPart.substring(1).toLowerCase(); // Ký tự phía sau dấu '@'
            List<String> completions = new ArrayList<>();

            // 1. Ưu tiên đưa AI Help Bot (VD: FozmineBot) lên đầu danh sách gợi ý
            if (aiConfig != null && aiConfig.isAiHelpEnabled()) {
                String helpBot = aiConfig.getAiHelpBotName();
                if (helpBot != null && !helpBot.isBlank() && helpBot.toLowerCase().startsWith(partial)) {
                    completions.add("@" + helpBot);
                }
            }

            // 2. Thêm các Bot AI / FakePlayer khác đang Online
            if (plugin.getFakePlayerManager() != null) {
                for (FakePlayerData botData : plugin.getFakePlayerManager().getOnlineBotsData()) {
                    String name = botData.getName();
                    if (name == null || name.isBlank()) continue;

                    String tag = "@" + name;
                    if (name.toLowerCase().startsWith(partial) && !completions.contains(tag)) {
                        completions.add(tag);
                    }
                }
            }

            if (!completions.isEmpty()) {
                event.setCompletions(completions);
                DebugLogger.logFine(plugin.getLogger(), "ChatTabComplete: suggested bot mentions for '%s': %s",
                        mentionPart, completions);
            }
            return;
        }

        // Trường hợp 2: Đã gõ xong tên Bot + khoảng trắng -> Gợi ý các từ bắt đầu câu hỏi
        if (buffer.endsWith(" ") || afterAt.length() > mentionPart.length()) {
            String afterSpace = buffer.substring(atIndex + mentionPart.length() + 1);
            String partialQuestion = afterSpace.toLowerCase();

            List<String> suggestions = new ArrayList<>();
            for (String starter : QUESTION_STARTERS) {
                if (starter.startsWith(partialQuestion)) {
                    suggestions.add(starter);
                }
            }

            if (!suggestions.isEmpty()) {
                event.setCompletions(suggestions);
                DebugLogger.logFine(plugin.getLogger(), "ChatTabComplete: suggested question starters for '%s': %s",
                        partialQuestion, suggestions);
            }
        }
    }
}