package net.ch4rlesexe.economy.auction;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AuctionCommands implements CommandExecutor {
    private final AuctionService auctions;

    public AuctionCommands(AuctionService auctions) {
        this.auctions = auctions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            if (args.length < 2) {
                player.sendMessage("§crEconomy §8» §7Usage: §f/ah list <price>");
                return true;
            }
            try {
                auctions.list(player, Double.parseDouble(args[1]));
            } catch (NumberFormatException e) {
                player.sendMessage("§crEconomy §8» §7Invalid price.");
            }
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("cancel") || args[0].equalsIgnoreCase("remove"))) {
            if (args.length < 2) {
                player.sendMessage("§crEconomy §8» §7Usage: §f/ah cancel <id>");
                player.sendMessage("§7Or open §f/ah §7→ My Listings → right-click.");
                return true;
            }
            try {
                auctions.cancel(player, Long.parseLong(args[1].replace("#", "")));
            } catch (NumberFormatException e) {
                player.sendMessage("§crEconomy §8» §7Invalid id.");
            }
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("mine") || args[0].equalsIgnoreCase("my"))) {
            auctions.openMine(player);
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?"))) {
            player.sendMessage("§crEconomy §8» §fAuction House");
            player.sendMessage("§8 • §e/ah §7open listings");
            player.sendMessage("§8 • §e/ah list <price> §7list held item");
            player.sendMessage("§8 • §e/ah mine §7your listings");
            player.sendMessage("§8 • §e/ah cancel <id> §7cancel your listing");
            player.sendMessage("§8 • §7In GUI: §fright-click §7your item to cancel");
            return true;
        }
        auctions.openGui(player);
        return true;
    }
}
