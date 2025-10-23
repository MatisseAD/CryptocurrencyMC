package fr.jachou.cryptocurrency.gui;

import fr.jachou.cryptocurrency.Cryptocurrency;
import fr.jachou.cryptocurrency.services.PriceService;
import fr.jachou.cryptocurrency.services.PriceTimeseriesService;
import fr.jachou.cryptocurrency.services.WalletManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Gère les ouvertures/rafraîchissements des GUIs et bloque les interactions indésirables.
 */
public class GuiManager implements Listener {

    private final Cryptocurrency plugin;
    private final PriceService priceService;
    private final WalletManager walletManager;
    private final PriceTimeseriesService tsService;

    // Pointeurs vers inventaires gérés
    private final Map<UUID, MarketGui> openMarkets = new HashMap<>();
    private final Map<UUID, SymbolPanel> openPanels = new HashMap<>();

    public GuiManager(Cryptocurrency plugin, PriceService priceService, WalletManager walletManager, PriceTimeseriesService tsService) {
        this.plugin = plugin;
        this.priceService = priceService;
        this.walletManager = walletManager;
        this.tsService = tsService;
    }

    public void registerPanel(UUID uuid, SymbolPanel panel) {
        openPanels.put(uuid, panel);
    }

    public void openMarket(Player player) {
        MarketGui gui = new MarketGui(plugin, priceService, walletManager, tsService);
        openMarkets.put(player.getUniqueId(), gui);
        gui.open(player);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        HumanEntity who = e.getWhoClicked();
        if (!(who instanceof Player player)) return;
        MarketGui mg = openMarkets.get(player.getUniqueId());
        if (mg != null && mg.owns(e.getInventory())) {
            mg.handleClick(e);
            return;
        }
        SymbolPanel sp = openPanels.get(player.getUniqueId());
        if (sp != null && sp.owns(e.getInventory())) {
            sp.handleClick(e);
            return;
        }
    }

    @EventHandler
    public void onInvDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        MarketGui mg = openMarkets.get(p.getUniqueId());
        if (mg != null && mg.owns(e.getInventory())) {
            e.setCancelled(true);
            return;
        }
        SymbolPanel sp = openPanels.get(p.getUniqueId());
        if (sp != null && sp.owns(e.getInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        MarketGui mg = openMarkets.get(p.getUniqueId());
        if (mg != null && mg.owns(e.getInventory())) {
            openMarkets.remove(p.getUniqueId());
            return;
        }
        SymbolPanel sp = openPanels.get(p.getUniqueId());
        if (sp != null && sp.owns(e.getInventory())) {
            openPanels.remove(p.getUniqueId());
        }
    }
}
