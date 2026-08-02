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
                // Chặn toàn bộ các hàm gửi tin nhắn của Paper/Spigot (sendMessage, sendRichMessage, sendPlainMessage...)
                String name = method.getName();
                if (name.startsWith("send") || name.contains("Message")) {
                    return null;
                }
                return method.invoke(Bukkit.getConsoleSender(), args);
            }
    );

    public RankWeightManager(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    public String getRandomRank(Map<String, Integer> rankWeights) {
        if (rankWeights == null || rankWeights.isEmpty()) return "default";

        int totalWeight = 0;
        for (int weight : rankWeights.values()) {
            if (weight > 0) totalWeight += weight;
        }

        if (totalWeight <= 0) return "default";

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

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            Bukkit.dispatchCommand(SILENT_CONSOLE, "lp user " + name + " parent set " + targetRank + " -s");
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("GroupManager") != null) {
            Bukkit.dispatchCommand(SILENT_CONSOLE, "manuadd " + name + " " + targetRank);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("PermissionsEx") != null) {
            Bukkit.dispatchCommand(SILENT_CONSOLE, "pex user " + name + " group set " + targetRank);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("UltraPermissions") != null) {
            Bukkit.dispatchCommand(SILENT_CONSOLE, "up setgroup " + name + " " + targetRank);
            return;
        }

        Bukkit.dispatchCommand(SILENT_CONSOLE, "lp user " + name + " parent set " + targetRank + " -s");
    }

    public void resetRank(String name) {
        if (name == null || name.isBlank()) return;

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            Bukkit.dispatchCommand(SILENT_CONSOLE, "lp user " + name + " clear -s");
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("GroupManager") != null) {
            Bukkit.dispatchCommand(SILENT_CONSOLE, "manuadd " + name + " default");
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("PermissionsEx") != null) {
            Bukkit.dispatchCommand(SILENT_CONSOLE, "pex user " + name + " group set default");
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("UltraPermissions") != null) {
            Bukkit.dispatchCommand(SILENT_CONSOLE, "up setgroup " + name + " default");
            return;
        }

        Bukkit.dispatchCommand(SILENT_CONSOLE, "lp user " + name + " clear -s");
    }
}