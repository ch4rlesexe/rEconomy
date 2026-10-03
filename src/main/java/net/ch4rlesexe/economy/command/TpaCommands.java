package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.tpa.TpaService;
import net.ch4rlesexe.economy.util.CooldownService;
import net.ch4rlesexe.economy.util.TeleportWarmupService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TpaCommands implements CommandExecutor {
    private final TpaService tpa;
    private final CooldownService cooldowns;
    private final TeleportWarmupService warmup;

    public TpaCommands(TpaService tpa, CooldownService cooldowns, TeleportWarmupService warmup) {
        this.tpa = tpa;
        this.cooldowns = cooldowns;
        this.warmup = warmup;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        String cmd = command.getName().toLowerCase();
        switch (cmd) {
            case "tpa" -> {
                if (args.length < 1) {
                    player.sendMessage("§cUsage: /tpa <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target.equals(player)) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                if (!cooldowns.canStart(player, "tpa")) {
                    return true;
                }
                tpa.request(player, target, TpaService.Type.TO);
                return true;
            }
            case "tpahere" -> {
                if (args.length < 1) {
                    player.sendMessage("§cUsage: /tpahere <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target.equals(player)) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                if (!cooldowns.canStart(player, "tpa")) {
                    return true;
                }
                tpa.request(player, target, TpaService.Type.HERE);
                return true;
            }
            case "tpaccept", "tpyes" -> {
                TpaService.Request req = tpa.peek(player.getUniqueId());
                if (req == null) {
                    player.sendMessage("§cNo pending request.");
                    return true;
                }
                Player from = Bukkit.getPlayer(req.from());
                if (from == null) {
                    tpa.deny(player.getUniqueId());
                    player.sendMessage("§cRequester is offline.");
                    return true;
                }
                Player moving = req.type() == TpaService.Type.TO ? from : player;
                Location dest = req.type() == TpaService.Type.TO
                        ? player.getLocation().clone()
                        : from.getLocation().clone();
                if (!cooldowns.canStart(moving, "tpa")) {
                    if (!moving.equals(player)) {
                        player.sendMessage("§crEconomy §8» §7Can't accept — §f" + from.getName()
                                + " §7is in combat/cooldown.");
                    }
                    return true;
                }
                tpa.take(player.getUniqueId());
                player.sendMessage("§crEconomy §8» §7Teleport accepted.");
                from.sendMessage("§crEconomy §8» §7Teleport accepted.");
                warmup.begin(moving, "tpa", p -> {
                    p.teleportAsync(dest);
                    p.sendMessage("§crEconomy §8» §7Teleported.");
                });
                return true;
            }
            case "tpdeny", "tpno" -> {
                tpa.deny(player.getUniqueId());
                player.sendMessage("§crEconomy §8» §7Teleport denied.");
                return true;
            }
            case "tpcancel" -> {
                if (tpa.cancelOutgoing(player.getUniqueId(), true)) {
                    player.sendMessage("§crEconomy §8» §7Teleport request cancelled.");
                } else {
                    player.sendMessage("§cNo outgoing teleport request.");
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
