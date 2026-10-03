package net.ch4rlesexe.economy.livingshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class LivingShopCommands implements CommandExecutor, TabCompleter {
    private final ShopEconomyService living;

    public LivingShopCommands(ShopEconomyService living) {
        this.living = living;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("reconomy.livingshop.admin")) {
            sender.sendMessage("§crEconomy §8» §7No permission.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "export" -> {
                Path path = args.length >= 2
                        ? Path.of(String.join(" ", Arrays.copyOfRange(args, 1, args.length)))
                        : living.defaultCsvPath();
                try {
                    living.seedFromCatalogIfEmpty();
                    Path out = living.exportCsv(path);
                    sender.sendMessage("§arEconomy §8» §7Exported shop CSV → §f" + out.toAbsolutePath());
                } catch (Exception e) {
                    sender.sendMessage("§cExport failed: " + e.getMessage());
                }
            }
            case "apply" -> {
                Path path = args.length >= 2
                        ? Path.of(String.join(" ", Arrays.copyOfRange(args, 1, args.length)))
                        : living.defaultCsvPath();
                ShopEconomyService.ApplyResult r = living.applyCsv(path);
                if (!r.ok()) {
                    sender.sendMessage("§cApply failed: " + r.error);
                    return true;
                }
                sender.sendMessage("§arEconomy §8» §7Applied CSV — updated §f" + r.updated
                        + "§7, skipped §f" + r.skipped + "§7, week §f" + r.week);
                int shown = 0;
                for (String w : r.warnings) {
                    if (shown++ >= 8) {
                        sender.sendMessage("§8… §7and " + (r.warnings.size() - 8) + " more warnings");
                        break;
                    }
                    sender.sendMessage("§8 • §7" + w);
                }
            }
            case "status" -> {
                sender.sendMessage("§crEconomy §8» §fLiving shop");
                sender.sendMessage("§8 • §7Enabled: §f" + living.isEnabled());
                sender.sendMessage("§8 • §7Week: §f" + living.currentWeek());
                sender.sendMessage("§8 • §7Tracked: §f" + living.allStates().size()
                        + " §8| §7Capped: §f" + living.cappedCount());
                sender.sendMessage("§8 • §7CSV: §f" + living.defaultCsvPath().toAbsolutePath());
            }
            case "seed" -> {
                living.seedFromCatalogIfEmpty();
                living.reloadCache();
                sender.sendMessage("§arEconomy §8» §7Catalog seed/reload done. Tracked: §f" + living.allStates().size());
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§crEconomy §8» §f/livingshop");
        sender.sendMessage("§8 • §f/livingshop export [path] §7— dump all shop items to CSV");
        sender.sendMessage("§8 • §f/livingshop apply [path] §7— apply Buy/Sell/Stock from CSV");
        sender.sendMessage("§8 • §f/livingshop status");
        sender.sendMessage("§8 • §7Stock sets weekly inventory; Supply §f-1 §7= unlimited");
        sender.sendMessage("§8 • §7Columns: Shop…Week, Player Count, To Date");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("reconomy.livingshop.admin")) return List.of();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Arrays.asList("export", "apply", "status", "seed").stream()
                    .filter(s -> s.startsWith(p))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return List.of();
    }
}
