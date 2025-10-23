package fr.jachou.cryptocurrency.services;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal wallet manager. In-memory only for now.
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
}
