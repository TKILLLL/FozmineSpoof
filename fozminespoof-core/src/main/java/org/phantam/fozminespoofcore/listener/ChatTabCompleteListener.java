package org.phantam.fozminespoofcore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.AiConfig;
import org.phantam.fozminespoofcore.utils.StringUtils;

import java.util.*;

/**
 * High-precision Tab Completion Listener EXCLUSIVELY for AI Help Bot (ai-help.bot-name).
 * <p>
 * - Only suggests the configured AI Help Bot name (e.g., @FozmineBot).
 * - All other simulated fake players are completely excluded from tab completions.
 * - Suggests Knowledge Base FAQs & questions upon tagging the helper bot.
 * - Supports PM command completions (/msg, /tell, /w, /pm) exclusively for the helper bot.
 * </p>
 */
public class ChatTabCompleteListener implements Listener {

    private final FozmineSpoofCore plugin;

    private static final Set<String> PM_COMMANDS = Set.of(
            "/msg", "/tell", "/w", "/whisper", "/pm", "/m", "/emsg", "/etell", "/ewhisper", "/t"
    );

    private static final List<String> FALLBACK_QUESTION_STARTERS = List.of(
            "gameplay", "shop", "discord", "how to", "what is", "where is", "help", "rules", "donate"
    );

    public ChatTabCompleteListener(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player)) return;

        AiConfig aiConfig = plugin.getAiConfig();

        if (aiConfig == null || !aiConfig.isAiHelpEnabled()) {
            return;
        }

        String helpBotName = aiConfig.getAiHelpBotName();
        if (helpBotName == null || helpBotName.isBlank()) {
            return;
        }

        String buffer = event.getBuffer();
        if (buffer == null) return;

        String tagPrefix = (aiConfig.getAiHelpTagPrefix() != null) ? aiConfig.getAiHelpTagPrefix() : "@";

        if (buffer.startsWith("/")) {
            handlePrivateMessageTab(event, buffer, helpBotName);
            return;
        }

        handlePublicChatTab(event, buffer, tagPrefix, helpBotName, aiConfig);
    }

    private void handlePublicChatTab(TabCompleteEvent event, String buffer, String tagPrefix, String helpBotName, AiConfig aiConfig) {
        int lastPrefixIndex = tagPrefix.isEmpty() ? buffer.lastIndexOf(' ') + 1 : buffer.lastIndexOf(tagPrefix);

        if (!tagPrefix.isEmpty() && lastPrefixIndex == -1) {
            return;
        }

        String afterPrefix = (lastPrefixIndex != -1) ? buffer.substring(lastPrefixIndex) : buffer;
        int spaceIndex = afterPrefix.indexOf(' ');

        if (spaceIndex == -1) {
            String token = tagPrefix.isEmpty() ? afterPrefix : afterPrefix.substring(tagPrefix.length());
            String tokenLower = token.toLowerCase(Locale.ROOT);

            List<String> completions = new ArrayList<>();
            String fullTag = tagPrefix + helpBotName;

            if (helpBotName.toLowerCase(Locale.ROOT).startsWith(tokenLower)) {
                completions.add(fullTag);
            }

            if (!completions.isEmpty()) {
                event.setCompletions(completions);
                DebugLogger.logFine(plugin.getLogger(), "ChatTabComplete: suggested AI Help Bot tag '%s'", fullTag);
            }
            return;
        }

        String mentionedBotWithPrefix = afterPrefix.substring(0, spaceIndex);
        String mentionedBotName = tagPrefix.isEmpty()
                ? mentionedBotWithPrefix
                : (mentionedBotWithPrefix.startsWith(tagPrefix) ? mentionedBotWithPrefix.substring(tagPrefix.length()) : mentionedBotWithPrefix);

        if (!mentionedBotName.equalsIgnoreCase(helpBotName)) {
            return;
        }

        String afterSpace = afterPrefix.substring(spaceIndex + 1);
        String questionToken = afterSpace.trim().toLowerCase(Locale.ROOT);
        String unaccentedToken = StringUtils.stripDiacritics(questionToken).toLowerCase(Locale.ROOT);

        List<String> suggestions = new ArrayList<>();

        List<String> questionPool = getDynamicKnowledgeBaseQuestions(aiConfig);

        for (String question : questionPool) {
            String questionLower = question.toLowerCase(Locale.ROOT);
            String questionUnaccented = StringUtils.stripDiacritics(questionLower);

            boolean matches = questionToken.isEmpty()
                    || questionLower.startsWith(questionToken)
                    || questionUnaccented.startsWith(unaccentedToken)
                    || questionLower.contains(questionToken)
                    || questionUnaccented.contains(unaccentedToken);

            if (matches && !suggestions.contains(question)) {
                suggestions.add(question);
            }
        }

        if (!suggestions.isEmpty()) {
            event.setCompletions(suggestions);
            DebugLogger.logFine(plugin.getLogger(), "ChatTabComplete: suggested %d knowledge-base questions for query '%s'",
                    suggestions.size(), questionToken);
        }
    }

    /**
     * Lấy danh sách câu hỏi / chủ đề tự động từ ai-help.knowledge-base trong ai-chat-bot.yml
     */
    private List<String> getDynamicKnowledgeBaseQuestions(AiConfig aiConfig) {
        if (aiConfig != null) {
            Map<String, String> kb = aiConfig.getAiHelpServerKnowledgeBase();
            if (kb != null && !kb.isEmpty()) {
                return new ArrayList<>(kb.keySet());
            }
        }
        return FALLBACK_QUESTION_STARTERS;
    }

    /**
     * Xử lý gợi ý lệnh PM (/msg, /tell...) CHỈ dành riêng cho AI Help Bot
     */
    private void handlePrivateMessageTab(TabCompleteEvent event, String buffer, String helpBotName) {
        String[] parts = buffer.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);

        if (!PM_COMMANDS.contains(cmd)) return;

        if (parts.length == 1 && buffer.endsWith(" ") || parts.length == 2 && !buffer.endsWith(" ")) {
            String query = (parts.length == 2) ? parts[1].toLowerCase(Locale.ROOT) : "";
            List<String> completions = new ArrayList<>(event.getCompletions());

            if (helpBotName.toLowerCase(Locale.ROOT).startsWith(query) && !completions.contains(helpBotName)) {
                completions.add(0, helpBotName);
            }

            event.setCompletions(completions);
        }
    }
}