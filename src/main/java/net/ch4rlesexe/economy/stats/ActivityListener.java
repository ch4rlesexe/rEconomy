package net.ch4rlesexe.economy.stats;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ActivityListener implements Listener {
    private final ActivityStatsService activity;

    public ActivityListener(ActivityStatsService activity) {
        this.activity = activity;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        activity.syncFromBukkit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        activity.syncFromBukkit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;
        activity.recordMobKill(killer, entity.getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();
        String key = ActivityStatsService.cropKeyFor(type);
        if (key == null) return;

        if (ActivityStatsService.isTrackedAgeableCrop(type)) {
            BlockData data = block.getBlockData();
            if (!(data instanceof Ageable ageable)) return;
            if (ageable.getAge() < ageable.getMaximumAge()) return;
            activity.recordCropHarvest(player, key, 1);
            return;
        }

        if (type == Material.SWEET_BERRY_BUSH) {
            BlockData data = block.getBlockData();
            if (data instanceof Ageable ageable && ageable.getAge() >= 2) {
                activity.recordCropHarvest(player, key, 1);
            }
            return;
        }


        activity.recordCropHarvest(player, key, 1);
    }
}
