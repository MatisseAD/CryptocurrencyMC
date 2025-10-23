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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class MarketGui {

    private final Cryptocurrency plugin;
    private final PriceService priceService;
    private final WalletManager walletManager;
    private final PriceTimeseriesService tsService;

    private Inventory inv;
    private List<String> symbols;

    private final int[] gridSlots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};

    public MarketGui(Cryptocurrency plugin, PriceService priceService, WalletManager walletManager, PriceTimeseriesService tsService) {
        this.plugin = plugin;
        this.priceService = priceService;
        this.walletManager = walletManager;
        this.tsService = tsService;
    }

    public boolean owns(Inventory other) { return inv != null && other == inv; }

    public void open(Player p) {
        this.inv = Bukkit.createInventory(p, 54, ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("market.gui_title", "&6Crypto Market")));
        this.symbols = plugin.getConfig().getStringList("market.enabled_symbols");
        if (this.symbols == null || this.symbols.isEmpty()) this.symbols = Arrays.asList("BTC","ETH","SOL","DOGE");
        draw(p);
        p.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent e) {
        if (!owns(e.getInventory())) return;
        e.setCancelled(true); // Anti-dup: bloquer tout par défaut
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 53) { // refresh
            Bukkit.getScheduler().runTask(plugin, () -> draw(p));
            return;
        }
        if (slot == 43 || slot == 44) { // buy/sell buttons from sidebar, not per-symbol panel
            p.sendMessage(Cryptocurrency.PREFIX + ChatColor.YELLOW + "Sélectionne d'abord un symbole (clic gauche sur l'item)." );
            return;
        }
        // Grid symbol click -> open symbol panel
        int idx = slotToSymbolIndex(slot);
        if (idx >= 0 && idx < symbols.size()) {
            String sym = symbols.get(idx);
            SymbolPanel panel = new SymbolPanel(plugin, priceService, walletManager, tsService, sym);
            plugin.getGuiManager().registerPanel(p.getUniqueId(), panel);
            panel.open(p);
        }
    }

    private int slotToSymbolIndex(int slot) {
        for (int i = 0; i < gridSlots.length; i++) if (gridSlots[i] == slot) return i;
        return -1;
    }

    private void draw(Player viewer) {
        // Header (slot 4)
        inv.setItem(4, named(Material.CLOCK, ChatColor.GOLD + "Crypto Market"));
        // Sidebar actions
        inv.setItem(41, named(Material.PAPER, ChatColor.YELLOW + "Quantité: via panneau"));
        inv.setItem(43, named(Material.LIME_WOOL, ChatColor.GREEN + "Acheter"));
        inv.setItem(44, named(Material.RED_WOOL, ChatColor.RED + "Vendre"));
        inv.setItem(52, named(Material.BOOK, ChatColor.GRAY + "Historique (bientôt)"));
        inv.setItem(53, named(Material.SUNFLOWER, ChatColor.YELLOW + "Rafraîchir"));
        inv.setItem(45, named(Material.ARROW, ChatColor.GRAY + "Page précédente"));
        inv.setItem(46, named(Material.ARROW, ChatColor.GRAY + "Page suivante"));
        inv.setItem(49, named(Material.EMERALD, ChatColor.GOLD + "Valeur totale: $" + totalUsd(viewer)));

        // Grid symbols
        for (int i = 0; i < gridSlots.length; i++) {
            int slot = gridSlots[i];
            if (i >= symbols.size()) {
                inv.setItem(slot, new ItemStack(Material.AIR));
                continue;
            }
            String sym = symbols.get(i).toUpperCase(Locale.ROOT);
            inv.setItem(slot, buildSymbolItem(viewer, sym));
        }
    }

    private String totalUsd(Player p) {
        Map<String, Double> w = walletManager.getWallet(p.getUniqueId());
        double total = 0.0;
        for (var e : w.entrySet()) {
            Double price = priceService.getCachedUsd(e.getKey());
            if (price == null) { priceService.refreshAsync(e.getKey()); continue; }
            total += price * e.getValue();
        }
        return ChartFormatter.fmt2(total);
    }

    private ItemStack buildSymbolItem(Player viewer, String sym) {
        double price = Optional.ofNullable(priceService.getCachedUsd(sym)).orElse(Double.NaN);
        if (Double.isNaN(price)) priceService.refreshAsync(sym);
        List<PriceTimeseriesService.PricePoint> series = tsService.getSeries(sym, 12, "h1");
        List<Double> vals = series.stream().map(p -> p.price()).collect(Collectors.toList());
        ChartFormatter.Stats st = ChartFormatter.stats(vals);
        String glyphs = plugin.getConfig().getString("chart.sparkline_chars", "▁▂▃▄▅▆▇█");
        String spark = ChartFormatter.sparkline(vals, glyphs);
        double change = st.deltaPct;
        String changeStr = (change >= 0 ? ChatColor.GREEN : ChatColor.RED) + String.format(Locale.US, "%+.2f%%", change);
        double youOwn = walletManager.get(viewer.getUniqueId(), sym);
        double youUsd = (Double.isNaN(price) ? 0.0 : price * youOwn);

        ItemStack it = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + sym);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Price: " + ChatColor.GREEN + "$" + (Double.isNaN(price) ? "..." : ChartFormatter.fmt2(price)));
        lore.add(ChatColor.GRAY + "Δ 24h: " + changeStr);
        lore.add(ChatColor.GRAY + "You own: " + ChatColor.WHITE + String.format(Locale.US, "%,.4f", youOwn) + " " + sym + ChatColor.GRAY + " (" + ChatColor.GREEN + "$" + ChartFormatter.fmt2(youUsd) + ChatColor.GRAY + ")");
        if (!spark.isEmpty()) lore.add(ChatColor.DARK_GRAY + spark);
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }
}
