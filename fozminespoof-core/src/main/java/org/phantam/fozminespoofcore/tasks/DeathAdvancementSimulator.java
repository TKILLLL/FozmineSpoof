package org.phantam.fozminespoofcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.BotSelector;
import org.phantam.fozminespoofcore.utils.ColorUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodically broadcasts realistic Vanilla death messages and milestone advancements.
 */
public class DeathAdvancementSimulator extends BukkitRunnable {

    private final FozmineSpoofCore plugin;
    private final BotSelector botSelector;

    private static final List<String> DEATH_TEMPLATES = List.of(
            "&f%player% fell from a high place",
            "&f%player% was slain by Zombie",
            "&f%player% was shot by Skeleton",
            "&f%player% tried to swim in lava",
            "&f%player% was blown up by Creeper",
            "&f%player% suffocated in a wall",
            "&f%player% drowned",
            "&f%player% hit the ground too hard",
            "&f%player% was killed by [Intentional Game Design]",
            "&f%player% experienced kinetic energy"
    );

    private static final List<String> ADVANCEMENTS = List.of(
            "Diamonds!",
            "We Need to Go Deeper",
            "Suit Up",
            "Stone Age",
            "Acquire Hardware",
            "Monster Hunter",
            "Into Fire",
            "Return to Sender",
            "Cover Me in Debris",
            "Eye Spy"
    );

    public DeathAdvancementSimulator(FozmineSpoofCore plugin, BotSelector botSelector) {
        this.plugin = plugin;
        this.botSelector = botSelector;
    }

    @Override
    public void run() {
        if (!plugin.isEnabled()) return;

        List<Player> bots = botSelector.selectRandomBots(1);
        if (bots.isEmpty()) return;

        Player selectedBot = bots.get(0);
        int roll = ThreadLocalRandom.current().nextInt(100);

        // 60% chance for Advancement, 40% for Death
        if (roll < 60) {
            String adv = ADVANCEMENTS.get(ThreadLocalRandom.current().nextInt(ADVANCEMENTS.size()));
            String message = ColorUtils.colorize("&e" + selectedBot.getName() + " has made the advancement &a[" + adv + "]");
            Bukkit.broadcastMessage(message);
            DebugLogger.log(plugin.getLogger(), "Simulator: Broadcasted advancement for %s: %s", selectedBot.getName(), adv);
        } else {
            String template = DEATH_TEMPLATES.get(ThreadLocalRandom.current().nextInt(DEATH_TEMPLATES.size()));
            String message = ColorUtils.colorize(template.replace("%player%", selectedBot.getName()));
            Bukkit.broadcastMessage(message);
            DebugLogger.log(plugin.getLogger(), "Simulator: Broadcasted simulated death for %s", selectedBot.getName());
        }
    }
}