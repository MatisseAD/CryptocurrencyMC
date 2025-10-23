package fr.jachou.cryptocurrency.services;

import fr.jachou.cryptocurrency.Cryptocurrency;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * Collecte périodiquement les prix et maintient des séries temporelles en mémoire.
 * Ne fait jamais d'appel réseau sur le main thread.
 */
public class PriceTimeseriesService {

    public static record PricePoint(long ts, double price) {}

    private final Cryptocurrency plugin;
    private final PriceService priceService;

    private final Map<String, Deque<PricePoint>> timeSeries = new ConcurrentHashMap<>();

    private int retentionPoints = 360;
    private int sampleSeconds = 60;

    private BukkitTask samplerTask;

    public PriceTimeseriesService(Cryptocurrency plugin, PriceService priceService) {
        this.plugin = plugin;
        this.priceService = priceService;
    }

    public void configure(int retentionPoints, int sampleSeconds) {
        this.retentionPoints = Math.max(12, retentionPoints);
        this.sampleSeconds = Math.max(5, sampleSeconds);
    }

    public void startSampler(List<String> enabledSymbols) {
        if (samplerTask != null) samplerTask.cancel();
        List<String> symbols = enabledSymbols.stream().map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toList());
        samplerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (String sym : symbols) {
                try {
                    priceService.getPriceUsd(sym).thenAccept(price -> {
                        Deque<PricePoint> dq = timeSeries.computeIfAbsent(sym, k -> new ConcurrentLinkedDeque<>());
                        dq.addLast(new PricePoint(now, price));
                        // trim
                        while (dq.size() > retentionPoints) dq.pollFirst();
                    }).exceptionally(ex -> {
                        // En cas d'échec, on peut quand même pousser la dernière valeur en cache si dispo
                        Double cached = priceService.getCachedUsd(sym);
                        if (cached != null) {
                            Deque<PricePoint> dq = timeSeries.computeIfAbsent(sym, k -> new ConcurrentLinkedDeque<>());
                            dq.addLast(new PricePoint(now, cached));
                            while (dq.size() > retentionPoints) dq.pollFirst();
                        }
                        return null;
                    });
                } catch (Throwable t) {
                    // Ne rien faire, éviter de casser le scheduler
                }
            }
        }, 20L, sampleSeconds * 20L);
    }

    public void stop() {
        if (samplerTask != null) samplerTask.cancel();
        samplerTask = null;
    }

    /**
     * Retourne une copie immuable de la série demandée, tronquée au nombre de points.
     * range: m15|h1|h6|d1 (actuellement indicatif; l'échantillonnage est fixe, on prend juste N derniers points).
     */
    public List<PricePoint> getSeries(String symbol, int points, String range) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        Deque<PricePoint> dq = timeSeries.get(sym);
        if (dq == null || dq.isEmpty()) return Collections.emptyList();
        int n = Math.min(points, dq.size());
        List<PricePoint> list = new ArrayList<>(n);
        Iterator<PricePoint> it = dq.descendingIterator();
        for (int i = 0; i < n && it.hasNext(); i++) list.add(it.next());
        Collections.reverse(list);
        return Collections.unmodifiableList(list);
    }

    public Map<String, Deque<PricePoint>> getTimeSeries() { return timeSeries; }
}
