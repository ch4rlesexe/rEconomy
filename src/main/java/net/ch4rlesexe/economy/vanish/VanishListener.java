package net.ch4rlesexe.economy.vanish;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class VanishListener implements Listener {
    private final VanishService vanish;

    public VanishListener(VanishService vanish) {
        this.vanish = vanish;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (vanish.isVanished(player)) {
            event.joinMessage(null);
            player.sendMessage("§crEconomy §8» §7You joined §avanished§7. §8(§f/vanish off§8)");
        }
        vanish.handleJoin(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        if (vanish.isVanished(event.getPlayer())) {
            event.quitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && vanish.isVanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && vanish.isVanished(player)) {
            event.setCancelled(true);
        }
    }
}
