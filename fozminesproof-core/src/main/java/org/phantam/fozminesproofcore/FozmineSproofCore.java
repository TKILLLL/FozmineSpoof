package org.phantam.fozminesproofcore;

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
import org.phantam.fozminesproofcore.tasks.KeepAliveTask;
import org.phantam.fozminesproofcore.tasks.ProxySyncTask;
import org.phantam.fozminesproofcore.utils.ColorUtils;
import org.phantam.fozminesproofcore.utils.DebugLogger;
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
        DebugLogger.log(getLogger(), "FozmineSproofCore: onEnable() starting");

        try {
            // 1. Load configuration
            DebugLogger.log(getLogger(), "FozmineSproofCore: loading configuration");
            this.saveDefaultConfig();
            this.configManager = new ConfigManager(this);
            DebugLogger.setDebugEnabled(configManager.isDebug());
            DebugLogger.log(getLogger(), "FozmineSproofCore: config loaded, debug=%s", configManager.isDebug());

            // 2. Load NMS bridge for the current server version
            DebugLogger.log(getLogger(), "FozmineSproofCore: loading NMS bridge");
            this.bridge = NMSBridgeLoader.loadBridge(this.getLogger());
            if (this.bridge == null) {
                disablePluginDueToError("Failed to load NMS bridge module.");
                return;
            }
            DebugLogger.log(getLogger(), "FozmineSproofCore: NMS bridge loaded successfully");

            // 3. Initialise database
            DebugLogger.log(getLogger(), "FozmineSproofCore: initializing database");
            this.database = createDatabase();
            this.database.setup();
            DebugLogger.log(getLogger(), "FozmineSproofCore: database initialized");

            // 4. Create fake player manager
            DebugLogger.log(getLogger(), "FozmineSproofCore: creating FakePlayerManager");
            this.fakePlayerManager = new FakePlayerManager(this, this.database);
            DebugLogger.log(getLogger(), "FozmineSproofCore: FakePlayerManager created");

            // 5. Create void world for NPCs
            DebugLogger.log(getLogger(), "FozmineSproofCore: creating void world");
            VoidWorldFactory.createVoidWorld(this, configManager.getBotWorldName());
            DebugLogger.log(getLogger(), "FozmineSproofCore: void world creation completed");

            // 6. Set up chat and extensions
            DebugLogger.log(getLogger(), "FozmineSproofCore: setting up chat system");
            setupChatSystem();
            DebugLogger.log(getLogger(), "FozmineSproofCore: chat system setup completed");

            DebugLogger.log(getLogger(), "FozmineSproofCore: registering external extensions");
            registerExternalExtensions();
            DebugLogger.log(getLogger(), "FozmineSproofCore: external extensions registered");

            // 7. Start periodic tasks
            DebugLogger.log(getLogger(), "FozmineSproofCore: starting periodic tasks");
            startKeepAliveTask();
            startTabUpdateScheduler();
            startProxySyncTask();
            DebugLogger.log(getLogger(), "FozmineSproofCore: periodic tasks started");

            // 8. Spawn all active bots on startup
            if (this.fakePlayerManager != null) {
                DebugLogger.log(getLogger(), "FozmineSproofCore: spawning bots on startup");
                this.fakePlayerManager.spawnAllOnStartup(this.configManager);
                DebugLogger.log(getLogger(), "FozmineSproofCore: startup spawn completed");
            }

            DebugLogger.log(getLogger(), "FozmineSproofCore: onEnable() completed successfully");

        } catch (Exception e) {
            DebugLogger.log(getLogger(), "FozmineSproofCore: fatal error during enable: %s", e.getMessage());
            getLogger().log(Level.SEVERE, "[FozmineSproofCore] Fatal error during enable: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        DebugLogger.log(getLogger(), "FozmineSproofCore: onDisable() starting");

        // Stop chat scheduler
        if (this.chatScheduler != null) {
            DebugLogger.log(getLogger(), "FozmineSproofCore: stopping chat scheduler");
            try {
                this.chatScheduler.stop();
                DebugLogger.log(getLogger(), "FozmineSproofCore: chat scheduler stopped");
            } catch (Exception e) {
                DebugLogger.log(getLogger(), "FozmineSproofCore: error stopping chat scheduler: %s", e.getMessage());
            }
        }

        // Despawn all bots and close database
        if (this.fakePlayerManager != null) {
            DebugLogger.log(getLogger(), "FozmineSproofCore: despanwing all bots on shutdown");
            try {
                this.fakePlayerManager.despawnAllOnShutdown();
                DebugLogger.log(getLogger(), "FozmineSproofCore: bots despawned");
                if (this.database != null) {
                    DebugLogger.log(getLogger(), "FozmineSproofCore: closing database");
                    try {
                        this.database.close();
                        DebugLogger.log(getLogger(), "FozmineSproofCore: database closed");
                    } catch (Exception ignored) {
                        DebugLogger.log(getLogger(), "FozmineSproofCore: error closing database (ignored)");
                    }
                }
                getLogger().log(Level.INFO, "[FozmineSproofCore] Plugin disabled successfully.");
                DebugLogger.log(getLogger(), "FozmineSproofCore: plugin disabled successfully");
            } catch (Exception e) {
                DebugLogger.log(getLogger(), "FozmineSproofCore: error during shutdown cleanup: %s", e.getMessage());
                getLogger().log(Level.SEVERE,
                        "[FozmineSproofCore] Error during shutdown cleanup: " + e.getMessage(), e);
            }
        } else {
            DebugLogger.log(getLogger(), "FozmineSproofCore: fakePlayerManager is null, skipping shutdown cleanup");
        }

        DebugLogger.log(getLogger(), "FozmineSproofCore: onDisable() completed");
    }

    // ---- Initialisation helpers ----

    /**
     * Creates the appropriate database implementation based on configuration.
     *
     * @return the database instance
     */
    private IFakePlayerDatabase createDatabase() {
        DebugLogger.log(getLogger(), "FozmineSproofCore: createDatabase() called");
        String tableName = DatabaseCredentialFactory.getSafeTableName(configManager.getRawDatabaseName());
        DebugLogger.logFine(getLogger(), "FozmineSproofCore: tableName='%s'", tableName);

        if (configManager.isDatabaseEnabled()) {
            DebugLogger.log(getLogger(), "FozmineSproofCore: using MySQL database");
            // Use MySQL
            DatabaseCredentialFactory.DatabaseCredentials credentials =
                    DatabaseCredentialFactory.createCredentials(this);
            return new DatabaseManager(credentials, tableName);
        } else {
            DebugLogger.log(getLogger(), "FozmineSproofCore: using SQLite database");
            // Use SQLite
            String dbPath = getDataFolder().getAbsolutePath() + File.separator + "fozminesproof.db";
            if (tableName == null || tableName.isEmpty()) {
                tableName = "fozminesproof";
                DebugLogger.logFine(getLogger(), "FozmineSproofCore: tableName empty, set to 'fozminesproof'");
            }
            return new SQLiteDatabaseManager(dbPath, tableName);
        }
    }

    /**
     * Sets up the chat system: loads messages and starts the scheduler.
     */
    private void setupChatSystem() {
        DebugLogger.log(getLogger(), "FozmineSproofCore: setupChatSystem()");
        this.messageLoader = new MessageLoader(this);
        this.messageLoader.loadMessages();
        DebugLogger.logFine(getLogger(), "FozmineSproofCore: messages loaded");

        this.chatScheduler = new ChatScheduler(
                this,
                this.fakePlayerManager,
                this.messageLoader,
                this.configManager
        );
        this.chatScheduler.start(this.configManager.getChatConfig());
        DebugLogger.log(getLogger(), "FozmineSproofCore: chat scheduler started");
    }

    /**
     * Registers PlaceholderAPI expansion and command executor.
     */
    private void registerExternalExtensions() {
        DebugLogger.log(getLogger(), "FozmineSproofCore: registerExternalExtensions()");

        // Register /sproof command
        if (getCommand("sproof") != null) {
            DebugLogger.log(getLogger(), "FozmineSproofCore: registering command executor");
            CommandManager commandManager = new CommandManager(this);
            getCommand("sproof").setExecutor(commandManager);
            getCommand("sproof").setTabCompleter(commandManager);
            DebugLogger.logFine(getLogger(), "FozmineSproofCore: command executor registered");
        } else {
            DebugLogger.log(getLogger(), "FozmineSproofCore: command 'sproof' not found, skipping executor");
        }

        // Register Listner
        DebugLogger.log(getLogger(), "FozmineSproofCore: registering PluginListInterceptor");
        getServer().getPluginManager().registerEvents(
                new org.phantam.fozminesproofcore.listener.PluginListInterceptor(configManager, this),
                this
        );
        DebugLogger.logFine(getLogger(), "FozmineSproofCore: PluginListInterceptor registered");
    }

    /**
     * Starts the keep-alive task that refreshes NPC visibility.
     */
    private void startKeepAliveTask() {
        DebugLogger.log(getLogger(), "FozmineSproofCore: starting KeepAliveTask");
        new KeepAliveTask(this).runTaskTimer(this, 100L, 200L);
        DebugLogger.logFine(getLogger(), "FozmineSproofCore: KeepAliveTask started (period: 200 ticks)");
    }

    /**
     * Starts the tablist update scheduler that refreshes bot names and prefixes.
     */
    /**
     * Starts the tablist update scheduler that refreshes bot names and prefixes.
     * Now simply sets the bot's name on the tablist without formatting or PlaceholderAPI.
     */
    private void startTabUpdateScheduler() {
        DebugLogger.log(getLogger(), "FozmineSproofCore: starting tab update scheduler");
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (fakePlayerManager == null || fakePlayerManager.getOnlineBotsData().isEmpty()) {
                    return;
                }

                fakePlayerManager.getOnlineBotsData().forEach(botData -> {
                    String botName = botData.getName();
                    org.bukkit.entity.Player botEntity = fakePlayerManager.getOnlineBotEntity(botName);
                    if (botEntity == null) return;

                    String finalTabName = botName;

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
        DebugLogger.log(getLogger(), "FozmineSproofCore: tab update scheduler started (period: 100 ticks)");
    }

    /**
     * Starts the proxy sync task if proxy bridging is enabled.
     */
    private void startProxySyncTask() {
        DebugLogger.log(getLogger(), "FozmineSproofCore: startProxySyncTask()");
        if (this.configManager.isProxyEnable()) {
            int initialDelaySeconds = this.configManager.getProxyUpdateInterval();
            DebugLogger.log(getLogger(), "FozmineSproofCore: proxy enabled, initial delay=%d seconds", initialDelaySeconds);
            new ProxySyncTask(this, this.database, this.configManager)
                    .runTaskLaterAsynchronously(this, initialDelaySeconds * 20L);
            getLogger().log(Level.INFO,
                    "[FozmineSproofCore] Proxy synchronisation enabled.");
            DebugLogger.log(getLogger(), "FozmineSproofCore: proxy sync task started");
        } else {
            getLogger().log(Level.INFO,
                    "[FozmineSproofCore] Proxy synchronisation disabled.");
            DebugLogger.log(getLogger(), "FozmineSproofCore: proxy disabled, skipping sync task");
        }
    }

    /**
     * Disables the plugin with an error message.
     *
     * @param reason the error reason
     */
    private void disablePluginDueToError(String reason) {
        DebugLogger.log(getLogger(), "FozmineSproofCore: disablePluginDueToError('%s')", reason);
        getLogger().log(Level.SEVERE, "[FozmineSproofCore] " + reason);
        getLogger().log(Level.SEVERE,
                "[FozmineSproofCore] Plugin will be disabled to prevent data corruption.");
        getServer().getPluginManager().disablePlugin(this);
        DebugLogger.log(getLogger(), "FozmineSproofCore: plugin disabled due to error");
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