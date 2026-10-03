package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.vanish.VanishService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class VanishCommand implements CommandExecutor, TabCompleter {
    private final VanishService vanish;

    public VanishCommand(VanishService vanish) {
        this.vanish = vanish;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("reconomy.vanish")) {
            player.sendMessage("§crEconomy §8» §7No permission.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            if (!player.hasPermission("reconomy.vanish.see")) {
                player.sendMessage("§crEconomy §8» §7No permission.");
                return true;
            }
            List<String> names = new ArrayList<>();
            for (UUID id : vanish.vanishedIds()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) names.add(p.getName());
            }
            if (names.isEmpty()) {
                player.sendMessage("§crEconomy §8» §7Nobody is vanished.");
            } else {
                player.sendMessage("§crEconomy §8» §7Vanished (§f" + names.size() + "§7): §f"
                        + String.join("§8, §f", names));
            }
            return true;
        }

        String mode = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "on", "enable", "true" -> {
                vanish.enable(player);
                player.sendMessage("§crEconomy §8» §aVanish ON §8— §7hidden from players.");
            }
            case "off", "disable", "false" -> {
                vanish.disable(player);
                player.sendMessage("§crEconomy §8» §eVanish OFF §8— §7you are visible.");
            }
            default -> {
                boolean now = vanish.toggle(player);
                if (now) {
                    player.sendMessage("§crEconomy §8» §aVanish ON §8— §7hidden from players.");
                } else {
                    player.sendMessage("§crEconomy §8» §eVanish OFF §8— §7you are visible.");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("reconomy.vanish")) return out;
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("on", "off", "toggle", "list")) {
                if (s.startsWith(p)) out.add(s);
            }
        }
        return out;
    }
}
