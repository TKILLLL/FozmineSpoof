package org.phantam.fozminespoofcore.utils;

import org.bukkit.Bukkit;
import org.phantam.fozminespoofapi.FozminespoofApi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dynamic class loader and version resolver for NMS bridge modules across 1.19.4 to 26.x.
 */
public final class NMSBridgeLoader {

    private static final Map<String, String> VERSION_MAP = new LinkedHashMap<>();

    static {
        VERSION_MAP.put("1.19.4", "1_19_4");
        VERSION_MAP.put("1.20.1", "1_20_1");
        VERSION_MAP.put("1.20.2", "1_20_2");
        VERSION_MAP.put("1.20.4", "1_20_4");
        VERSION_MAP.put("1.20.6", "1_20_6");
        VERSION_MAP.put("1.21.1", "1_21_1");
        VERSION_MAP.put("1.21.4", "1_21_4");
        VERSION_MAP.put("1.21.11", "1_21_11");
        // Support for 26.x releases
        VERSION_MAP.put("26.0", "1_21_11");
        VERSION_MAP.put("26.1", "1_21_11");
        VERSION_MAP.put("1.26", "1_21_11");
    }

    private NMSBridgeLoader() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static FozminespoofApi loadBridge(Logger logger) {
        String rawVersion = Bukkit.getServer().getMinecraftVersion();
        org.phantam.fozminespoofapi.utils.DebugLogger.log(logger, "NMSBridgeLoader: detected server version '%s'", rawVersion);

        String versionKey = resolveVersionKey(rawVersion);

        if (versionKey == null) {
            logger.log(Level.SEVERE, "[NMSBridgeLoader] Unsupported Minecraft version: " + rawVersion);
            logger.log(Level.SEVERE, "[NMSBridgeLoader] Supported versions: " + VERSION_MAP.keySet());
            return null;
        }

        String className = "org.phantam.fozminespoofv" + versionKey + ".NMSBridge_v" + versionKey;
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            if (instance instanceof FozminespoofApi api) {
                logger.log(Level.INFO, "[NMSBridgeLoader] Successfully loaded bridge for version: " + rawVersion + " (Using " + versionKey + ")");
                return api;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[NMSBridgeLoader] Failed to load bridge " + className + ": " + e.getMessage(), e);
        }
        return null;
    }

    private static String resolveVersionKey(String rawVersion) {
        if (VERSION_MAP.containsKey(rawVersion)) {
            return VERSION_MAP.get(rawVersion);
        }
        if (rawVersion.startsWith("1.19")) return "1_19_4";
        if (rawVersion.startsWith("1.20.1") || rawVersion.equals("1.20")) return "1_20_1";
        if (rawVersion.startsWith("1.20.2") || rawVersion.startsWith("1.20.3")) return "1_20_2";
        if (rawVersion.startsWith("1.20.4") || rawVersion.startsWith("1.20.5")) return "1_20_4";
        if (rawVersion.startsWith("1.20.6")) return "1_20_6";
        if (rawVersion.startsWith("1.21.1") || rawVersion.startsWith("1.21.2") || rawVersion.startsWith("1.21.3") || rawVersion.equals("1.21"))
            return "1_21_1";
        if (rawVersion.startsWith("1.21.4")) return "1_21_4";
        if (rawVersion.startsWith("1.21.")) return "1_21_11";
        // Automatic wildcard resolver for 26.x releases
        if (rawVersion.startsWith("26.") || rawVersion.startsWith("1.26")) return "1_21_11";
        return null;
    }
}