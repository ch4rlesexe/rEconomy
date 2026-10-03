package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.wager.WagerGui;
import net.ch4rlesexe.economy.wager.WagerService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class WagerCommands implements CommandExecutor, TabCompleter {
    private final WagerService wagers;
    private final WagerGui gui;

    public WagerCommands(WagerService wagers, WagerGui gui) {
        this.wagers = wagers;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) {
            gui.openMain(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui", "menu" -> gui.openMain(player);
            case "accept" -> wagers.accept(player);
            case "deny" -> wagers.deny(player);
            case "win" -> wagers.declareWinner(player, player.getUniqueId(), false);
            case "cancel" -> {
                if (wagers.cancel(player)) player.sendMessage("§crEconomy §8» §7Wager cancelled.");
                else player.sendMessage("§cNo wager to cancel.");
            }
            case "resolve" -> {
                if (!player.hasPermission("reconomy.economy.admin") || args.length < 2) {
                    player.sendMessage("§c/wager resolve <winner>");
                    return true;
                }
                Player winner = Bukkit.getPlayerExact(args[1]);
                if (winner == null) {
                    player.sendMessage("§cPlayer offline.");
                    return true;
                }
                wagers.declareWinner(player, winner.getUniqueId(), true);
            }
            default -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /wager [player] [amount]  §8or §f/wager §7for menu");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target.equals(player)) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                try {
                    wagers.challenge(player, target, Double.parseDouble(args[1]));
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid amount.");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> base = new ArrayList<>(List.of("gui", "accept", "deny", "win", "cancel"));
            if (sender.hasPermission("reconomy.economy.admin")) base.add("resolve");
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player self && online.equals(self)) continue;
                base.add(online.getName());
            }
            return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resolve")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (args.length == 2) {
            return List.of("10", "50", "100", "250", "500", "1000");
        }
        return List.of();
    }
}
