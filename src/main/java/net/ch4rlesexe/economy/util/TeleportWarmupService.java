package net.ch4rlesexe.economy.util;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.combat.CombatTagService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class TeleportWarmupService implements Listener {
    private final REconomyPlugin plugin;
    private final CooldownService cooldowns;
    private CombatTagService combat;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private record Pending(BukkitTask task, Location start, String category) {}

    public TeleportWarmupService(REconomyPlugin plugin, CooldownService cooldowns) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
    }

    public void setCombat(CombatTagService combat) {
        this.combat = combat;
    }

    public int warmupSeconds() {
        return Math.max(0, plugin.getConfig().getInt("teleports.warmup-seconds", 5));
    }


    public boolean begin(Player player, String category, Consumer<Player> onSuccess) {
        if (combat != null && !combat.allowTeleport(player)) {
            return false;
        }
        if (!cooldowns.canStart(player, category)) {
            return false;
        }
        cancel(player.getUniqueId(), false);

        int warmup = warmupSeconds();
        if (warmup <= 0) {
            if (!cooldowns.tryUse(player, category)) {
                return false;
            }
            onSuccess.accept(player);
            return true;
        }

        player.sendMessage("§crEconomy §8» §7Teleporting in §f" + warmup
                + "s§7… Don't move!");
        Location start = player.getLocation().clone();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (combat != null && !combat.allowTeleport(player)) return;
            if (!cooldowns.tryUse(player, category)) return;
            onSuccess.accept(player);
        }, warmup * 20L);
        pending.put(player.getUniqueId(), new Pending(task, start, category));
        return true;
    }

    public void cancel(UUID uuid, boolean notify) {
        Pending p = pending.remove(uuid);
        if (p == null) return;
        p.task().cancel();
        if (notify) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage("§crEconomy §8» §7Teleport cancelled.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Pending p = pending.get(event.getPlayer().getUniqueId());
        if (p == null) return;
        Location to = event.getTo();
        if (to == null) return;
        Location from = p.start();
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            cancel(event.getPlayer().getUniqueId(), true);
            return;
        }
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            cancel(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (pending.containsKey(player.getUniqueId())) {
            cancel(player.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId(), false);
    }
}
