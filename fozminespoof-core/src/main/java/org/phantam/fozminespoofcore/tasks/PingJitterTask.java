package org.phantam.fozminespoofcore.tasks;

import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodically applies natural latency fluctuations (ping jitter) to simulated fake players.
 */
public class PingJitterTask extends BukkitRunnable {

    private final FozmineSpoofCore plugin;
    private final Map<UUID, Integer> basePings = new ConcurrentHashMap<>();

    public PingJitterTask(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.isEnabled() || plugin.getBridge() == null) {
            return;
        }

        var onlineBots = plugin.getFakePlayerManager().getOnlineBotsData();
        if (onlineBots.isEmpty()) {
            basePings.clear();
            return;
        }

        for (FakePlayerData bot : onlineBots) {
            UUID uuid = bot.getUuid();
            // Assign a persistent base ping for each bot session (e.g., 30 - 85ms)
            int basePing = basePings.computeIfAbsent(uuid, k -> ThreadLocalRandom.current().nextInt(30, 85));

            // Apply realistic network jitter (+/- 2 to 7 ms)
            int jitter = ThreadLocalRandom.current().nextInt(-5, 6);
            int currentPing = Math.max(15, basePing + jitter);

            plugin.getBridge().updatePlayerLatency(uuid, currentPing);
        }

        DebugLogger.logFine(plugin.getLogger(), "PingJitterTask: updated latencies for %d active bots", onlineBots.size());
    }

    public void removeBot(UUID uuid) {
        basePings.remove(uuid);
    }
}