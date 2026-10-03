package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.homes.HomeService;
import net.ch4rlesexe.economy.util.TeleportWarmupService;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class HomeCommands implements CommandExecutor {
    private final HomeService homes;
    private final TeleportWarmupService warmup;

    public HomeCommands(HomeService homes, TeleportWarmupService warmup) {
        this.homes = homes;
        this.warmup = warmup;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("reconomy.homes.use")) {
            player.sendMessage("§crEconomy §8» §7No permission to use homes.");
            return true;
        }
        String cmd = command.getName().toLowerCase();
        switch (cmd) {
            case "sethome" -> {
                String name = args.length > 0 ? args[0] : "home";
                if (!homes.setHome(player, name)) {
                    player.sendMessage("§cHome limit reached (" + homes.limitFor(player) + ").");
                    return true;
                }
                player.sendMessage("§crEconomy §8» §7Home §f" + name.toLowerCase() + " §7set.");
                return true;
            }
            case "home" -> {
                String name = args.length > 0 ? args[0] : "home";
                Location loc = homes.get(player.getUniqueId(), name);
                if (loc == null) {
                    player.sendMessage("§cHome not found.");
                    return true;
                }
                final String homeName = name.toLowerCase();
                warmup.begin(player, "home", p -> {
                    p.teleportAsync(loc);
                    p.sendMessage("§crEconomy §8» §7Teleported to §f" + homeName);
                });
                return true;
            }
            case "delhome", "deletehome" -> {
                if (args.length < 1) {
                    player.sendMessage("§cUsage: /delhome <name>");
                    return true;
                }
                if (homes.delete(player.getUniqueId(), args[0])) {
                    player.sendMessage("§crEconomy §8» §7Deleted home §f" + args[0].toLowerCase());
                } else {
                    player.sendMessage("§cHome not found.");
                }
                return true;
            }
            case "homes" -> {
                List<String> list = homes.list(player.getUniqueId());
                int limit = homes.limitFor(player);
                if (list.isEmpty()) {
                    player.sendMessage("§crEconomy §8» §7No homes set. §8(§7limit §f" + limit + "§8)");
                } else {
                    player.sendMessage("§crEconomy §8» §7Homes §8(§f" + list.size() + "§7/§f" + limit + "§8): §f"
                            + String.join("§7, §f", list));
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
