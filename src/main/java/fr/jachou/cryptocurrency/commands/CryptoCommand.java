package fr.jachou.cryptocurrency.commands;

import fr.jachou.cryptocurrency.Cryptocurrency;
import fr.jachou.cryptocurrency.services.PriceService;
import fr.jachou.cryptocurrency.services.WalletManager;
import fr.jachou.cryptocurrency.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CryptoCommand implements CommandExecutor, TabCompleter {

    private final Cryptocurrency plugin;

    public CryptoCommand(Cryptocurrency plugin) {
        this.plugin = plugin;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Cryptocurrency.PREFIX + Messages.t("help.header", "&eCommandes disponibles :"));
        sender.sendMessage("§7/crypto price <symbole...> §f- Voir le prix actuel d’une crypto");
        sender.sendMessage("§7/crypto buy <symbole> <montant> §f- Acheter une crypto");
        sender.sendMessage("§7/crypto sell <symbole> <montant> §f- Vendre une crypto");
        sender.sendMessage("§7/crypto balance [joueur] §f- Voir ton portefeuille");
        sender.sendMessage("§7/crypto transfer <joueur> <symbole> <montant> §f- Envoyer des cryptos");
        sender.sendMessage("§7/crypto top §f- Voir le classement des investisseurs");
        sender.sendMessage("§7/crypto convert <symbole> <symbole2> <montant> §f- Convertir des cryptos");
        sender.sendMessage("§7/crypto reload §f- Recharger la config (admin)");
        sender.sendMessage("§7/crypto set|add|remove <joueur> <symbole> <montant> §f- Gérer les wallets (admin)");
        sender.sendMessage("§7/crypto giveall <symbole> <montant> §f- Airdrop global (admin)");
        sender.sendMessage("§7/crypto history [joueur] §f- Voir l’historique");
        sender.sendMessage("§7/crypto chart <symbole> §f- Mini graphique");
        sender.sendMessage("§7/crypto api <status|refresh> §f- État API");
        sender.sendMessage("§7/crypto market §f- Ouvrir le marché (GUI)");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help", "info" -> {
                sendHelp(sender);
                return true;
            }
            case "price" -> {
                if (!sender.hasPermission("crypto.user.price")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                PriceService price = plugin.getPriceService();
                if (args.length < 2) { sender.sendMessage(Messages.f("usage.price", "&eUsage: /{label} price <symbole...>", java.util.Map.of("label", label))); return true; }

                // Un message par symbole, sans bloquer
                for (int i = 1; i < args.length; i++) {
                    final String sym = args[i];
                    price.getPriceUsd(sym)
                            .thenAccept(p -> runSync(() ->
                                    sender.sendMessage(Cryptocurrency.PREFIX + "§7" + sym.toUpperCase(Locale.ROOT) + " §f= §a$" + fmt2(p))
                            ))
                            .exceptionally(ex -> {
                                runSync(() -> sender.sendMessage(Cryptocurrency.PREFIX + "§cImpossible de récupérer le prix de " + sym.toUpperCase(Locale.ROOT) + " : " + ex.getMessage()));
                                return null;
                            });
                }
                return true;
            }
            case "buy", "sell" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(Messages.t("player_only", "&cJoueur uniquement.")); return true; }
                if (!sender.hasPermission("crypto.user.trade")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                if (args.length < 3) { sender.sendMessage(Messages.f("usage.trade", "&eUsage: /{label} {sub} <symbole> <montant>", java.util.Map.of("label", label, "sub", sub))); return true; }

                final String sym = args[1];
                final double amount;
                try { amount = Double.parseDouble(args[2]); } catch (Exception e) { sender.sendMessage(Messages.t("invalid_amount", "&cMontant invalide.")); return true; }

                plugin.getPriceService().getPriceUsd(sym)
                        .thenAccept(priceUsd -> {
                            double usd = priceUsd * amount;
                            runSync(() -> {
                                String symUp = sym.toUpperCase(java.util.Locale.ROOT);
                                String usdStr = fmt2(usd);
                                String amtStr = fmt4(amount);
                                Economy econ = Cryptocurrency.getEconomy();
                                WalletManager wm2 = plugin.getWalletManager();
                                if (sub.equals("buy")) {
                                    if (econ != null) {
                                        double bal = econ.getBalance(player);
                                        if (bal + 1e-9 < usd) {
                                            sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("trade.buy.nomoney", "&cFonds insuffisants. Requis: ${usd} | Solde: ${balance}", java.util.Map.of(
                                                    "usd", usdStr,
                                                    "balance", fmt2(bal)
                                            )));
                                            return;
                                        }
                                        EconomyResponse r = econ.withdrawPlayer(player, usd);
                                        if (r != null && r.transactionSuccess()) {
                                            wm2.add(player.getUniqueId(), symUp, amount);
                                            plugin.getTransactionManager().record(player.getUniqueId(), "BUY", 
                                                String.format("Bought %s %s for $%s", amtStr, symUp, usdStr));
                                            sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("trade.buy.success", "&aAchat: &f{amount} {symbol} &7(-${usd})", java.util.Map.of(
                                                    "amount", amtStr,
                                                    "symbol", symUp,
                                                    "usd", usdStr
                                            )));
                                        } else {
                                            String err = (r == null ? "unknown" : r.errorMessage);
                                            sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("trade.buy.failed", "&cAchat impossible: {error}", java.util.Map.of("error", err)));
                                        }
                                    } else {
                                        sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("trade.buy.simulated", "&eAchat simulé: &f{amount} {symbol} &7(~${usd})", java.util.Map.of(
                                                "amount", amtStr,
                                                "symbol", symUp,
                                                "usd", usdStr
                                        )));
                                    }
                                } else { // sell
                                    double owned = wm2.get(player.getUniqueId(), symUp);
                                    if (owned + 1e-9 < amount) {
                                        sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("trade.sell.notenough", "&cVous n'avez pas assez de {symbol}. Possédé: {owned}", java.util.Map.of(
                                                "symbol", symUp,
                                                "owned", fmt4(owned)
                                        )));
                                        return;
                                    }
                                    if (econ != null) {
                                        boolean ok = wm2.remove(player.getUniqueId(), symUp, amount);
                                        if (ok) {
                                            EconomyResponse r = econ.depositPlayer(player, usd);
                                            if (r != null && r.transactionSuccess()) {
                                                plugin.getTransactionManager().record(player.getUniqueId(), "SELL", 
                                                    String.format("Sold %s %s for $%s", amtStr, symUp, usdStr));
                                                sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("trade.sell.success", "&aVente: &f{amount} {symbol} &7(+${usd})", java.util.Map.of(
                                                        "amount", amtStr,
                                                        "symbol", symUp,
                                                        "usd", usdStr
                                                )));
                                            } else {
                                                // rollback wallet if economy failed
                                                wm2.add(player.getUniqueId(), symUp, amount);
                                                String err = (r == null ? "unknown" : r.errorMessage);
                                                sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("trade.sell.failed", "&cVente impossible: {error}", java.util.Map.of("error", err)));
                                            }
                                        } else {
                                            sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("trade.sell.notenough", "&cVous n'avez pas assez de {symbol}. Possédé: {owned}", java.util.Map.of(
                                                    "symbol", symUp,
                                                    "owned", fmt4(owned)
                                            )));
                                        }
                                    } else {
                                        sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("trade.sell.simulated", "&eVente simulée: &f{amount} {symbol} &7(~${usd})", java.util.Map.of(
                                                "amount", amtStr,
                                                "symbol", symUp,
                                                "usd", usdStr
                                        )));
                                    }
                                }
                            });
                        })
                        .exceptionally(ex -> {
                            runSync(() -> sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("price.error", "&cErreur prix {symbol} : {error}", java.util.Map.of(
                                    "symbol", sym.toUpperCase(java.util.Locale.ROOT),
                                    "error", ex.getMessage() == null ? "" : ex.getMessage()
                            ))));
                            return null;
                        });
                return true;
            }
            case "balance", "wallet" -> {
                if (!sender.hasPermission("crypto.user.balance")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                if (args.length == 1) {
                    if (!(sender instanceof Player p)) { sender.sendMessage(Messages.f("usage.balance", "&eUsage: /{label} balance <joueur>", java.util.Map.of("label", label))); return true; }
                    showBalanceAsync(sender, p.getUniqueId(), p.getName());
                } else {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    showBalanceAsync(sender, target.getUniqueId(), target.getName());
                }
                return true;
            }
            case "transfer" -> {
                if (!(sender instanceof Player)) { sender.sendMessage(Messages.t("player_only", "&cJoueur uniquement.")); return true; }
                if (!sender.hasPermission("crypto.user.trade")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                sender.sendMessage("§eUsage: /" + label + " transfer <joueur> <symbole> <montant> §7(placeholder)");
                return true;
            }
            case "top" -> {
                if (!sender.hasPermission("crypto.user.top")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                sender.sendMessage(Cryptocurrency.PREFIX + Messages.t("top.placeholder", "&eTop 5 investisseurs : &7(placeholder)"));
                return true;
            }
            case "convert" -> {
                if (!(sender instanceof Player)) { sender.sendMessage(Messages.t("player_only", "&cJoueur uniquement.")); return true; }
                if (!sender.hasPermission("crypto.user.trade")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                sender.sendMessage(Messages.f("usage.convert", "&eUsage: /{label} convert <symbole> <symbole2> <montant> &7(placeholder)", java.util.Map.of("label", label)));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("crypto.admin.reload")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                sender.sendMessage(Cryptocurrency.PREFIX + Messages.t("reload.ok", "&aConfiguration rechargée."));
                return true;
            }
            case "set", "add", "remove" -> {
                if (!sender.hasPermission("crypto.admin.edit")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                sender.sendMessage(Messages.f("usage.edit", "&eUsage: /{label} {sub} <joueur> <symbole> <montant> &7(placeholder)", java.util.Map.of("label", label, "sub", sub)));
                return true;
            }
            case "giveall" -> {
                if (!sender.hasPermission("crypto.admin.giveall")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                sender.sendMessage(Messages.f("usage.giveall", "&eUsage: /{label} giveall <symbole> <montant> &7(placeholder)", java.util.Map.of("label", label)));
                return true;
            }
            case "history" -> {
                UUID targetUuid;
                String targetName;
                
                if (args.length >= 2) {
                    // View someone else's history
                    if (!sender.hasPermission("crypto.admin.edit")) {
                        sender.sendMessage(Messages.t("no_permission", "&cVous n'avez pas la permission."));
                        return true;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    targetUuid = target.getUniqueId();
                    targetName = args[1];
                } else {
                    // View own history
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Messages.f("usage.history", "&eUsage: /{label} history <joueur>", 
                            java.util.Map.of("label", label)));
                        return true;
                    }
                    targetUuid = player.getUniqueId();
                    targetName = player.getName();
                }
                
                int limit = 10;
                if (args.length >= 3) {
                    try { limit = Math.max(1, Math.min(50, Integer.parseInt(args[2]))); } catch (Exception ignored) {}
                }
                
                var txns = plugin.getTransactionManager().getRecent(targetUuid, limit);
                
                sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("history.header", 
                    "&eHistorique de &f{player} &7({count} dernières transactions):", 
                    java.util.Map.of("player", targetName, "count", String.valueOf(txns.size()))));
                
                if (txns.isEmpty()) {
                    sender.sendMessage(Messages.t("history.empty", "&7Aucune transaction trouvée."));
                    return true;
                }
                
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm");
                for (var txn : txns) {
                    String time = txn.time.atZone(java.time.ZoneId.systemDefault()).format(fmt);
                    String typeColor = switch(txn.type) {
                        case "BUY" -> "§a";
                        case "SELL" -> "§c";
                        case "TRANSFER_IN" -> "§b";
                        case "TRANSFER_OUT" -> "§e";
                        case "CONVERT" -> "§d";
                        case "AIRDROP" -> "§6";
                        default -> "§7";
                    };
                    sender.sendMessage(String.format("§8[%s] %s%s §7- §f%s", time, typeColor, txn.type, txn.details));
                }
                
                return true;
            }
            case "chart" -> {
                if (!sender.hasPermission("crypto.user.chart")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                if (args.length < 2) { sender.sendMessage(Messages.f("usage.chart", "&eUsage: /{label} chart <symbole> [points] [range]", java.util.Map.of("label", label))); return true; }
                String sym = args[1].toUpperCase(Locale.ROOT);
                int points = plugin.getConfig().getInt("chart.default_points", 24);
                if (args.length >= 3) {
                    try { points = Math.max(12, Integer.parseInt(args[2])); } catch (Exception ignored) {}
                }
                String range = plugin.getConfig().getString("chart.default_range", "h1");
                if (args.length >= 4) range = args[3];

                var ts = plugin.getTimeseriesService();
                var series = ts != null ? ts.getSeries(sym, points, range) : java.util.Collections.<fr.jachou.cryptocurrency.services.PriceTimeseriesService.PricePoint>emptyList();
                if (series.isEmpty()) {
                    // Hydrate async: trigger price fetch and inform user
                    plugin.getPriceService().refreshAsync(sym);
                    sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("chart.hydrating", "&7Hydratation de la série en cours pour &e{symbol}&7... Réessaie dans quelques secondes.", java.util.Map.of("symbol", sym)));
                    return true;
                }
                java.util.List<Double> vals = series.stream().map(p -> p.price()).toList();
                fr.jachou.cryptocurrency.util.ChartFormatter.Stats st = fr.jachou.cryptocurrency.util.ChartFormatter.stats(vals);
                String glyphs = plugin.getConfig().getString("chart.sparkline_chars", "▁▂▃▄▅▆▇█");
                String spark = fr.jachou.cryptocurrency.util.ChartFormatter.sparkline(vals, glyphs);
                String header = String.format(java.util.Locale.US, "§e%s §7— %d pts — Min $%s / Max $%s — Δ%% %+.2f", sym, st.points, fr.jachou.cryptocurrency.util.ChartFormatter.fmt2(st.min), fr.jachou.cryptocurrency.util.ChartFormatter.fmt2(st.max), st.deltaPct);
                Double lastPrice = vals.isEmpty() ? null : vals.get(vals.size()-1);
                String line2 = "§8" + spark + (lastPrice != null ? " §7$" + fr.jachou.cryptocurrency.util.ChartFormatter.fmt2(lastPrice) : "");
                sender.sendMessage(Cryptocurrency.PREFIX + header);
                sender.sendMessage(line2);
                return true;
            }
            case "api" -> {
                if (!sender.hasPermission("crypto.admin.reload")) {
                    sender.sendMessage(Messages.t("no_permission", "&cVous n'avez pas la permission."));
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage(Messages.f("usage.api", "&eUsage: /{label} api <status|refresh>", 
                        java.util.Map.of("label", label)));
                    return true;
                }
                
                String subCmd = args[1].toLowerCase(Locale.ROOT);
                PriceService ps = plugin.getPriceService();
                
                if (subCmd.equals("status")) {
                    var status = ps.getApiStatus();
                    String statusColor = switch(status) {
                        case OK -> "§a";
                        case DEGRADED -> "§e";
                        case DOWN -> "§c";
                    };
                    sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("api.status", 
                        "&7État de l'API: {status}{status_name}", 
                        java.util.Map.of("status", statusColor, "status_name", status.name())));
                } else if (subCmd.equals("refresh")) {
                    if (args.length < 3) {
                        sender.sendMessage(Messages.f("usage.api.refresh", 
                            "&eUsage: /{label} api refresh <symbole>", 
                            java.util.Map.of("label", label)));
                        return true;
                    }
                    String sym = args[2].toUpperCase(Locale.ROOT);
                    ps.refresh(sym);
                    sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("api.refresh", 
                        "&7Rafraîchissement de &e{symbol} &7en cours...", 
                        java.util.Map.of("symbol", sym)));
                    
                    ps.getPriceUsd(sym).thenAccept(price -> runSync(() -> {
                        sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("api.refresh.success", 
                            "&a{symbol} &7= &a${price}", 
                            java.util.Map.of("symbol", sym, "price", fmt2(price))));
                    })).exceptionally(ex -> {
                        runSync(() -> sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("api.refresh.failed", 
                            "&cÉchec du rafraîchissement: {error}", 
                            java.util.Map.of("error", ex.getMessage() == null ? "" : ex.getMessage()))));
                        return null;
                    });
                } else {
                    sender.sendMessage(Messages.f("usage.api", "&eUsage: /{label} api <status|refresh>", 
                        java.util.Map.of("label", label)));
                }
                
                return true;
            }
            case "market" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(Messages.t("player_only", "&cJoueur uniquement.")); return true; }
                if (!sender.hasPermission("crypto.user.market")) { sender.sendMessage(Messages.t("no_permission", "&cVous n’avez pas la permission.")); return true; }
                plugin.getGuiManager().openMarket(p);
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    /**
     * Affiche le portefeuille de façon ASYNCHRONE :
     * - récupère tous les prix en parallèle
     * - calcule la valeur totale
     * - envoie le résultat sur le main thread
     */
    private void showBalanceAsync(CommandSender sender, UUID uuid, String name) {
        WalletManager wm = plugin.getWalletManager();
        Map<String, Double> wallet = wm.getWallet(uuid);

        if (wallet.isEmpty()) {
            String owner = (name == null ? uuid.toString() : name);
            sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("balance.empty", "&ePortefeuille de &f{player} &7: (vidé)", java.util.Map.of("player", owner)));
            return;
        }

        PriceService ps = plugin.getPriceService();

        // Pour chaque symbole, on prépare un Future prix
        Map<String, CompletableFuture<Double>> priceFutures = new HashMap<>();
        for (String sym : wallet.keySet()) {
            priceFutures.put(sym, ps.getPriceUsd(sym));
        }

        // Quand tous les prix sont prêts, on assemble l'affichage
        CompletableFuture<Void> all = CompletableFuture.allOf(priceFutures.values().toArray(new CompletableFuture[0]));
        all.thenRun(() -> {
            String owner = (name == null ? uuid.toString() : name);
            StringBuilder lines = new StringBuilder();
            double totalUsd = 0.0;

            for (Map.Entry<String, Double> entry : wallet.entrySet()) {
                String sym = entry.getKey();
                double qty = entry.getValue();
                Double priceUsd = priceFutures.get(sym).getNow(1.0); // getNow: non bloquant, car allOf est déjà résolu
                double usd = priceUsd * qty;
                totalUsd += usd;

                lines.append("§7").append(sym.toUpperCase(Locale.ROOT))
                        .append(" : §f").append(fmt4(qty))
                        .append(" §8(~$").append(fmt2(usd)).append(")\n");
            }

            String msgHeader = Cryptocurrency.PREFIX + Messages.f("balance.header", "&ePortefeuille de &f{player} :", java.util.Map.of("player", owner));
            String msgBody = lines.toString().trim();
            String msgTotal = Messages.f("balance.total", "&7Valeur totale : &a${total}", java.util.Map.of("total", fmt2(totalUsd)));

            runSync(() -> {
                sender.sendMessage(msgHeader);
                sender.sendMessage(msgBody);
                sender.sendMessage(msgTotal);
            });
        }).exceptionally(ex -> {
            runSync(() -> sender.sendMessage(Cryptocurrency.PREFIX + Messages.f("balance.error", "&cErreur lors du calcul du portefeuille : {error}", java.util.Map.of(
                    "error", ex.getMessage() == null ? "" : ex.getMessage()
            ))));
            return null;
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList(
                    "help","info","price","buy","sell","balance","transfer","top","convert",
                    "reload","set","add","remove","giveall","history","chart","api","market"
            ));
            return filter(list, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("price") || sub.equals("buy") || sub.equals("sell") || sub.equals("chart")) {
                return filter(Arrays.asList("BTC","ETH","SOL","DOGE"), args[1]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> base, String token) {
        String t = token.toLowerCase(Locale.ROOT);
        return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t)).collect(Collectors.toList());
    }

    /* ===================== Utilitaires ===================== */

    private static String fmt2(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    private static String fmt4(double v) {
        return String.format(Locale.US, "%,.4f", v);
    }

    private void runSync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }
}
