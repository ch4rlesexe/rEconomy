package net.ch4rlesexe.economy.listener;

import net.ch4rlesexe.economy.economy.EconomyService;
import net.ch4rlesexe.economy.namecolor.NameColorService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JoinListener implements Listener {
    private final EconomyService economy;
    private final NameColorService nameColors;

    public JoinListener(EconomyService economy, NameColorService nameColors) {
        this.economy = economy;
        this.nameColors = nameColors;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        economy.ensureAccount(player.getUniqueId(), player.getName());
        nameColors.invalidate(player.getUniqueId());
        nameColors.applyLater(player);
        String bal = economy.format(economy.getBalance(player.getUniqueId()));

        player.sendMessage("§c§lEconomy §8» §7Welcome");
        player.sendMessage("§8 • §fBalance: §a" + bal);
        player.sendMessage("§8 • §e/guide §7— full how-to menu (shops, teams, maps…)");
        player.sendMessage("§8 • §e/shop §7market  §8|  §e/ah §7auctions  §8|  §e/team §7claims");
        player.sendMessage("§8 • §e/home §7§o/tpa /spawn /rtp§7  §8|  §e/top §7leaderboards");
        if (!player.hasPlayedBefore()) {
            player.sendMessage("§c§lEconomy §8» §aWelcome! Starter coins are ready — open §e/guide§a.");
        }
    }
}
