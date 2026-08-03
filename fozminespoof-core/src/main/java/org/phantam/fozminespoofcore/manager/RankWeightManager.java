package org.phantam.fozminespoofcore.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.phantam.fozminespoofcore.FozmineSpoofCore;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RankWeightManager {

    private final FozmineSpoofCore plugin;
    private final Map<UUID, PermissionAttachment> fallbackAttachments = new HashMap<>();

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
        if (player == null) return;

        String targetRank = (chosenRank != null && !chosenRank.isBlank()) ? chosenRank : "default";
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                LuckPerms luckPerms = LuckPermsProvider.get();

                if (luckPerms.getGroupManager().getGroup(targetRank) == null) {
                    targetRank = "default";
                }

                User user = luckPerms.getUserManager().loadUser(uuid, name).join();
                if (user != null) {

                    for (Node node : user.transientData().toCollection()) {
                        if (NodeType.INHERITANCE.matches(node)) {
                            user.transientData().remove(node);
                        }
                    }

                    InheritanceNode node = InheritanceNode.builder(targetRank).build();
                    user.transientData().add(node);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        try {
            resetFallbackAttachment(player);

            PermissionAttachment attachment = player.addAttachment(plugin);
            attachment.setPermission("group." + targetRank, true);
            fallbackAttachments.put(uuid, attachment);
        } catch (Exception ignored) {
        }
    }

    public void resetRank(String name) {
        if (name == null || name.isBlank()) return;

        Player player = Bukkit.getPlayer(name);
        UUID uuid = (player != null) ? player.getUniqueId() : Bukkit.getOfflinePlayer(name).getUniqueId();

        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                LuckPerms luckPerms = LuckPermsProvider.get();
                User user = luckPerms.getUserManager().loadUser(uuid, name).join();
                if (user != null) {

                    for (Node node : user.transientData().toCollection()) {
                        if (NodeType.INHERITANCE.matches(node)) {
                            user.transientData().remove(node);
                        }
                    }

                }
            } catch (Exception ignored) {
            }
        }

        if (player != null) {
            resetFallbackAttachment(player);
        } else {
            fallbackAttachments.remove(uuid);
        }
    }

    private void resetFallbackAttachment(Player player) {
        PermissionAttachment attachment = fallbackAttachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (Exception ignored) {
            }
        }
    }
}