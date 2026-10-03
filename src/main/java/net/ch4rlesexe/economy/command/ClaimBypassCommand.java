package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.claim.ClaimBypassService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ClaimBypassCommand implements CommandExecutor {
    private final ClaimBypassService bypass;

    public ClaimBypassCommand(ClaimBypassService bypass) {
        this.bypass = bypass;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Boolean now = bypass.toggle(player);
        if (now == null) {
            player.sendMessage("§crEconomy §8» §7No permission. Need §freconomy.claims.bypass§7.");
            return true;
        }
        if (now) {
            player.sendMessage("§crEconomy §8» §aClaim bypass ON §8— §7you can interact with others' claims.");
        } else {
            player.sendMessage("§crEconomy §8» §eClaim bypass OFF §8— §7claim protection applies to you.");
        }
        return true;
    }
}
