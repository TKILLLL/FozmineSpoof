package org.phantam.fozminesproofcore.factory;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Random;

public final class VoidWorldFactory {

    private VoidWorldFactory() {
        throw new UnsupportedOperationException("Factory class");
    }

    /**
     * Kỹ thuật khởi tạo thế giới trống rỗng (Void World) biệt lập
     */
    public static void createVoidWorld(JavaPlugin plugin, String worldName) {
        if (worldName == null || worldName.isEmpty()) return;

        try {
            if (Bukkit.getWorld(worldName) != null) return;

            plugin.getLogger().info("⚙ Đang thiết lập thế giới Void chuyên dụng: " + worldName);

            WorldCreator creator = new WorldCreator(worldName);
            creator.generator(new VoidChunkGenerator());
            Bukkit.createWorld(creator);

            plugin.getLogger().info("✅ Thế giới Void '" + worldName + "' đã được khởi tạo!");
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Lỗi khởi tạo thế giới trống '" + worldName + "': " + e.getMessage());
        }
    }

    private static class VoidChunkGenerator extends ChunkGenerator {
        @Override
        @SuppressWarnings("deprecation")
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }
    }
}
