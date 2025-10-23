package fr.jachou.cryptocurrency.services;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import fr.jachou.cryptocurrency.Cryptocurrency;
import org.json.JSONObject;

public class PriceService {

    public enum ApiStatus { OK, DEGRADED, DOWN }

    // Cache symbol -> prix (USD)
    private final Map<String, Double> cache = new ConcurrentHashMap<>();

    // Temps de cache en ms (ex : 60 secondes)
    private static final long CACHE_DURATION = 60_000;
    private final Map<String, Long> lastUpdate = new ConcurrentHashMap<>();

    // Circuit breaker minimal
    private int errorThreshold = 5;
    private int openSeconds = 30;
    private volatile long circuitOpenUntil = 0L;
    private volatile int recentErrors = 0;

    // HTTP config
    private int timeoutMs = 4000;
    private int retryCount = 1;

    public void configureCircuitBreaker(int errorThreshold, int openSeconds) {
        this.errorThreshold = Math.max(1, errorThreshold);
        this.openSeconds = Math.max(1, openSeconds);
    }

    public void configureHttp(int timeoutMs, int retryCount) {
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.retryCount = Math.max(0, retryCount);
    }

    public ApiStatus getApiStatus() {
        long now = System.currentTimeMillis();
        if (now < circuitOpenUntil) return ApiStatus.DOWN;
        if (recentErrors > 0) return ApiStatus.DEGRADED;
        return ApiStatus.OK;
    }

    private void onError() {
        recentErrors++;
        if (recentErrors >= errorThreshold) {
            circuitOpenUntil = System.currentTimeMillis() + openSeconds * 1000L;
            recentErrors = 0; // reset counter when opened
        }
    }

    private void onSuccess() {
        recentErrors = 0;
    }

    /**
     * Récupère le prix USD d'une crypto.
     * Si le cache est récent, on l'utilise, sinon on fait une requête à CoinGecko.
     */
    public CompletableFuture<Double> getPriceUsd(String symbol) {
        String sym = symbol.toLowerCase(Locale.ROOT);

        // Si le cache est encore frais (<60s), retourne directement la valeur
        if (cache.containsKey(sym) && (System.currentTimeMillis() - lastUpdate.getOrDefault(sym, 0L)) < CACHE_DURATION) {
            return CompletableFuture.completedFuture(cache.get(sym));
        }

        // Circuit breaker ouvert -> pas d'appel réseau
        if (System.currentTimeMillis() < circuitOpenUntil) {
            Double cached = cache.get(sym);
            return CompletableFuture.completedFuture(cached != null ? cached : 1.0);
        }

        // Requête asynchrone (non bloquante)
        return CompletableFuture.supplyAsync(() -> {
            for (int attempt = 0; attempt <= retryCount; attempt++) {
                try {
                    String apiUrl = "https://api.coingecko.com/api/v3/simple/price?ids=" +
                            getCoinGeckoId(sym) + "&vs_currencies=usd";
                    HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(timeoutMs);
                    conn.setReadTimeout(timeoutMs);

                    if (conn.getResponseCode() != 200) {
                        throw new RuntimeException("API error: " + conn.getResponseCode());
                    }

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        JSONObject json = new JSONObject(response.toString());
                        String id = getCoinGeckoId(sym);
                        double price = json.getJSONObject(id).getDouble("usd");

                        cache.put(sym, price);
                        lastUpdate.put(sym, System.currentTimeMillis());
                        onSuccess();
                        return price;
                    }
                } catch (Exception e) {
                    onError();
                    Bukkit.getLogger().warning("[Crypto] Erreur lors de la récupération du prix de " + sym + " (tentative " + (attempt+1) + ") : " + e.getMessage());
                    // boucle pour retry
                }
            }
            // Si tous les essais échouent, ouvrir potentiellement le circuit et renvoyer cache ou 1.0
            return cache.getOrDefault(sym, 1.0);
        });
    }

    /**
     * Convertit le symbole en ID CoinGecko
     */
    private String getCoinGeckoId(String symbol) {
        return switch (symbol.toLowerCase(Locale.ROOT)) {
            case "btc" -> "bitcoin";
            case "eth" -> "ethereum";
            case "sol" -> "solana";
            case "doge" -> "dogecoin";
            case "bnb" -> "binancecoin";
            case "ada" -> "cardano";
            default -> symbol.toLowerCase(Locale.ROOT);
        };
    }

    /**
     * Permet de forcer le rafraîchissement d’un symbole
     */
    public void refresh(String symbol) {
        lastUpdate.put(symbol.toLowerCase(Locale.ROOT), 0L);
    }

    public Double getCachedUsd(String symbol) {
        String sym = symbol.toLowerCase(Locale.ROOT);
        return cache.get(sym);
    }

    // Déclenche un rafraîchissement asynchrone (ne bloque pas)
    public void refreshAsync(String symbol) {
        // Appelle votre méthode async ; elle mettra à jour le cache quand prête
        getPriceUsd(symbol);
    }
}
