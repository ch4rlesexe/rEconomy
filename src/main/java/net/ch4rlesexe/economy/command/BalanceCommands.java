package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BalanceCommands implements CommandExecutor {
    private final EconomyService economy;

    public BalanceCommands(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        switch (name) {
            case "bal", "balance", "money" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (args.length == 1 && sender.hasPermission("reconomy.economy.admin")) {
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        sender.sendMessage("§cPlayer not found.");
                        return true;
                    }
                    sender.sendMessage("§crEconomy §8» §7" + target.getName() + ": §f"
                            + economy.format(economy.getBalance(target.getUniqueId())));
                    return true;
                }
                economy.ensureAccount(player.getUniqueId(), player.getName());
                player.sendMessage("§crEconomy §8» §7Balance: §f"
                        + economy.format(economy.getBalance(player.getUniqueId())));
                return true;
            }
            case "pay" -> {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /pay <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage("§cPlayer not online.");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid amount.");
                    return true;
                }
                if (amount <= 0) {
                    player.sendMessage("§cAmount must be positive.");
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage("§cYou cannot pay yourself.");
                    return true;
                }
                if (!economy.transfer(player.getUniqueId(), target.getUniqueId(), amount, "pay")) {
                    player.sendMessage("§cInsufficient funds.");
                    return true;
                }
                player.sendMessage("§crEconomy §8» §7Paid §f" + economy.format(amount) + " §7to §f" + target.getName());
                target.sendMessage("§crEconomy §8» §7Received §f" + economy.format(amount) + " §7from §f" + player.getName());
                return true;
            }
            case "baltop", "balancetop", "moneytop" -> {
                sender.sendMessage("§crEconomy §8» §fTop balances");
                int i = 1;
                for (EconomyService.TopEntry e : economy.top(10)) {
                    sender.sendMessage("§8" + i++ + ". §f" + e.name() + " §7- §c" + economy.format(e.balance()));
                }
                sender.sendMessage("§8More boards: §f/top");
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
