package net.ch4rlesexe.economy.command;

import fr.maxlego08.head.api.enums.HeadCategory;
import net.ch4rlesexe.economy.heads.HeadsShop;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class HeadsCommand implements CommandExecutor, TabCompleter {
    private final HeadsShop shop;

    public HeadsCommand(HeadsShop shop) {
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("search")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /heads search <query>");
                return true;
            }
            String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            shop.openSearch(player, query, 1);
            return true;
        }
        if (args.length >= 1) {
            HeadCategory cat = null;
            for (HeadCategory c : HeadCategory.values()) {
                if (c.name().equalsIgnoreCase(args[0]) || c.getName().equalsIgnoreCase(args[0])) {
                    cat = c;
                    break;
                }
            }
            if (cat != null) {
                shop.openCategory(player, cat, 1);
                return true;
            }
        }
        shop.openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            if ("search".startsWith(p)) out.add("search");
            for (HeadCategory c : HeadCategory.values()) {
                String n = c.getName();
                if (n.startsWith(p) || c.name().toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(n);
                }
            }
        }
        return out.stream().distinct().collect(Collectors.toList());
    }
}
