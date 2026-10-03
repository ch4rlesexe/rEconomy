package net.ch4rlesexe.economy.market;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MarketCommands implements CommandExecutor {
    private final MarketService market;

    public MarketCommands(MarketService market) {
        this.market = market;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("reconomy.market.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§c/market admin setprice|setstock|setbase|freeze|unfreeze <material> [value]");
                return true;
            }
            String sub = args[1].toLowerCase();
            String mat = args[2].toUpperCase();
            switch (sub) {
                case "setprice" -> {
                    market.setPrice(mat, Double.parseDouble(args[3]), true);
                    sender.sendMessage("§aPrice updated.");
                }
                case "setstock" -> {
                    market.setStock(mat, Integer.parseInt(args[3]));
                    sender.sendMessage("§aStock updated.");
                }
                case "setbase" -> {
                    market.setBase(mat, Double.parseDouble(args[3]));
                    sender.sendMessage("§aBase updated.");
                }
                case "freeze" -> {
                    market.setFrozen(mat, true);
                    sender.sendMessage("§aFrozen.");
                }
                case "unfreeze" -> {
                    market.setFrozen(mat, false);
                    sender.sendMessage("§aUnfrozen.");
                }
                default -> sender.sendMessage("§cUnknown admin subcommand.");
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        market.openGui(player);
        return true;
    }
}
