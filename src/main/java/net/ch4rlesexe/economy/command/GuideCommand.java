package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.guide.GuideGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class GuideCommand implements CommandExecutor {
    private final GuideGui gui;

    public GuideCommand(GuideGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only — use in-game /guide.");
            return true;
        }
        gui.open(player);
        return true;
    }
}
