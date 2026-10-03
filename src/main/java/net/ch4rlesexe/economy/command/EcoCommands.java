package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class EcoCommands implements CommandExecutor, TabCompleter {
    private final EconomyService economy;

    public EcoCommands(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("reconomy.economy.admin")) {
            sender.sendMessage("§crEconomy §8» §7No permission.");
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("givemoney") || name.equals("givecoins")) {
            return give(sender, args, 0);
        }

        if (args.length < 1) {
            sender.sendMessage("§crEconomy §8» §7Usage:");
            sender.sendMessage("§8 • §f/eco give <player> <amount>");
            sender.sendMessage("§8 • §f/eco take <player> <amount>");
            sender.sendMessage("§8 • §f/eco set <player> <amount>");
            sender.sendMessage("§8 • §f/eco bal <player>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "give", "add", "deposit" -> give(sender, args, 1);
            case "take", "remove", "withdraw" -> take(sender, args, 1);
            case "set" -> set(sender, args, 1);
            case "bal", "balance", "check" -> bal(sender, args, 1);
            default -> {
                sender.sendMessage("§cUnknown subcommand. Use give/take/set/bal.");
                yield true;
            }
        };
    }

    private boolean give(CommandSender sender, String[] args, int offset) {
        if (args.length < offset + 2) {
            sender.sendMessage("§cUsage: /eco give <player> <amount>");
            return true;
        }
        OfflinePlayer target = resolve(args[offset]);
        if (target == null || target.getUniqueId() == null
                || (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }
        Double amount = parseAmount(args[offset + 1]);
        if (amount == null || amount <= 0) {
            sender.sendMessage("§cInvalid amount.");
            return true;
        }
        UUID id = target.getUniqueId();
        String display = target.getName() != null ? target.getName() : id.toString();
        economy.ensureAccount(id, display);
        if (!economy.deposit(id, amount, "admin-give:" + sender.getName())) {
            sender.sendMessage("§cCould not deposit.");
            return true;
        }
        sender.sendMessage("§crEconomy §8» §7Gave §f" + economy.format(amount) + " §7to §f" + display
                + "§7. New balance: §f" + economy.format(economy.getBalance(id)));
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage("§crEconomy §8» §7You received §f" + economy.format(amount)
                    + " §7from an admin.");
        }
        return true;
    }

    private boolean take(CommandSender sender, String[] args, int offset) {
        if (args.length < offset + 2) {
            sender.sendMessage("§cUsage: /eco take <player> <amount>");
            return true;
        }
        OfflinePlayer target = resolve(args[offset]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }
        Double amount = parseAmount(args[offset + 1]);
        if (amount == null || amount <= 0) {
            sender.sendMessage("§cInvalid amount.");
            return true;
        }
        UUID id = target.getUniqueId();
        String display = target.getName() != null ? target.getName() : id.toString();
        economy.ensureAccount(id, display);
        double bal = economy.getBalance(id);
        double remove = Math.min(bal, amount);
        if (remove <= 0) {
            sender.sendMessage("§c" + display + " has no coins to take.");
            return true;
        }
        if (!economy.withdraw(id, remove, "admin-take:" + sender.getName())) {

            economy.setBalance(id, Math.max(0, bal - remove), "admin-take:" + sender.getName());
        }
        sender.sendMessage("§crEconomy §8» §7Took §f" + economy.format(remove) + " §7from §f" + display
                + "§7. New balance: §f" + economy.format(economy.getBalance(id)));
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage("§crEconomy §8» §7An admin removed §f" + economy.format(remove)
                    + " §7from your balance.");
        }
        return true;
    }

    private boolean set(CommandSender sender, String[] args, int offset) {
        if (args.length < offset + 2) {
            sender.sendMessage("§cUsage: /eco set <player> <amount>");
            return true;
        }
        OfflinePlayer target = resolve(args[offset]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }
        Double amount = parseAmount(args[offset + 1]);
        if (amount == null || amount < 0) {
            sender.sendMessage("§cInvalid amount.");
            return true;
        }
        UUID id = target.getUniqueId();
        String display = target.getName() != null ? target.getName() : id.toString();
        economy.ensureAccount(id, display);
        if (!economy.setBalance(id, amount, "admin-set:" + sender.getName())) {
            sender.sendMessage("§cCould not set balance.");
            return true;
        }
        sender.sendMessage("§crEconomy §8» §7Set §f" + display + "§7's balance to §f" + economy.format(amount));
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage("§crEconomy §8» §7Your balance was set to §f"
                    + economy.format(amount) + " §7by an admin.");
        }
        return true;
    }

    private boolean bal(CommandSender sender, String[] args, int offset) {
        if (args.length < offset + 1) {
            sender.sendMessage("§cUsage: /eco bal <player>");
            return true;
        }
        OfflinePlayer target = resolve(args[offset]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }
        String display = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        economy.ensureAccount(target.getUniqueId(), display);
        sender.sendMessage("§crEconomy §8» §7" + display + ": §f"
                + economy.format(economy.getBalance(target.getUniqueId())));
        return true;
    }

    private static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline;
    }

    private static Double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", "").replace("$", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("reconomy.economy.admin")) return out;
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("givemoney") || cmd.equals("givecoins")) {
            if (args.length == 1) return playerNames(args[0]);
            if (args.length == 2) {
                out.add("1000");
                out.add("10000");
                out.add("100000");
            }
            return out;
        }
        if (args.length == 1) {
            for (String s : List.of("give", "take", "set", "bal")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
            return out;
        }
        if (args.length == 2) return playerNames(args[1]);
        if (args.length == 3) {
            out.add("1000");
            out.add("10000");
            out.add("100000");
        }
        return out;
    }

    private static List<String> playerNames(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(player.getName());
            }
        }
        return out;
    }
}
