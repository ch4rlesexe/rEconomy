package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.rtp.RtpGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RtpCommand implements CommandExecutor {
    private final RtpGui gui;

    public RtpCommand(RtpGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        gui.open(player);
        return true;
    }
}
