package org.phantam.fozminesproofcore.database;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofcore.utils.DebugLogger;

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
            DebugLogger.log(JavaPlugin.getPlugin(JavaPlugin.class).getLogger(),
                    "DatabaseCredentialFactory: raw name null/empty, using default 'fozminesproof'");
            return "fozminesproof";
        }
        String sanitized = rawName.replaceAll("[^a-zA-Z0-9_]", "");
        if (!sanitized.equals(rawName)) {
            DebugLogger.log(JavaPlugin.getPlugin(JavaPlugin.class).getLogger(),
                    "DatabaseCredentialFactory: sanitized table name '%s' from '%s'", sanitized, rawName);
        }
        return sanitized;
    }

    /**
     * Creates database credentials from the plugin's configuration.
     *
     * @param plugin the plugin instance
     * @return a DatabaseCredentials record
     */
    public static DatabaseCredentials createCredentials(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("Database.host", "localhost");
        int port = config.getInt("Database.port", 3306);
        String database = config.getString("Database.database", "minecraft");
        String user = config.getString("Database.user", "root");
        // password not logged for security

        DebugLogger.log(plugin.getLogger(),
                "DatabaseCredentialFactory: credentials: host=%s, port=%d, database=%s, user=%s",
                host, port, database, user);

        return new DatabaseCredentials(host, port, database, user, config.getString("Database.password", ""));
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