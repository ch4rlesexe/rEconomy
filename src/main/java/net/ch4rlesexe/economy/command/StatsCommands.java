package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.stats.ActivityStatsService;
import net.ch4rlesexe.economy.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class StatsCommands implements CommandExecutor {
    private final StatsService stats;
    private final ActivityStatsService activity;

    public StatsCommands(StatsService stats, ActivityStatsService activity) {
        this.stats = stats;
        this.activity = activity;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("killtop") || cmd.equals("statstop")) {
            List<StatsService.Stats> top = stats.topKills(10);
            sender.sendMessage("§crEconomy §8» §fKill Top");
            if (top.isEmpty()) {
                sender.sendMessage("§7No stats yet.");
                return true;
            }
            int i = 1;
            for (StatsService.Stats s : top) {
                sender.sendMessage("§8" + i + ". §f" + s.name() + " §7— §c" + s.kills() + "K §8/ §7"
                        + s.deaths() + "D §8(§f" + s.kdr() + " KDR§8)");
                i++;
            }
            sender.sendMessage("§8Use §f/top §8for all leaderboards (money, playtime, mobs, crops...).");
            return true;
        }


        UUID target;
        String display;
        if (args.length >= 1) {
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) {
                activity.syncFromBukkit(online);
                target = online.getUniqueId();
                display = online.getName();
            } else {
                OfflinePlayer off = Bukkit.getOfflinePlayer(args[0]);
                if (!off.hasPlayedBefore() && !off.isOnline()) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                target = off.getUniqueId();
                display = off.getName() != null ? off.getName() : args[0];
            }
        } else if (sender instanceof Player player) {
            activity.syncFromBukkit(player);
            target = player.getUniqueId();
            display = player.getName();
        } else {
            sender.sendMessage("§cUsage: /stats <player>");
            return true;
        }

        StatsService.Stats s = stats.get(target);
        sender.sendMessage("§crEconomy §8» §f" + display + " §7stats");
        sender.sendMessage("§8 • §7Kills: §f" + s.kills());
        sender.sendMessage("§8 • §7Deaths: §f" + s.deaths());
        sender.sendMessage("§8 • §7KDR: §f" + s.kdr());
        sender.sendMessage("§8 • §7Combat logs: §f" + s.combatLogs());
        sender.sendMessage("§8 • §7Streak: §f" + s.killstreak() + " §8(best §f" + s.bestKillstreak() + "§8)");
        sender.sendMessage("§8 • §7Playtime: §f" + ActivityStatsService.formatPlaytime(activity.playtimeTicks(target)));
        sender.sendMessage("§8 • §7Mob kills: §f" + activity.mobKills(target));
        sender.sendMessage("§8 • §7Crops farmed: §f" + activity.cropHarvests(target));
        sender.sendMessage("§8Use §f/top §8for leaderboards.");
        return true;
    }
}
