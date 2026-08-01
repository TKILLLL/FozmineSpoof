package org.phantam.fozminesproofcore.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

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
            return;
        }

        try {
            if (Bukkit.getWorld(worldName) != null) {
                plugin.getLogger().log(Level.FINE,
                        "[VoidWorldFactory] World '" + worldName + "' already exists. Skipping creation.");
                return;
            }

            plugin.getLogger().log(Level.INFO,
                    "[VoidWorldFactory] Creating void world: " + worldName);

            WorldCreator creator = new WorldCreator(worldName);
            creator.generator(new VoidChunkGenerator());
            Bukkit.createWorld(creator);

            plugin.getLogger().log(Level.INFO,
                    "[VoidWorldFactory] Void world '" + worldName + "' created successfully.");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[VoidWorldFactory] Failed to create void world '" + worldName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Custom chunk generator that produces only air blocks (void world).
     */
    private static final class VoidChunkGenerator extends ChunkGenerator {

        @Override
        @SuppressWarnings("deprecation")
        public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
            return createChunkData(world);
        }
    }
}