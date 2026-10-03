package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.coinflip.CoinflipGui;
import net.ch4rlesexe.economy.coinflip.CoinflipService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class CoinflipCommands implements CommandExecutor, TabCompleter {
    private final CoinflipService flips;
    private final CoinflipGui gui;

    public CoinflipCommands(CoinflipService flips, CoinflipGui gui) {
        this.flips = flips;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            gui.openMain(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui", "menu" -> gui.openMain(player);
            case "browse", "list" -> gui.openBrowse(player);
            case "cancel" -> flips.cancel(player);
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /coinflip create <amount> <heads|tails>");
                    player.sendMessage("§7Or just §f/coinflip §7for the menu.");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1].replace(",", "").replace("$", ""));
                    flips.create(player, amount, args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid amount.");
                }
            }
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /coinflip join <id>");
                    return true;
                }
                try {
                    gui.joinAndAnimate(player, Long.parseLong(args[1]));
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid id.");
                }
            }
            default -> {

                try {
                    double amount = Double.parseDouble(args[0].replace(",", "").replace("$", ""));
                    String side = args.length >= 2 ? args[1] : (Math.random() < 0.5 ? "HEADS" : "TAILS");
                    if (args.length < 2) {

                        if (amount < flips.minAmount()) {
                            player.sendMessage("§cMinimum is " + flips.minAmount());
                            return true;
                        }
                        gui.openSide(player, amount);
                    } else {
                        flips.create(player, amount, side);
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§c/coinflip §8| §ccreate <amt> <side> §8| §cjoin <id> §8| §ccancel");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return List.of("gui", "create", "join", "browse", "cancel", "10", "50", "100", "500")
                    .stream().filter(s -> s.startsWith(p)).collect(Collectors.toCollection(ArrayList::new));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return List.of("10", "50", "100", "250", "500", "1000");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            String p = args[2].toLowerCase(Locale.ROOT);
            return List.of("heads", "tails").stream().filter(s -> s.startsWith(p))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("join") && !args[0].equalsIgnoreCase("create")) {

            try {
                Double.parseDouble(args[0]);
                String p = args[1].toLowerCase(Locale.ROOT);
                return List.of("heads", "tails").stream().filter(s -> s.startsWith(p))
                        .collect(Collectors.toCollection(ArrayList::new));
            } catch (NumberFormatException ignored) {
            }
        }
        return List.of();
    }
}
