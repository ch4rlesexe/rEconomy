package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.economy.EconomyService;
import net.ch4rlesexe.economy.government.GovernmentService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;

public final class GovernmentCommands implements CommandExecutor, TabCompleter {
    private static final int LOG_PAGE_SIZE = 8;
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("MM/dd HH:mm", Locale.US);

    static {
        TIME.setTimeZone(TimeZone.getDefault());
    }

    private final GovernmentService government;
    private final EconomyService economy;

    public GovernmentCommands(GovernmentService government, EconomyService economy) {
        this.government = government;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (!government.canAccess(player)) {
            player.sendMessage("§crEconomy §8» §7No permission (§freconomy.government.access§7).");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            player.sendMessage("§crEconomy §8» §7Government treasury: §a" + economy.format(government.balance()));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "bal", "balance", "treasury" -> {
                player.sendMessage("§crEconomy §8» §7Government treasury: §a"
                        + economy.format(government.balance()));
                yield true;
            }
            case "withdraw", "take", "claim" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /gov withdraw <amount>");
                    yield true;
                }
                Double amount = parseAmount(args[1]);
                if (amount == null || amount <= 0) {
                    player.sendMessage("§cInvalid amount.");
                    yield true;
                }
                if (!government.withdrawTo(player, amount)) {
                    player.sendMessage("§crEconomy §8» §7Could not withdraw (treasury may be short).");
                    yield true;
                }
                player.sendMessage("§crEconomy §8» §7Withdrew §a" + economy.format(amount)
                        + " §7from Government. Treasury: §f" + economy.format(government.balance()));
                yield true;
            }
            case "deposit", "add", "put" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /gov deposit <amount>");
                    yield true;
                }
                Double amount = parseAmount(args[1]);
                if (amount == null || amount <= 0) {
                    player.sendMessage("§cInvalid amount.");
                    yield true;
                }
                if (!government.depositFrom(player, amount)) {
                    player.sendMessage("§crEconomy §8» §7Could not deposit (check your balance).");
                    yield true;
                }
                player.sendMessage("§crEconomy §8» §7Deposited §a" + economy.format(amount)
                        + " §7into Government. Treasury: §f" + economy.format(government.balance()));
                yield true;
            }
            case "pay", "send", "give" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /gov pay <player> <amount> [note]");
                    yield true;
                }
                OfflinePlayer target = resolvePlayer(args[1]);
                if (target == null || target.getUniqueId() == null
                        || (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline())) {
                    player.sendMessage("§cPlayer not found.");
                    yield true;
                }
                Double amount = parseAmount(args[2]);
                if (amount == null || amount <= 0) {
                    player.sendMessage("§cInvalid amount.");
                    yield true;
                }
                String note = args.length > 3
                        ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                        : null;
                if (!government.pay(player, target, amount, note)) {
                    player.sendMessage("§crEconomy §8» §7Pay failed (treasury may be short).");
                    yield true;
                }
                String display = target.getName() != null ? target.getName() : args[1];
                player.sendMessage("§crEconomy §8» §7Paid §a" + economy.format(amount)
                        + " §7from Government to §f" + display
                        + "§7. Treasury: §f" + economy.format(government.balance()));
                yield true;
            }
            case "log", "logs", "history", "ledger" -> {
                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {
                        page = 1;
                    }
                }
                showLog(player, page);
                yield true;
            }
            case "help", "?" -> {
                sendHelp(player);
                yield true;
            }
            default -> {
                sendHelp(player);
                yield true;
            }
        };
    }

    private void showLog(Player player, int page) {
        int total = government.ledgerCount();
        int pages = Math.max(1, (int) Math.ceil(total / (double) LOG_PAGE_SIZE));
        if (page > pages) page = pages;
        int offset = (page - 1) * LOG_PAGE_SIZE;
        List<GovernmentService.LedgerEntry> entries = government.recent(LOG_PAGE_SIZE, offset);
        player.sendMessage("§crEconomy §8» §7Government log §f" + page + "§7/§f" + pages
                + " §8(§7" + total + " entries§8)");
        if (entries.isEmpty()) {
            player.sendMessage("§8 • §7No transactions yet.");
            return;
        }
        for (GovernmentService.LedgerEntry e : entries) {
            String when = e.createdAt() == null ? "?" : TIME.format(e.createdAt());
            String who = e.actorName() == null ? "system" : e.actorName();
            String line = switch (e.type()) {
                case "collect" -> "§a+" + economy.format(e.amount()) + " §7tax §8" + e.reason();
                case "deposit" -> "§a+" + economy.format(e.amount()) + " §7deposit by §f" + who;
                case "withdraw" -> "§c-" + economy.format(e.amount()) + " §7withdraw by §f" + who;
                case "pay" -> "§c-" + economy.format(e.amount()) + " §7pay §f"
                        + (e.targetName() == null ? "?" : e.targetName())
                        + " §8by §7" + who
                        + (e.reason() != null && !e.reason().equals("pay") ? " §8· §7" + e.reason() : "");
                default -> "§7" + e.type() + " §f" + economy.format(e.amount()) + " §8" + e.reason();
            };
            player.sendMessage("§8" + when + " §7#" + e.id() + " " + line
                    + " §8(bal §7" + economy.format(e.balanceAfter()) + "§8)");
        }
        if (page < pages) {
            player.sendMessage("§7Next: §f/gov log " + (page + 1));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§crEconomy §8» §7Government commands:");
        player.sendMessage("§8 • §f/gov §7— treasury balance");
        player.sendMessage("§8 • §f/gov bal");
        player.sendMessage("§8 • §f/gov withdraw <amount> §7— take from treasury");
        player.sendMessage("§8 • §f/gov deposit <amount> §7— add to treasury");
        player.sendMessage("§8 • §f/gov pay <player> <amount> [note] §7— pay from treasury");
        player.sendMessage("§8 • §f/gov log [page] §7— full transaction history");
    }

    private static Double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", "").replace("$", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player player) || !government.canAccess(player)) return out;
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("bal", "withdraw", "deposit", "pay", "log", "help")) {
                if (s.startsWith(p)) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pay")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            out.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .collect(Collectors.toList()));
        }
        return out;
    }
}
