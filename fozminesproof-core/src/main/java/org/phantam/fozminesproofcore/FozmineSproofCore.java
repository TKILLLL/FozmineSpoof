package org.phantam.fozminesproofcore;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofapi.FozminesproofApi;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.chat.ChatScheduler;
import org.phantam.fozminesproofcore.chat.MessageLoader;
import org.phantam.fozminesproofcore.commands.CommandManager;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.database.DatabaseCredentialFactory;
import org.phantam.fozminesproofcore.database.DatabaseManager;
import org.phantam.fozminesproofcore.database.SQLiteDatabaseManager;
import org.phantam.fozminesproofcore.manager.FakePlayerManager;
import org.phantam.fozminesproofcore.papi.FakePlayerPapiExpansion;
import org.phantam.fozminesproofcore.tasks.KeepAliveTask;
import org.phantam.fozminesproofcore.tasks.ProxySyncTask;
import org.phantam.fozminesproofcore.utils.ColorUtils;
import org.phantam.fozminesproofcore.utils.NMSBridgeLoader;
import org.phantam.fozminesproofcore.world.VoidWorldFactory;

import java.io.File;
import java.util.logging.Level;

/**
 * Main plugin class for FozmineSproof.
 * Orchestrates the entire fake player system including database, NMS bridge, chat, and sync tasks.
 */
public class FozmineSproofCore extends JavaPlugin {

    private ConfigManager configManager;
    private FozminesproofApi bridge;
    private IFakePlayerDatabase database;
    private FakePlayerManager fakePlayerManager;
    private MessageLoader messageLoader;
    private ChatScheduler chatScheduler;

    @Override
    public void onEnable() {
        try {
            // 1. Load configuration
            this.saveDefaultConfig();
            this.configManager = new ConfigManager(this);

            // 2. Load NMS bridge for the current server version
            this.bridge = NMSBridgeLoader.loadBridge(this.getLogger());
            if (this.bridge == null) {
                disablePluginDueToError("Failed to load NMS bridge module.");
                return;
            }

            // 3. Initialise database
            this.database = createDatabase();
            this.database.setup();

            // 4. Create fake player manager
            this.fakePlayerManager = new FakePlayerManager(this, this.database);

            // 5. Create void world for NPCs
            VoidWorldFactory.createVoidWorld(this, configManager.getBotWorldName());

            // 6. Set up chat and extensions
            setupChatSystem();
            registerExternalExtensions();

            // 7. Start periodic tasks
            startKeepAliveTask();
            startTabUpdateScheduler();
            startProxySyncTask();

            // 8. Spawn all active bots on startup
            if (this.fakePlayerManager != null) {
                this.fakePlayerManager.spawnAllOnStartup(this.configManager);
            }

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[FozmineSproofCore] Fatal error during enable: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Stop chat scheduler
        if (this.chatScheduler != null) {
            try {
                this.chatScheduler.stop();
            } catch (Exception ignored) {
            }
        }

        // Despawn all bots and close database
        if (this.fakePlayerManager != null) {
            try {
                this.fakePlayerManager.despawnAllOnShutdown();
                if (this.database != null) {
                    try {
                        this.database.close();
                    } catch (Exception ignored) {
                    }
                }
                getLogger().log(Level.INFO, "[FozmineSproofCore] Plugin disabled successfully.");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE,
                        "[FozmineSproofCore] Error during shutdown cleanup: " + e.getMessage(), e);
            }
        }
    }

    // ---- Initialisation helpers ----

    /**
     * Creates the appropriate database implementation based on configuration.
     *
     * @return the database instance
     */
    private IFakePlayerDatabase createDatabase() {
        String tableName = DatabaseCredentialFactory.getSafeTableName(configManager.getRawDatabaseName());

        if (configManager.isDatabaseEnabled()) {
            // Use MySQL
            DatabaseCredentialFactory.DatabaseCredentials credentials =
                    DatabaseCredentialFactory.createCredentials(this);
            return new DatabaseManager(credentials, tableName);
        } else {
            // Use SQLite
            String dbPath = getDataFolder().getAbsolutePath() + File.separator + "fozminesproof.db";
            if (tableName == null || tableName.isEmpty()) {
                tableName = "fozminesproof";
            }
            return new SQLiteDatabaseManager(dbPath, tableName);
        }
    }

    /**
     * Sets up the chat system: loads messages and starts the scheduler.
     */
    private void setupChatSystem() {
        this.messageLoader = new MessageLoader(this);
        this.messageLoader.loadMessages();

        this.chatScheduler = new ChatScheduler(
                this,
                this.fakePlayerManager,
                this.messageLoader,
                this.configManager
        );
        this.chatScheduler.start(this.configManager.getChatConfig());
    }

    /**
     * Registers PlaceholderAPI expansion and command executor.
     */
    private void registerExternalExtensions() {
        // Register /sproof command
        if (getCommand("sproof") != null) {
            CommandManager commandManager = new CommandManager(this);
            getCommand("sproof").setExecutor(commandManager);
            getCommand("sproof").setTabCompleter(commandManager);
        }

        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FakePlayerPapiExpansion(this, this.configManager, this.fakePlayerManager).register();
            getLogger().log(Level.INFO,
                    "[FozmineSproofCore] Linked with PlaceholderAPI successfully.");
        }

        // Register Listner
        getServer().getPluginManager().registerEvents(
                new org.phantam.fozminesproofcore.listener.PluginListInterceptor(configManager, this),
                this
        );
    }

    /**
     * Starts the keep-alive task that refreshes NPC visibility.
     */
    private void startKeepAliveTask() {
        new KeepAliveTask(this).runTaskTimer(this, 100L, 200L);
    }

    /**
     * Starts the tablist update scheduler that refreshes bot names and prefixes.
     */
    private void startTabUpdateScheduler() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (fakePlayerManager == null || fakePlayerManager.getOnlineBotsData().isEmpty()) {
                    return;
                }

                boolean hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
                String rawTabFormat = configManager.getTabFormat();

                fakePlayerManager.getOnlineBotsData().forEach(botData -> {
                    String botName = botData.getName();
                    org.bukkit.entity.Player botEntity = fakePlayerManager.getOnlineBotEntity(botName);
                    if (botEntity == null) return;

                    String formattedTab = rawTabFormat.replace("%fakeplayer_name%", botName);
                    if (hasPapi) {
                        formattedTab = PlaceholderAPI.setPlaceholders(botEntity, formattedTab);
                    }

                    final String finalTabName = formattedTab;

                    Bukkit.getScheduler().runTask(FozmineSproofCore.this, () -> {
                        if (fakePlayerManager.isBotOnline(botName)) {
                            String colored = ColorUtils.colorize(finalTabName);
                            botEntity.setPlayerListName(colored);
                        }
                    });
                });
            }
        }.runTaskTimerAsynchronously(this, 100L, 100L);

        getLogger().log(Level.INFO,
                "[FozmineSproofCore] Tablist synchronisation scheduler enabled.");
    }

    /**
     * Starts the proxy sync task if proxy bridging is enabled.
     */
    private void startProxySyncTask() {
        if (this.configManager.isProxyEnable()) {
            int initialDelaySeconds = this.configManager.getProxyUpdateInterval();
            new ProxySyncTask(this, this.database, this.configManager)
                    .runTaskLaterAsynchronously(this, initialDelaySeconds * 20L);
            getLogger().log(Level.INFO,
                    "[FozmineSproofCore] Proxy synchronisation enabled.");
        } else {
            getLogger().log(Level.INFO,
                    "[FozmineSproofCore] Proxy synchronisation disabled.");
        }
    }

    /**
     * Disables the plugin with an error message.
     *
     * @param reason the error reason
     */
    private void disablePluginDueToError(String reason) {
        getLogger().log(Level.SEVERE, "[FozmineSproofCore] " + reason);
        getLogger().log(Level.SEVERE,
                "[FozmineSproofCore] Plugin will be disabled to prevent data corruption.");
        getServer().getPluginManager().disablePlugin(this);
    }

    // ---- Public accessors ----

    public FozminesproofApi getBridge() {
        return this.bridge;
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    public IFakePlayerDatabase getFakePlayerDatabase() {
        return this.database;
    }

    public FakePlayerManager getFakePlayerManager() {
        return this.fakePlayerManager;
    }

    public MessageLoader getMessageLoader() {
        return this.messageLoader;
    }

    public ChatScheduler getChatScheduler() {
        return this.chatScheduler;
    }
}