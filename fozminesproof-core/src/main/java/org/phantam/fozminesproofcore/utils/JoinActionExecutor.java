package org.phantam.fozminesproofcore.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.util.List;
import java.util.logging.Logger;

public class JoinActionExecutor {

    public static void execute(Player player, List<String> fakePlayerCommands, List<String> consoleCommands, Logger logger) {
        if (fakePlayerCommands != null && !fakePlayerCommands.isEmpty()) {
            for (String cmd : fakePlayerCommands) {
                try {
                    Bukkit.dispatchCommand(player, cmd);
                    DebugLogger.logFine(logger, "JoinActionExecutor: executed fake command: " + cmd);
                } catch (Exception e) {
                    logger.warning("JoinActionExecutor: failed to execute fake command '" + cmd + "': " + e.getMessage());
                }
            }
        }

        if (consoleCommands != null && !consoleCommands.isEmpty()) {
            for (String cmd : consoleCommands) {
                try {
                    String processed = cmd.replace("%fakeplayer_name%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
                    DebugLogger.logFine(logger, "JoinActionExecutor: executed console command: " + processed);
                } catch (Exception e) {
                    logger.warning("JoinActionExecutor: failed to execute console command '" + cmd + "': " + e.getMessage());
                }
            }
        }
    }
}