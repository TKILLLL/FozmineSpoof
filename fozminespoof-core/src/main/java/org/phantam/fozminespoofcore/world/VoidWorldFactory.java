package org.phantam.fozminespoofcore.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.util.Random;
import java.util.logging.Level;

/**
 * Factory for creating an empty (void) world where fake players can be spawned.
 * This world has no blocks or terrain, reducing server load for NPC operations.
 */
public final class VoidWorldFactory {

    private VoidWorldFactory() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a void world with the given name if it does not already exist.
     * Uses a custom chunk generator that produces only air blocks.
     *
     * @param plugin    the plugin instance for logging
     * @param worldName the name of the world to create
     */
    public static void createVoidWorld(JavaPlugin plugin, String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            DebugLogger.log(plugin.getLogger(), "VoidWorldFactory: worldName is null or empty, skipping");
            return;
        }

        DebugLogger.log(plugin.getLogger(), "VoidWorldFactory: createVoidWorld('%s') called", worldName);

        try {
            if (Bukkit.getWorld(worldName) != null) {
                plugin.getLogger().log(Level.FINE,
                        "[VoidWorldFactory] World '" + worldName + "' already exists. Skipping creation.");
                DebugLogger.log(plugin.getLogger(), "VoidWorldFactory: world '%s' already exists", worldName);
                return;
            }

            plugin.getLogger().log(Level.INFO,
                    "[VoidWorldFactory] Creating void world: " + worldName);
            DebugLogger.log(plugin.getLogger(), "VoidWorldFactory: creating void world '%s'", worldName);

            WorldCreator creator = new WorldCreator(worldName);
            creator.generator(new VoidChunkGenerator());
            World createdWorld = Bukkit.createWorld(creator);

            if (createdWorld != null) {
                plugin.getLogger().log(Level.INFO,
                        "[VoidWorldFactory] Void world '" + worldName + "' created successfully.");
                DebugLogger.log(plugin.getLogger(), "VoidWorldFactory: void world '%s' created successfully", worldName);
            } else {
                plugin.getLogger().log(Level.WARNING,
                        "[VoidWorldFactory] Void world '" + worldName + "' creation returned null.");
                DebugLogger.log(plugin.getLogger(), "VoidWorldFactory: world creation returned null for '%s'", worldName);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[VoidWorldFactory] Failed to create void world '" + worldName + "': " + e.getMessage(), e);
            DebugLogger.log(plugin.getLogger(), "VoidWorldFactory: exception creating world '%s': %s",
                    worldName, e.getMessage());
        }
    }

    /**
     * Custom chunk generator that produces only air blocks (void world).
     */
    private static final class VoidChunkGenerator extends ChunkGenerator {

        @Override
        @SuppressWarnings("deprecation")
        public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
            // This method always returns an empty chunk (air blocks only)
            return createChunkData(world);
        }
    }
}