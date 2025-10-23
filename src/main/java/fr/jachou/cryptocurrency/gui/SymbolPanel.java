package fr.jachou.cryptocurrency.gui;

import fr.jachou.cryptocurrency.Cryptocurrency;
import fr.jachou.cryptocurrency.services.PriceService;
import fr.jachou.cryptocurrency.services.PriceTimeseriesService;
import fr.jachou.cryptocurrency.services.WalletManager;
import fr.jachou.cryptocurrency.util.ChartFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SymbolPanel {
    private final Cryptocurrency plugin;
    private final PriceService priceService;
    private final WalletManager walletManager;
    private final PriceTimeseriesService tsService;
    private final String symbol;

    private Inventory inv;
    private double qty = 0.1; // default qty

    public SymbolPanel(Cryptocurrency plugin, PriceService priceService, WalletManager walletManager, PriceTimeseriesService tsService, String symbol) {
        this.plugin = plugin;
        this.priceService = priceService;
        this.walletManager = walletManager;
        this.tsService = tsService;
        this.symbol = symbol.toUpperCase(Locale.ROOT);
    }

    public boolean owns(Inventory other) { return inv != null && other == inv; }

    public void open(Player p) {
        this.inv = Bukkit.createInventory(p, 27, ChatColor.GOLD + symbol + ChatColor.YELLOW + " — Trade");
        draw(p);
        p.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent e) {
        if (!owns(e.getInventory())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 26) { p.closeInventory(); return; }
        if (slot == 22) { // Confirm BUY by default
            if (!p.hasPermission("crypto.user.trade")) {
                p.sendMessage(Cryptocurrency.PREFIX + ChatColor.RED + "Vous n’avez pas la permission.");
                return;
            }
            priceService.getPriceUsd(symbol).thenAccept(price -> Bukkit.getScheduler().runTask(plugin, () -> {
                double costUsd = price * qty;
                walletManager.add(p.getUniqueId(), symbol, qty);
                p.sendMessage(Cryptocurrency.PREFIX + ChatColor.GREEN + "Achat simulé: " + String.format(Locale.US, "%,.4f", qty) + " " + symbol + ChatColor.GRAY + " (~$" + ChartFormatter.fmt2(costUsd) + ")");
                draw(p);
            })).exceptionally(ex -> { Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(Cryptocurrency.PREFIX + ChatColor.RED + "Erreur prix: " + ex.getMessage())); return null; });
            return;
        }
        if (slot == 12) { qty = 0.01; draw(p); return; }
        if (slot == 13) { qty = 0.1; draw(p); return; }
        if (slot == 14) { qty = 1.0; draw(p); return; }
        if (slot == 15) { // max = tout vendre
            double have = walletManager.get(p.getUniqueId(), symbol);
            qty = Math.max(0.0, have);
            draw(p);
            return;
        }
        if (slot == 20) { // SELL confirm
            if (!p.hasPermission("crypto.user.trade")) {
                p.sendMessage(Cryptocurrency.PREFIX + ChatColor.RED + "Vous n’avez pas la permission.");
                return;
            }
            priceService.getPriceUsd(symbol).thenAccept(price -> Bukkit.getScheduler().runTask(plugin, () -> {
                double have = walletManager.get(p.getUniqueId(), symbol);
                double sellQty = Math.min(qty, have);
                if (sellQty <= 0.0) { p.sendMessage(Cryptocurrency.PREFIX + ChatColor.RED + "Quantité insuffisante."); return; }
                walletManager.remove(p.getUniqueId(), symbol, sellQty);
                double usd = price * sellQty;
                p.sendMessage(Cryptocurrency.PREFIX + ChatColor.GREEN + "Vente simulée: " + String.format(Locale.US, "%,.4f", sellQty) + " " + symbol + ChatColor.GRAY + " (~$" + ChartFormatter.fmt2(usd) + ")");
                draw(p);
            })).exceptionally(ex -> { Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(Cryptocurrency.PREFIX + ChatColor.RED + "Erreur prix: " + ex.getMessage())); return null; });
        }
    }

    private void draw(Player viewer) {
        double price = Optional.ofNullable(priceService.getCachedUsd(symbol)).orElse(Double.NaN);
        if (Double.isNaN(price)) priceService.refreshAsync(symbol);
        List<PriceTimeseriesService.PricePoint> series = tsService.getSeries(symbol, 24, "h1");
        List<Double> vals = series.stream().map(p -> p.price()).toList();
        ChartFormatter.Stats st = ChartFormatter.stats(vals);

        inv.clear();
        inv.setItem(4, named(Material.GOLD_INGOT, ChatColor.YELLOW + symbol));
        inv.setItem(10, named(Material.PAPER, ChatColor.GRAY + "Prix: " + ChatColor.GREEN + "$" + (Double.isNaN(price) ? "..." : ChartFormatter.fmt2(price))));
        inv.setItem(11, named(Material.PAPER, ChatColor.GRAY + "Δ: " + (st.deltaPct >= 0 ? ChatColor.GREEN : ChatColor.RED) + String.format(Locale.US, "%+.2f%%", st.deltaPct)));

        inv.setItem(12, named(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, ChatColor.YELLOW + "+0.01"));
        inv.setItem(13, named(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, ChatColor.YELLOW + "+0.1"));
        inv.setItem(14, named(Material.IRON_BLOCK, ChatColor.YELLOW + "+1"));
        inv.setItem(15, named(Material.DIAMOND_BLOCK, ChatColor.YELLOW + "Max"));

        inv.setItem(20, named(Material.RED_WOOL, ChatColor.RED + "Vendre " + fmtQty()));
        inv.setItem(22, named(Material.LIME_WOOL, ChatColor.GREEN + "Acheter " + fmtQty()));
        inv.setItem(26, named(Material.BARRIER, ChatColor.GRAY + "Fermer"));
    }

    private String fmtQty() { return String.format(Locale.US, "%,.4f", qty) + " " + symbol; }

    private ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }
}
