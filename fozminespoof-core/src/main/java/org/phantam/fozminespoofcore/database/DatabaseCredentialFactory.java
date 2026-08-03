package org.phantam.fozminespoofcore.database;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminespoofapi.utils.DebugLogger;

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
     * @return sanitised table name, or "fozminespoof" if null/empty
     */
    public static String getSafeTableName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "fozminespoof";
        }
        String sanitized = rawName.replaceAll("[^a-zA-Z0-9_]", "");
        // No debug log here to avoid classloader issues.
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
    public record DatabaseCredentials(String host, int port, String database, String user, String password) {
    }
}