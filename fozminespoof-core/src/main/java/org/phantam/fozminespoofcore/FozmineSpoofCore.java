package org.phantam.fozminespoofcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.phantam.fozminespoofapi.FozminespoofApi;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.chat.*;
import org.phantam.fozminespoofcore.chat.ai.AiChatProcessor;
import org.phantam.fozminespoofcore.chat.ai.AiPersonalityManager;
import org.phantam.fozminespoofcore.commands.CommandManager;
import org.phantam.fozminespoofcore.config.AiConfig;
import org.phantam.fozminespoofcore.config.ConfigManager;
import org.phantam.fozminespoofcore.config.InteractiveMessageConfig;
import org.phantam.fozminespoofcore.config.JoinMessageConfig;
import org.phantam.fozminespoofcore.database.DatabaseCredentialFactory;
import org.phantam.fozminespoofcore.database.DatabaseManager;
import org.phantam.fozminespoofcore.database.SQLiteDatabaseManager;
import org.phantam.fozminespoofcore.listener.AiChatListener;
import org.phantam.fozminespoofcore.listener.BotJoinQuitListener;
import org.phantam.fozminespoofcore.listener.InteractiveChatListener;
import org.phantam.fozminespoofcore.listener.PluginListInterceptor;
import org.phantam.fozminespoofcore.manager.BotLifecycleManager;
import org.phantam.fozminespoofcore.manager.FakePlayerManager;
import org.phantam.fozminespoofcore.manager.RankWeightManager;
import org.phantam.fozminespoofcore.tasks.KeepAliveTask;
import org.phantam.fozminespoofcore.tasks.ProxySyncTask;
import org.phantam.fozminespoofcore.utils.ColorUtils;
import org.phantam.fozminespoofcore.utils.CryptoUtils;
import org.phantam.fozminespoofcore.utils.NMSBridgeLoader;
import org.phantam.fozminespoofcore.utils.RsaVerifier;
import org.phantam.fozminespoofcore.world.VoidWorldFactory;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;

public class FozmineSpoofCore extends JavaPlugin {

    private static final String ENCRYPTED_LICENSE_URL = "LhsOHRpUSnwcBgwDPRYGXwQEIkgJWlFcQicCVBsMHAY2HEEOFiNKAgIMWygNHFFb";

    private ConfigManager configManager;
    private FozminespoofApi bridge;
    private IFakePlayerDatabase database;
    private FakePlayerManager fakePlayerManager;
    private MessageLoader messageLoader;
    private ChatScheduler chatScheduler;
    private BotLifecycleManager botLifecycleManager;
    private RankWeightManager rankWeightManager;
    private JoinMessageConfig joinMessageConfig;
    private JoinChatProcessor joinChatProcessor;
    private InteractiveMessageConfig interactiveMessageConfig;
    private AiConfig aiConfig;
    private AiPersonalityManager aiPersonalityManager;
    private AiChatProcessor aiChatProcessor;

    private BukkitTask keepAliveTaskHandle;
    private BukkitTask tabUpdateTaskHandle;
    private BukkitTask heartbeatTaskHandle;

    @Override
    public void onEnable() {
        printStartupBanner();
        DebugLogger.log(getLogger(), "FozmineSpoofCore: onEnable() starting");

        try {
            logConsole("&#00F2FE[1/8] &#3B82F6Loading configuration engine...");
            this.saveDefaultConfig();
            this.configManager = new ConfigManager(this);
            DebugLogger.setDebugEnabled(configManager.isDebug());
            logConsole("&#00F2FE[1/8] &#10B981Configuration loaded! Debug Mode: " + (configManager.isDebug() ? "&#F59E0BENABLED" : "&#9CA3AFDISABLED"));

            String licenseKey = getConfig().getString("license-key", "").trim();

            if (licenseKey.isEmpty() || licenseKey.equalsIgnoreCase("YOUR_LICENSE_KEY_HERE")) {
                logConsole("&#EF4444==========================================================");
                logConsole("&#EF4444  LICENSE ERROR: License key is missing in config.yml!");
                logConsole("&#EF4444  Please enter a valid license key and restart the server.");
                logConsole("&#EF4444==========================================================");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            logConsole("&#00F2FE[License] &#3B82F6Verifying license key...");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> verifyLicenseAsync(licenseKey, true));

        } catch (Exception e) {
            DebugLogger.log(getLogger(), "FozmineSpoofCore: fatal error during enable: %s", e.getMessage());
            logConsole("&#EF4444✖ Fatal exception caught during startup procedure!");
            getLogger().log(Level.SEVERE, "[FozmineSpoofCore] Details: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void verifyLicenseAsync(String licenseKey, boolean isInitialStart) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        try {
            String serverIP = fetchPublicServerIp(client);

            String rawUrl = CryptoUtils.decrypt(ENCRYPTED_LICENSE_URL);
            if (rawUrl.isEmpty()) {
                rawUrl = "https://license-api-phantam.vercel.app/api/check"; // Fallback an toàn
            }

            String encodedKey = URLEncoder.encode(licenseKey, StandardCharsets.UTF_8);
            String encodedIp = URLEncoder.encode(serverIP, StandardCharsets.UTF_8);

            String delimiter = rawUrl.contains("?") ? "&" : "?";
            String apiUrl = rawUrl + delimiter + "key=" + encodedKey + "&ip=" + encodedIp;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", "FozmineSpoof-Plugin/2.0")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = (response.body() != null) ? response.body().trim() : "";

            Bukkit.getScheduler().runTask(this, () -> processSignedLicenseResponse(statusCode, responseBody, licenseKey, isInitialStart));

        } catch (Exception e) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (isInitialStart) {
                    logConsole("&#EF4444[License] Could not connect to verification server!");
                    disablePluginDueToError("License server connection timed out or offline.");
                }
            });
        }
    }

    private void processSignedLicenseResponse(int statusCode, String responseBody, String licenseKey, boolean isInitialStart) {
        if (statusCode != 200 || responseBody.isEmpty()) {
            if (isInitialStart) {
                logConsole("&#EF4444[License] HTTP Error " + statusCode + " from License Server!");
                disablePluginDueToError("HTTP " + statusCode + " error from license server.");
            }
            return;
        }

        // Kiểm tra response hợp lệ (JSON)
        if (!responseBody.trim().startsWith("{")) {
            logConsole("&#EF4444[License] Invalid response format from license server.");
            disablePluginDueToError("Invalid response from license server.");
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            String payload = json.get("payload").getAsString();
            String signature = json.get("signature").getAsString();

            boolean isAuthentic = RsaVerifier.verify(payload, signature);
            if (!isAuthentic) {
                logConsole("&#EF4444[License Security] Invalid RSA signature! Tampering detected.");
                disablePluginDueToError("Tampered or forged license response.");
                return;
            }

            JsonObject payloadObj = JsonParser.parseString(payload).getAsJsonObject();
            String status = payloadObj.get("status").getAsString();
            long timestamp = payloadObj.get("timestamp").getAsLong();

            if (Math.abs(System.currentTimeMillis() - timestamp) > 300000) {
                disablePluginDueToError("License response timestamp expired (replay attack prevention).");
                return;
            }

            if ("VALID".equalsIgnoreCase(status)) {
                if (isInitialStart) {
                    logConsole("&#10B981[License] ✔ RSA License verified successfully! Status: VALID");
                    completePluginInitialization();
                    startHeartbeatScheduler(licenseKey);
                }
            } else if ("INVALID".equalsIgnoreCase(status)) {
                logConsole("Invalid License Key! Disabling plugin...");
                disablePluginDueToError("Invalid License Key!");
            } else if ("KEY_EXPIRED".equalsIgnoreCase(status)) {
                logConsole("This license key has expired! Disabling plugin...");
                disablePluginDueToError("License key expired!");
            } else if ("KEY_SHARING_DETECTED".equalsIgnoreCase(status)) {
                logConsole("This license key is being used on another server IP! Disabling plugin...");
                disablePluginDueToError("This license key is being used on another server IP!");
            } else {
                logConsole("&#EF4444[License] Verification failed.");
                disablePluginDueToError("License verification failed.");
            }

        } catch (Exception e) {
            logConsole("&#EF4444[License] Failed to process license server response.");
            disablePluginDueToError("Invalid license response payload.");
        }
    }

    private void startHeartbeatScheduler(String licenseKey) {
        if (heartbeatTaskHandle != null) {
            heartbeatTaskHandle.cancel();
        }

        heartbeatTaskHandle = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            verifyLicenseAsync(licenseKey, false);
        }, 36000L, 36000L);
    }

    private String fetchPublicServerIp(HttpClient client) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.ipify.org"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                return response.body().trim();
            }
        } catch (Exception ignored) {
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://checkip.amazonaws.com"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                return response.body().trim();
            }
        } catch (Exception ignored) {
        }

        String bukkitIp = Bukkit.getIp();
        return (bukkitIp != null && !bukkitIp.isBlank()) ? bukkitIp : "127.0.0.1";
    }

    private void completePluginInitialization() {
        try {
            logConsole("&#00F2FE[2/8] &#3B82F6Detecting server NMS architecture...");
            this.bridge = NMSBridgeLoader.loadBridge(this.getLogger());
            if (this.bridge == null) {
                disablePluginDueToError("Failed to initialize NMS Bridge module for this Minecraft version.");
                return;
            }
            logConsole("&#00F2FE[2/8] &#10B981NMS Core Bridge hooked successfully.");

            logConsole("&#00F2FE[3/8] &#3B82F6Connecting to storage engine...");
            this.database = createDatabase();
            this.database.setup();
            logConsole("&#00F2FE[3/8] &#10B981Database storage engine initialized and ready.");

            logConsole("&#00F2FE[4/8] &#3B82F6Initializing FakePlayer Registry, Lifecycle & Rank Weight Manager...");
            this.fakePlayerManager = new FakePlayerManager(this, this.database);
            this.botLifecycleManager = new BotLifecycleManager(this, this.fakePlayerManager, this.configManager);
            this.fakePlayerManager.setLifecycleManager(this.botLifecycleManager);
            this.rankWeightManager = new RankWeightManager(this);
            logConsole("&#00F2FE[4/8] &#10B981Manager subsystem linked.");

            logConsole("&#00F2FE[5/8] &#3B82F6Checking isolated Bot World environment...");
            VoidWorldFactory.createVoidWorld(this, configManager.getBotWorldName());
            logConsole("&#00F2FE[5/8] &#10B981Isolated Void World '" + configManager.getBotWorldName() + "' confirmed.");

            logConsole("&#00F2FE[6/8] &#3B82F6Setting up AI Chat Simulation Engine & Interceptors...");
            setupChatSystem();
            registerExternalExtensions();
            logConsole("&#00F2FE[6/8] &#10B981AI Chat Engine & Commands registered.");

            logConsole("&#00F2FE[7/8] &#3B82F6Starting KeepAlive, Tablist Sync, and Proxy Schedulers...");
            startKeepAliveTask();
            startTabUpdateScheduler();

            if (configManager.isDatabaseEnabled() && Boolean.TRUE.equals(configManager.isProxyEnable())) {
                startProxySyncTask();
                logConsole("&#00F2FE[Proxy Bridge] &#10B981Proxy synchronization bridge successfully activated.");
            } else {
                logConsole("&#00F2FE[Proxy Bridge] &#9CA3AFProxy synchronization bridge is disabled (Database or Proxy bridge turned off).");
            }

            logConsole("&#00F2FE[7/8] &#10B981Background schedulers started.");

            if (this.botLifecycleManager != null) {
                logConsole("&#00F2FE[8/8] &#3B82F6Pre-populating database & spawning baseline bot allocation...");
                this.botLifecycleManager.initializeAndSpawn();
            }

            logConsole("&#10B981✔ FozmineSpoof System successfully enabled and fully operational!");

        } catch (Exception e) {
            DebugLogger.log(getLogger(), "FozmineSpoofCore: fatal error during feature load: %s", e.getMessage());
            logConsole("&#EF4444✖ Fatal exception caught during feature initialization!");
            getLogger().log(Level.SEVERE, "[FozmineSpoofCore] Details: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        logConsole("&#F59E0B⚡ Gracefully shutting down FozmineSpoof core subsystems...");
        DebugLogger.log(getLogger(), "FozmineSpoofCore: onDisable() starting");

        if (keepAliveTaskHandle != null) {
            keepAliveTaskHandle.cancel();
            keepAliveTaskHandle = null;
        }
        if (tabUpdateTaskHandle != null) {
            tabUpdateTaskHandle.cancel();
            tabUpdateTaskHandle = null;
        }
        if (heartbeatTaskHandle != null) {
            heartbeatTaskHandle.cancel();
            heartbeatTaskHandle = null;
        }

        if (this.chatScheduler != null) {
            try {
                this.chatScheduler.stop();
                logConsole("&#00F2FE  ▪ &#9CA3AFAI Chat Scheduler stopped.");
            } catch (Exception e) {
                DebugLogger.log(getLogger(), "FozmineSpoofCore: error stopping chat scheduler: %s", e.getMessage());
            }
        }

        if (this.fakePlayerManager != null) {
            try {
                this.fakePlayerManager.despawnAllOnShutdown();
                logConsole("&#00F2FE  ▪ &#10B981All active fake player entities despawned safely.");

                if (this.database != null) {
                    try {
                        this.database.close();
                        logConsole("&#00F2FE  ▪ &#10B981Database connection safely closed.");
                    } catch (Exception ignored) {
                    }
                }
                logConsole("&#EF4444✖ FozmineSpoof disabled successfully.");
            } catch (Exception e) {
                logConsole("&#EF4444✖ Exception caught during shutdown cleanup procedure!");
                getLogger().log(Level.SEVERE, "[FozmineSpoofCore] Details: " + e.getMessage(), e);
            }
        }

        DebugLogger.log(getLogger(), "FozmineSpoofCore: onDisable() completed");
    }

    // ---- Helper Methods ----

    private IFakePlayerDatabase createDatabase() {
        String tableName = DatabaseCredentialFactory.getSafeTableName(configManager.getRawDatabaseName());

        if (configManager.isDatabaseEnabled()) {
            logConsole("&#00F2FE  ▪ &#3B82F6Storage Engine: &#F59E0BMySQL Remote Database");
            DatabaseCredentialFactory.DatabaseCredentials credentials = DatabaseCredentialFactory.createCredentials(this);
            return new DatabaseManager(credentials, tableName);
        } else {
            logConsole("&#00F2FE  ▪ &#3B82F6Storage Engine: &#10B981SQLite Local Database (fozminespoof.db)");
            String dbPath = getDataFolder().getAbsolutePath() + File.separator + "fozminespoof.db";
            if (tableName == null || tableName.isEmpty()) {
                tableName = "fozminespoof";
            }
            return new SQLiteDatabaseManager(dbPath, tableName);
        }
    }

    private void setupChatSystem() {
        this.messageLoader = new MessageLoader(this);
        this.messageLoader.loadMessages();

        this.joinMessageConfig = new JoinMessageConfig(this);
        this.interactiveMessageConfig = new InteractiveMessageConfig(this);

        this.aiConfig = new AiConfig(this);
        this.aiPersonalityManager = new AiPersonalityManager(this);
        this.aiChatProcessor = new AiChatProcessor(this, this.aiConfig, this.aiPersonalityManager);

        this.chatScheduler = new ChatScheduler(
                this,
                this.fakePlayerManager,
                this.messageLoader,
                this.configManager
        );
        this.chatScheduler.start(this.configManager.getChatConfig());

        this.joinChatProcessor = new JoinChatProcessor(
                this,
                new BotSelector(this.fakePlayerManager, getLogger()),
                new BotChatProcessor(this, this.fakePlayerManager, this.configManager)
        );

        InteractiveChatListener interactiveListener = new InteractiveChatListener(
                this,
                new BotSelector(this.fakePlayerManager, getLogger()),
                new BotChatProcessor(this, this.fakePlayerManager, this.configManager)
        );
        getServer().getPluginManager().registerEvents(interactiveListener, this);

        AiChatListener aiListener = new AiChatListener(
                this,
                this.aiConfig,
                this.aiChatProcessor,
                new BotSelector(this.fakePlayerManager, getLogger())
        );
        getServer().getPluginManager().registerEvents(aiListener, this);
    }

    private void registerExternalExtensions() {
        if (getCommand("spoof") != null) {
            CommandManager commandManager = new CommandManager(this);
            getCommand("spoof").setExecutor(commandManager);
            getCommand("spoof").setTabCompleter(commandManager);
        }

        getServer().getPluginManager().registerEvents(new PluginListInterceptor(configManager, this), this);
        getServer().getPluginManager().registerEvents(new BotJoinQuitListener(this), this);
    }

    private void startKeepAliveTask() {
        if (keepAliveTaskHandle != null) {
            keepAliveTaskHandle.cancel();
        }
        keepAliveTaskHandle = new KeepAliveTask(this).runTaskTimer(this, 100L, 200L);
    }

    private void startTabUpdateScheduler() {
        if (tabUpdateTaskHandle != null) {
            tabUpdateTaskHandle.cancel();
        }
        tabUpdateTaskHandle = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (fakePlayerManager == null || fakePlayerManager.getOnlineBotsData().isEmpty()) {
                    return;
                }

                fakePlayerManager.getOnlineBotsData().forEach(botData -> {
                    String botName = botData.getName();
                    org.bukkit.entity.Player botEntity = fakePlayerManager.getOnlineBotEntity(botName);
                    if (botEntity == null) return;

                    Bukkit.getScheduler().runTask(FozmineSpoofCore.this, () -> {
                        if (fakePlayerManager.isBotOnline(botName)) {
                            botEntity.setPlayerListName(ColorUtils.colorize(botName));
                        }
                    });
                });
            }
        }.runTaskTimerAsynchronously(this, 100L, 100L);
    }

    private void startProxySyncTask() {
        if (this.configManager.isProxyEnable()) {
            int initialDelaySeconds = this.configManager.getProxyUpdateInterval();
            new ProxySyncTask(this, this.database, this.configManager)
                    .runTaskLaterAsynchronously(this, initialDelaySeconds * 20L);
            logConsole("&#00F2FE  ▪ &#10B981Proxy Synchronization Bridge Enabled (Sync Interval: " + initialDelaySeconds + "s).");
        } else {
            logConsole("&#00F2FE  ▪ &#9CA3AFProxy Synchronization Bridge Disabled.");
        }
    }

    private void disablePluginDueToError(String reason) {
        logConsole("&#EF4444==========================================================");
        logConsole("&#EF4444  CRITICAL INITIALIZATION ERROR:");
        logConsole("&#EF4444  " + reason);
        logConsole("&#EF4444  Disabling plugin to prevent data corruption.");
        logConsole("&#EF4444==========================================================");
        getServer().getPluginManager().disablePlugin(this);
    }

    private void printStartupBanner() {
        Bukkit.getConsoleSender().sendMessage(ColorUtils.colorize(
                "&#00F2FE\n" +
                        "&#00F2FE     ______                      _                 _____                     ______    \n" +
                        "&#3B82F6    / ____/___  ____  ____ ___  (_)___  ___       / ___/____   ____  ____   / ____/    \n" +
                        "&#3B82F6   / /_  / __ \\/_  / / __ `__ \\/ / __ \\/ _ \\      \\__ \\/ __ \\ / __ \\/ __ \\ / /_        \n" +
                        "&#4FACFE  / __/ / /_/ / / /_/ / / / / / / / / /  __/     ___/ / /_/ // /_/ / /_/ // /_/        \n" +
                        "&#4FACFE /_/    \\____/ /___/_/ /_/ /_/_/_/ /_/\\___/    /____/ .___/ \\____/\\____/ /_/           \n" +
                        "&#00F2FE                                                  /_/                                  \n" +
                        "&#10B981                        FozmineSpoof Core v" + getDescription().getVersion() + "\n" +
                        "&#9CA3AF                    Ultra-Realistic FakePlayer Solution\n"
        ));
    }

    public void logConsole(String message) {
        Bukkit.getConsoleSender().sendMessage(ColorUtils.colorize("&#00F2FE[FozmineSpoof] " + message));
    }

    // ---- Public accessors ----

    public FozminespoofApi getBridge() {
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

    public BotLifecycleManager getBotLifecycleManager() {
        return botLifecycleManager;
    }

    public RankWeightManager getRankWeightManager() {
        return rankWeightManager;
    }

    public JoinMessageConfig getJoinMessageConfig() {
        return joinMessageConfig;
    }

    public JoinChatProcessor getJoinChatProcessor() {
        return joinChatProcessor;
    }

    public InteractiveMessageConfig getInteractiveMessageConfig() {
        return interactiveMessageConfig;
    }

    public AiConfig getAiConfig() {
        return aiConfig;
    }

    public AiPersonalityManager getAiPersonalityManager() {
        return aiPersonalityManager;
    }

    public AiChatProcessor getAiChatProcessor() {
        return aiChatProcessor;
    }
}