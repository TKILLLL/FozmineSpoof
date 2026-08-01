package org.phantam.fozminesproofcore.database;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DatabaseCredentialFactory {

    private DatabaseCredentialFactory() {
        throw new UnsupportedOperationException("Factory class");
    }

    public static String getSafeTableName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "fozminesproof";
        return rawName.replaceAll("[^a-zA-Z0-9_]", "");
    }

    public static DatabaseCredentials createCredentials(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new DatabaseCredentials(
                config.getString("Database.host", "localhost"),
                config.getInt("Database.port", 3306),
                config.getString("Database.database", "minecraft"),
                config.getString("Database.user", "root"),
                config.getString("Database.password", "")
        );
    }

    public static record DatabaseCredentials(String host, int port, String database, String user, String password) {}
}