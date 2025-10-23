package fr.jachou.cryptocurrency.services;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wallet manager with persistence support.
 * Map: player UUID -> Map<symbol, amount>
 */
public class WalletManager {
    private final Map<UUID, Map<String, Double>> wallets = new ConcurrentHashMap<>();

    public Map<String, Double> getWallet(UUID uuid) {
        return wallets.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    }

    public double get(UUID uuid, String symbol) {
        return getWallet(uuid).getOrDefault(symbol.toUpperCase(Locale.ROOT), 0.0);
    }

    public void set(UUID uuid, String symbol, double amount) {
        getWallet(uuid).put(symbol.toUpperCase(Locale.ROOT), amount);
    }

    public void add(UUID uuid, String symbol, double delta) {
        String s = symbol.toUpperCase(Locale.ROOT);
        getWallet(uuid).merge(s, delta, Double::sum);
    }

    public boolean remove(UUID uuid, String symbol, double delta) {
        String s = symbol.toUpperCase(Locale.ROOT);
        double cur = get(uuid, s);
        if (cur < delta) return false;
        set(uuid, s, cur - delta);
        return true;
    }

    public List<Map.Entry<UUID, Double>> topByUsdValue(java.util.function.Function<Map<String, Double>, Double> valuator, int limit){
        List<Map.Entry<UUID, Double>> list = new ArrayList<>();
        for (var e : wallets.entrySet()) {
            list.add(Map.entry(e.getKey(), valuator.apply(e.getValue())));
        }
        list.sort((a,b) -> Double.compare(b.getValue(), a.getValue()));
        if (list.size() > limit) return list.subList(0, limit);
        return list;
    }
    
    public Map<UUID, Map<String, Double>> getAllWallets() {
        return wallets;
    }
    
    /**
     * Load wallets from YAML file
     */
    public void loadFromFile(File file) {
        if (!file.exists()) return;
        
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            var section = config.getConfigurationSection("wallets");
            if (section == null) return;
            
            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    var symbolSection = section.getConfigurationSection(uuidStr);
                    if (symbolSection == null) continue;
                    
                    Map<String, Double> wallet = getWallet(uuid);
                    for (String symbol : symbolSection.getKeys(false)) {
                        double amount = symbolSection.getDouble(symbol, 0.0);
                        if (amount > 0.0) {
                            wallet.put(symbol.toUpperCase(Locale.ROOT), amount);
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID, skip
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Save wallets to YAML file
     */
    public void saveToFile(File file) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            
            for (Map.Entry<UUID, Map<String, Double>> entry : wallets.entrySet()) {
                String uuidStr = entry.getKey().toString();
                Map<String, Double> wallet = entry.getValue();
                
                for (Map.Entry<String, Double> symbolEntry : wallet.entrySet()) {
                    String symbol = symbolEntry.getKey();
                    double amount = symbolEntry.getValue();
                    if (amount > 0.0) {
                        config.set("wallets." + uuidStr + "." + symbol, amount);
                    }
                }
            }
            
            config.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
