package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.leaderboard.LeaderboardGui;
import net.ch4rlesexe.economy.leaderboard.LeaderboardService;
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

public final class TopCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUGGESTIONS = List.of(
            "money", "playtime", "kills", "deaths", "kdr", "streak",
            "mobs", "crops", "teams", "claims", "wealth"
    );

    private final LeaderboardGui gui;
    private final LeaderboardService boards;

    public TopCommand(LeaderboardGui gui, LeaderboardService boards) {
        this.gui = gui;
        this.boards = boards;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            LeaderboardService.Category cat = args.length >= 1
                    ? LeaderboardService.Category.fromArg(args[0])
                    : LeaderboardService.Category.MONEY;
            if (cat == null) {
                sender.sendMessage("Usage: /top <money|playtime|kills|deaths|kdr|streak|mobs|crops|teams|claims|wealth>");
                return true;
            }
            var entries = boards.top(cat, 10);
            sender.sendMessage("Top " + cat.title() + ":");
            int i = 1;
            for (var e : entries) {
                sender.sendMessage(i + ". " + e.name() + " - " + e.detail());
                i++;
            }
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
            gui.openMain(player);
            return true;
        }

        String a0 = args[0].toLowerCase(Locale.ROOT);
        if (a0.equals("mobs") || a0.equals("mob")) {
            gui.openMobHub(player);
            return true;
        }
        if (a0.equals("crops") || a0.equals("farm") || a0.equals("farming")) {
            gui.openCropHub(player);
            return true;
        }

        LeaderboardService.Category cat = LeaderboardService.Category.fromArg(args[0]);
        if (cat == null) {
            player.sendMessage("§crEconomy §8» §7Unknown board. Try §f/top §7or:");
            player.sendMessage("§8 money, playtime, kills, deaths, kdr, streak, mobs, crops, teams, claims, wealth");
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("chat")) {
            gui.sendChat(player, cat);
            return true;
        }

        if (cat == LeaderboardService.Category.MOB_KILLS) {
            gui.openMobHub(player);
            return true;
        }
        if (cat == LeaderboardService.Category.CROPS) {
            gui.openCropHub(player);
            return true;
        }

        gui.openCategory(player, cat);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return SUGGESTIONS.stream().filter(s -> s.startsWith(p)).collect(Collectors.toCollection(ArrayList::new));
        }
        if (args.length == 2) {
            return Arrays.asList("chat", "gui");
        }
        return List.of();
    }
}
