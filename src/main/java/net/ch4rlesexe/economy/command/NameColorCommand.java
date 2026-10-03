package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.namecolor.NameColorGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class NameColorCommand implements CommandExecutor {
    private final NameColorGui gui;

    public NameColorCommand(NameColorGui gui) {
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
