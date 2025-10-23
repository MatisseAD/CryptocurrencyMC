package fr.jachou.cryptocurrency;

import fr.jachou.cryptocurrency.commands.CryptoCommand;
import fr.jachou.cryptocurrency.placeholders.CryptoPlaceholderExpansion;
import fr.jachou.cryptocurrency.services.Metrics;
import fr.jachou.cryptocurrency.services.PriceService;
import fr.jachou.cryptocurrency.services.PriceTimeseriesService;
import fr.jachou.cryptocurrency.services.TransactionManager;
import fr.jachou.cryptocurrency.services.WalletManager;
import fr.jachou.cryptocurrency.gui.GuiManager;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.permission.Permission;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

public final class Cryptocurrency extends JavaPlugin {

    private static Cryptocurrency instance;
    private static Economy economy = null;
    public static String PREFIX = "§6[§eCrypto§6] §r";
    private static Permission perms = null;
    private static Chat chat = null;

    // Simple address store retained from initial draft (not used by command stubs yet)
    private static HashMap<UUID, String> wallets = new HashMap<>();

    private File walletsFile;
    private FileConfiguration walletsConfig;

    // Services
    private final WalletManager walletManager = new WalletManager();
    private final PriceService priceService = new PriceService();
    private final TransactionManager transactionManager = new TransactionManager();
    private PriceTimeseriesService timeseriesService;
    private GuiManager guiManager;

    @Override
    public void onLoad() {
        // Initialiser les fichiers de config le plus tôt possible
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        walletsFile = new File(getDataFolder(), "wallets.yml");
        walletsConfig = YamlConfiguration.loadConfiguration(walletsFile);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;

        saveDefaultConfig();
        // Initialize messages and configurable prefix
        fr.jachou.cryptocurrency.util.Messages.init(this);
        String cfgPrefix = getConfig().getString("messages.prefix", "&6[&eCrypto&6] &r");
        PREFIX = fr.jachou.cryptocurrency.util.Messages.color(cfgPrefix);

        setupPermissions();
        setupChat();
        setupEconomy();

        int pluginID = 27681;
        Metrics metrics = new Metrics(this, pluginID);

        // Services complémentaires
        // Configurer PriceService (timeouts + circuit breaker)
        int timeout = getConfig().getInt("api.timeout_ms", 4000);
        int retry = getConfig().getInt("api.retry", 1);
        priceService.configureHttp(timeout, retry);
        int errTh = getConfig().getInt("api.circuit_breaker.error_threshold", 5);
        int openSec = getConfig().getInt("api.circuit_breaker.open_seconds", 30);
        priceService.configureCircuitBreaker(errTh, openSec);

        // Load wallets from file
        walletManager.loadFromFile(walletsFile);

        timeseriesService = new PriceTimeseriesService(this, priceService);
        int retention = getConfig().getInt("chart.retention_points", 360);
        int sample = getConfig().getInt("chart.sample_seconds", 60);
        timeseriesService.configure(retention, sample);
        java.util.List<String> enabled = getConfig().getStringList("market.enabled_symbols");
        if (enabled == null || enabled.isEmpty()) enabled = java.util.Arrays.asList("BTC","ETH","SOL","DOGE");
        timeseriesService.startSampler(enabled);

        guiManager = new fr.jachou.cryptocurrency.gui.GuiManager(this, priceService, walletManager, timeseriesService);
        getServer().getPluginManager().registerEvents(guiManager, this);

        // Commande
        CryptoCommand command = new CryptoCommand(this);
        if (getCommand("crypto") != null) {
            getCommand("crypto").setExecutor(command);
            getCommand("crypto").setTabCompleter(command);
        } else {
            getLogger().warning("La commande 'crypto' n'est pas déclarée dans plugin.yml.");
        }

        // Placeholders
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                if (getConfig().getBoolean("placeholders.enabled", true)) {
                    new CryptoPlaceholderExpansion(this).register();
                    getLogger().info("[Crypto] PlaceholderAPI détecté : placeholders enregistrés.");
                } else {
                    getLogger().info("[Crypto] Placeholders désactivés via config.");
                }
            } catch (Throwable t) {
                getLogger().warning("[Crypto] Échec de l'enregistrement des placeholders : " + t.getMessage());
            }
        } else {
            getLogger().info("[Crypto] PlaceholderAPI non trouvé : aucun placeholder n'a été enregistré.");
        }

        // Les fichiers sont initialisés dans onLoad(), on lit simplement le contenu ici
        ConfigurationSection sec = walletsConfig != null ? walletsConfig.getConfigurationSection("wallets") : null;
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String addr = sec.getString(key);
                    if (addr != null) wallets.put(uuid, addr);
                } catch (IllegalArgumentException ignored) {}
            }
        }

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        
        // Save wallets to file
        walletManager.saveToFile(walletsFile);

        walletsConfig.set("wallets", null); // reset la section
        for (var entry : wallets.entrySet()) {
            walletsConfig.set("wallets." + entry.getKey(), entry.getValue());
        }
        try {
            walletsConfig.save(walletsFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Stop timeseries sampler
        if (timeseriesService != null) {
            timeseriesService.stop();
        }

    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private boolean setupChat() {
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        if (rsp == null) {
            chat = null;
            return false;
        }
        chat = rsp.getProvider();
        return chat != null;
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp == null) {
            perms = null;
            return false;
        }
        perms = rsp.getProvider();
        return perms != null;
    }

    public static Economy getEconomy() {
        return economy;
    }

    public static Cryptocurrency getInstance() {
        return instance;
    }

    public WalletManager getWalletManager() { return walletManager; }
    public PriceService getPriceService() { return priceService; }
    public TransactionManager getTransactionManager() { return transactionManager; }
    public PriceTimeseriesService getTimeseriesService() { return timeseriesService; }
    public GuiManager getGuiManager() { return guiManager; }

    public void setWallet(UUID uuid, String address) {
        wallets.put(uuid, address);
        if (walletsConfig != null && walletsFile != null) {
            walletsConfig.set("wallets." + uuid.toString(), address);
            try { walletsConfig.save(walletsFile); } catch (Exception ignored) {}
        }
    }

    public String getWallet(UUID uuid) {
        return wallets.get(uuid);
    }

}
