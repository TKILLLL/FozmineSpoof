package org.phantam.fozminesproofcore.utils;

import org.bukkit.Bukkit;
import org.phantam.fozminesproofapi.FozminesproofApi;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dynamically loads the appropriate NMS bridge implementation based on the server version.
 * Uses reflection to instantiate version-specific bridge classes at runtime.
 */
public final class NMSBridgeLoader {

    // Version mapping: server version string -> module version key
    private static final Map<String, String> VERSION_MAP = new HashMap<>();

    static {
        VERSION_MAP.put("1.19.4", "1_19_4");
        VERSION_MAP.put("1.20.1", "1_20_1");
        VERSION_MAP.put("1.20.2", "1_20_2");
        VERSION_MAP.put("1.20.4", "1_20_4");
        VERSION_MAP.put("1.20.6", "1_20_6");
        VERSION_MAP.put("1.21.1", "1_21_1");
        VERSION_MAP.put("1.21.4", "1_21_4");
        VERSION_MAP.put("1.21.11", "1_21_11");
    }

    private NMSBridgeLoader() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Loads the NMS bridge implementation for the current server version.
     *
     * @param logger the plugin logger for error reporting
     * @return a FozminesproofApi instance, or null if loading fails
     */
    public static FozminesproofApi loadBridge(Logger logger) {
        String rawVersion = Bukkit.getServer().getMinecraftVersion();
        org.phantam.fozminesproofapi.utils.DebugLogger.log(logger, "NMSBridgeLoader: detected server version '%s'", rawVersion);

        String versionKey = VERSION_MAP.get(rawVersion);

        if (versionKey == null) {
            logger.log(Level.SEVERE,
                    "[NMSBridgeLoader] Unsupported Minecraft version: " + rawVersion);
            logger.log(Level.SEVERE,
                    "[NMSBridgeLoader] Supported versions: " + VERSION_MAP.keySet());
            org.phantam.fozminesproofapi.utils.DebugLogger.log(logger, "NMSBridgeLoader: unsupported version '%s'", rawVersion);
            return null;
        }

        String className = "org.phantam.fozminesproofv" + versionKey + ".NMSBridge_v" + versionKey;
        org.phantam.fozminesproofapi.utils.DebugLogger.log(logger, "NMSBridgeLoader: attempting to load class '%s'", className);

        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            if (instance instanceof FozminesproofApi) {
                logger.log(Level.INFO,
                        "[NMSBridgeLoader] Successfully loaded bridge for version: " + rawVersion);
                org.phantam.fozminesproofapi.utils.DebugLogger.log(logger, "NMSBridgeLoader: successfully loaded bridge for %s", rawVersion);
                return (FozminesproofApi) instance;
            } else {
                logger.log(Level.SEVERE,
                        "[NMSBridgeLoader] Class " + className + " does not implement FozminesproofApi");
                org.phantam.fozminesproofapi.utils.DebugLogger.log(logger, "NMSBridgeLoader: class %s does not implement FozminesproofApi", className);
                return null;
            }

        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE,
                    "[NMSBridgeLoader] Bridge class not found: " + className);
            logger.log(Level.SEVERE,
                    "[NMSBridgeLoader] Ensure the version module is present in the classpath.");
            org.phantam.fozminesproofapi.utils.DebugLogger.log(logger, "NMSBridgeLoader: class not found: %s", className);
            return null;

        } catch (NoSuchMethodException e) {
            logger.log(Level.SEVERE,
                    "[NMSBridgeLoader] No default constructor found in " + className);
            org.phantam.fozminesproofapi.utils.DebugLogger.log(logger, "NMSBridgeLoader: no default constructor in %s", className);
            return null;

        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "[NMSBridgeLoader] Reflection error while loading bridge: " + e.getMessage(), e);
            org.phantam.fozminesproofapi.utils.DebugLogger.log(logger, "NMSBridgeLoader: reflection error: %s", e.getMessage());
            return null;
        }
    }
}