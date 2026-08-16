package org.phantam.fozminespoofcore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.ai.AiChatProcessor;
import org.phantam.fozminespoofcore.config.AiConfig;
import org.phantam.fozminespoofcore.utils.ColorUtils;

import java.util.List;
import java.util.Locale;

/**
 * Intercepts player-to-player direct messaging commands directed towards simulated AI fake players.
 */
public class PrivateMessageListener implements Listener {

    private final FozmineSpoofCore plugin;
    private final AiConfig aiConfig;
    private final AiChatProcessor aiProcessor;

    private static final List<String> PM_COMMANDS = List.of(
            "/msg", "/tell", "/w", "/whisper", "/pm", "/m", "/emsg", "/etell", "/ewhisper", "/t"
    );

    public PrivateMessageListener(FozmineSpoofCore plugin, AiConfig aiConfig, AiChatProcessor aiProcessor) {
        this.plugin = plugin;
        this.aiConfig = aiConfig;
        this.aiProcessor = aiProcessor;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!aiConfig.isEnabled()) {
            return;
        }

        String commandLine = event.getMessage().trim();
        String lowerCmd = commandLine.toLowerCase(Locale.ROOT);

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

        String body = commandLine.substring(matchedPrefix.length()).trim();
        int spaceIdx = body.indexOf(' ');
        if (spaceIdx == -1) {
            return;
        }

        String targetBotName = body.substring(0, spaceIdx).trim();
        String rawMessage = body.substring(spaceIdx + 1).trim();

        if (rawMessage.isEmpty()) {
            return;
        }

        Player bot = plugin.getFakePlayerManager().getOnlineBotEntity(targetBotName);
        if (bot == null || !bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
            return;
        }

        Player sender = event.getPlayer();

        if ("custom".equalsIgnoreCase(aiConfig.getChatFormatMethod())) {
            event.setCancelled(true);
            String outgoingFormatted = aiConfig.getPmOutgoingFormat()
                    .replace("{bot}", bot.getName())
                    .replace("%fakeplayer_name%", bot.getName())
                    .replace("{message}", rawMessage)
                    .replace("%fakeplayer_message%", rawMessage);
            sender.sendMessage(ColorUtils.colorize(outgoingFormatted));
        }

        DebugLogger.log(plugin.getLogger(), "PrivateMessageListener: Intercepted PM from %s to %s: '%s'",
                sender.getName(), bot.getName(), rawMessage);

        boolean isHelpMode = (aiConfig.isAiHelpEnabled() && bot.getName().equalsIgnoreCase(aiConfig.getAiHelpBotName()));

        aiProcessor.processPlayerToAiChatAsync(sender, bot, rawMessage, isHelpMode, true);
    }
}