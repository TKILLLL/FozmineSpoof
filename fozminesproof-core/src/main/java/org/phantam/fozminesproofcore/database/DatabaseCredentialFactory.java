package org.phantam.fozminesproofcore.database;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Factory for creating database credentials and sanitising table names.
 */
public final class DatabaseCredentialFactory {

    private DatabaseCredentialFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Sanitises a table name to contain only alphanumeric characters and underscores.
     *
     * @param rawName raw table name
     * @return sanitised table name, or "fozminesproof" if null/empty
     */
    public static String getSafeTableName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "fozminesproof";
        }
        return rawName.replaceAll("[^a-zA-Z0-9_]", "");
    }

    /**
     * Creates database credentials from the plugin's configuration.
     *
     * @param plugin the plugin instance
     * @return a DatabaseCredentials record
     */
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

    /**
     * Data record for database connection credentials.
     *
     * @param host     database host
     * @param port     database port
     * @param database database name
     * @param user     username
     * @param password password
     */
    public record DatabaseCredentials(String host, int port, String database, String user, String password) {}
}