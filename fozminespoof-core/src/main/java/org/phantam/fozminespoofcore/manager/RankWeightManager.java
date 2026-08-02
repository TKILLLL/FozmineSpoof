package org.phantam.fozminespoofcore.manager;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RankWeightManager {

    private final FozmineSpoofCore plugin;

    private static final ConsoleCommandSender SILENT_CONSOLE = (ConsoleCommandSender) Proxy.newProxyInstance(
            RankWeightManager.class.getClassLoader(),
            new Class<?>[]{ConsoleCommandSender.class},
            (proxy, method, args) -> {
                if (method.getName().startsWith("sendMessage")) {
                    return null;
                }
                return method.invoke(Bukkit.getConsoleSender(), args);
            }
    );

    public RankWeightManager(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    public String getRandomRank(Map<String, Integer> rankWeights) {
        if (rankWeights == null || rankWeights.isEmpty()) {
            return "default";
        }

        int totalWeight = 0;
        for (int weight : rankWeights.values()) {
            if (weight > 0) {
                totalWeight += weight;
            }
        }

        if (totalWeight <= 0) {
            return "default";
        }

        int randomVal = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentSum = 0;

        for (Map.Entry<String, Integer> entry : rankWeights.entrySet()) {
            int weight = entry.getValue();
            if (weight <= 0) continue;

            currentSum += weight;
            if (randomVal < currentSum) {
                return entry.getKey();
            }
        }

        return "default";
    }

    public void assignRank(Player player, String chosenRank) {
        if (player == null || !player.isOnline()) return;

        String targetRank = (chosenRank != null && !chosenRank.isBlank()) ? chosenRank : "default";
        String name = player.getName();

        // 1. LuckPerms (Thêm cờ -s để chạy chế độ Silent Im Lặng)
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            String cmd = "lp user " + name + " parent set " + targetRank + " -s";
            Bukkit.dispatchCommand(SILENT_CONSOLE, cmd);
            DebugLogger.log(plugin.getLogger(), "RankWeightManager: assigned rank '%s' to bot %s via LuckPerms", targetRank, name);
            return;
        }

        // 2. GroupManager
        if (Bukkit.getPluginManager().getPlugin("GroupManager") != null) {
            String cmd = "manuadd " + name + " " + targetRank;
            Bukkit.dispatchCommand(SILENT_CONSOLE, cmd);
            DebugLogger.log(plugin.getLogger(), "RankWeightManager: assigned rank '%s' to bot %s via GroupManager", targetRank, name);
            return;
        }

        // 3. PermissionsEx (PEX)
        if (Bukkit.getPluginManager().getPlugin("PermissionsEx") != null) {
            String cmd = "pex user " + name + " group set " + targetRank;
            Bukkit.dispatchCommand(SILENT_CONSOLE, cmd);
            DebugLogger.log(plugin.getLogger(), "RankWeightManager: assigned rank '%s' to bot %s via PEX", targetRank, name);
            return;
        }

        // 4. UltraPermissions
        if (Bukkit.getPluginManager().getPlugin("UltraPermissions") != null) {
            String cmd = "up setgroup " + name + " " + targetRank;
            Bukkit.dispatchCommand(SILENT_CONSOLE, cmd);
            DebugLogger.log(plugin.getLogger(), "RankWeightManager: assigned rank '%s' to bot %s via UltraPermissions", targetRank, name);
            return;
        }

        String fallbackCmd = "lp user " + name + " parent set " + targetRank + " -s";
        Bukkit.dispatchCommand(SILENT_CONSOLE, fallbackCmd);
        DebugLogger.log(plugin.getLogger(), "RankWeightManager: executed fallback command '%s'", fallbackCmd);
    }

    public void resetRank(String name) {
        if (name == null || name.isBlank()) return;

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            String cmd = "lp user " + name + " clear -s";
            Bukkit.dispatchCommand(SILENT_CONSOLE, cmd);
            DebugLogger.log(plugin.getLogger(), "RankWeightManager: cleared permissions for despawned bot %s", name);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("GroupManager") != null) {
            String cmd = "manuadd " + name + " default";
            Bukkit.dispatchCommand(SILENT_CONSOLE, cmd);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("PermissionsEx") != null) {
            String cmd = "pex user " + name + " group set default";
            Bukkit.dispatchCommand(SILENT_CONSOLE, cmd);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("UltraPermissions") != null) {
            String cmd = "up setgroup " + name + " default";
            Bukkit.dispatchCommand(SILENT_CONSOLE, cmd);
            return;
        }

        String fallbackCmd = "lp user " + name + " clear -s";
        Bukkit.dispatchCommand(SILENT_CONSOLE, fallbackCmd);
    }
}