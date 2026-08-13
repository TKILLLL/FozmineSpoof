package org.phantam.fozminespoofcore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.ChatConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Listener for tab completion in chat.
 * <p>
 * - Suggests bot names when typing '@' followed by a partial name.
 * - When '@botname ' (with space) is typed, suggests common question starters.
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
        ChatConfig chatConfig = plugin.getConfigManager().getChatConfig();
        if (chatConfig == null || !chatConfig.isEnabled()) {
            return;
        }

        if (!(event.getSender() instanceof Player)) return;

        Player player = (Player) event.getSender();
        String buffer = event.getBuffer();

        // Ignore commands
        if (buffer.startsWith("/")) return;

        // Find last '@' in buffer
        int atIndex = buffer.lastIndexOf('@');
        if (atIndex == -1) return;

        String afterAt = buffer.substring(atIndex);
        int spaceAfterAt = afterAt.indexOf(' ');
        String mentionPart = (spaceAfterAt == -1) ? afterAt : afterAt.substring(0, spaceAfterAt);

        // Case 1: Still typing the mention (no space after @botname)
        if (spaceAfterAt == -1) {
            String partial = mentionPart.substring(1); // remove '@'
            List<String> botNames = plugin.getFakePlayerManager().getOnlineBotsData().stream()
                    .map(data -> data.getName())
                    .filter(name -> name.toLowerCase().startsWith(partial.toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());

            if (botNames.isEmpty()) return;

            List<String> completions = new ArrayList<>();
            String prefix = buffer.substring(0, atIndex + 1);
            for (String name : botNames) {
                completions.add(prefix + name + " ");
            }

            event.setCompletions(completions);
            DebugLogger.logFine(plugin.getLogger(), "ChatTabComplete: suggested bot names for '%s': %s", partial, completions);
            return;
        }

        // Case 2: Have space after mention → suggest question starters
        // Only if buffer ends with a space and we are after the mention+space
        if (buffer.endsWith(" ")) {
            String afterSpace = buffer.substring(atIndex + mentionPart.length() + 1);
            String partialQuestion = afterSpace.toLowerCase();

            List<String> suggestions = QUESTION_STARTERS.stream()
                    .filter(s -> s.startsWith(partialQuestion))
                    .collect(Collectors.toList());

            if (!suggestions.isEmpty()) {
                int tokenStart = buffer.lastIndexOf(' ') + 1;
                String prefix = buffer.substring(0, tokenStart);
                List<String> completions = new ArrayList<>();
                for (String s : suggestions) {
                    completions.add(prefix + s);
                }
                event.setCompletions(completions);
                DebugLogger.logFine(plugin.getLogger(), "ChatTabComplete: suggested question starters for '%s': %s",
                        partialQuestion, completions);
            }
        }
    }
}