package fr.jachou.cryptocurrency.placeholders;

import fr.jachou.cryptocurrency.Cryptocurrency;
import fr.jachou.cryptocurrency.services.PriceService;
import fr.jachou.cryptocurrency.services.WalletManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.Map;

public class CryptoPlaceholderExpansion extends PlaceholderExpansion {

    private final Cryptocurrency plugin;

    public CryptoPlaceholderExpansion(Cryptocurrency plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "crypto"; // %crypto_...%
    }

    @Override
    public String getAuthor() {
        return "Jachou";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    // Important pour éviter la désinscription au /reload
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    /**
     * Placeholders (insensibles à la casse):
     * - %crypto_price_<sym>% -> prix USD (cache) 2 décimales
     * - %crypto_change24_<sym>% -> variation % sur la fenêtre (séries) +/−1.23
     * - %crypto_wallet_<sym>% -> quantité possédée (4 décimales)
     * - %crypto_wallet_usd% -> valeur totale du portefeuille du joueur (USD)
     * - %crypto_top1_name% / %crypto_top1_value% -> meilleur investisseur
     * - %crypto_api_status% -> OK/DEGRADED/DOWN
     */
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";

        String p = params.toLowerCase(Locale.ROOT);
        PriceService ps = plugin.getPriceService();
        WalletManager wm = plugin.getWalletManager();

        // api_status
        if (p.equals("api_status")) {
            return ps.getApiStatus().name();
        }

        // wallet_usd (total)
        if (p.equals("wallet_usd") || p.equals("value_total")) {
            Map<String, Double> wallet = wm.getWallet(player.getUniqueId());
            if (wallet.isEmpty()) return "0.00";
            double total = 0.0;
            for (Map.Entry<String, Double> e : wallet.entrySet()) {
                Double price = ps.getCachedUsd(e.getKey());
                if (price == null) { ps.refreshAsync(e.getKey()); continue; }
                total += price * e.getValue();
            }
            return fmt2(total);
        }

        // top1
        if (p.equals("top1_name") || p.equals("top1_value")) {
            java.util.List<java.util.Map.Entry<java.util.UUID, Double>> top = wm.topByUsdValue(w -> {
                double sum = 0.0;
                for (var e : w.entrySet()) {
                    Double price = ps.getCachedUsd(e.getKey());
                    if (price == null) continue;
                    sum += price * e.getValue();
                }
                return sum;
            }, 1);
            if (top.isEmpty()) return "";
            java.util.UUID uuid = top.get(0).getKey();
            if (p.equals("top1_name")) {
                String name = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
                return name == null ? uuid.toString() : name;
            } else {
                return fmt2(top.get(0).getValue());
            }
        }

        // price_<sym>
        if (p.startsWith("price_")) {
            String sym = p.substring("price_".length()).trim().toUpperCase(Locale.ROOT);
            if (sym.isEmpty()) return "";
            Double price = ps.getCachedUsd(sym);
            if (price == null) { ps.refreshAsync(sym); return ""; }
            return fmt2(price);
        }

        // change24_<sym> (basé sur séries temporelles; fenêtre approx selon sample_seconds)
        if (p.startsWith("change24_")) {
            String sym = p.substring("change24_".length()).trim().toUpperCase(Locale.ROOT);
            if (sym.isEmpty()) return "";
            var ts = plugin.getTimeseriesService();
            if (ts == null) return "";
            int sample = Math.max(1, plugin.getConfig().getInt("chart.sample_seconds", 60));
            int windowPoints = Math.max(2, (int) Math.round(24 * 3600.0 / sample));
            var list = ts.getSeries(sym, windowPoints, "d1");
            if (list.size() < 2) return "";
            double first = list.get(0).price();
            double last = list.get(list.size()-1).price();
            if (first == 0.0) return "";
            double pct = (last - first) / first * 100.0;
            return String.format(Locale.US, "%+.2f", pct);
        }

        // wallet_<sym>
        if (p.startsWith("wallet_")) {
            String sym = p.substring("wallet_".length()).trim().toUpperCase(Locale.ROOT);
            if (sym.isEmpty()) return "";
            double qty = wm.get(player.getUniqueId(), sym);
            return fmt4(qty);
        }

        return ""; // inconnu -> vide
    }

    private static String fmt2(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    private static String fmt4(double v) {
        return String.format(Locale.US, "%,.4f", v);
    }
}