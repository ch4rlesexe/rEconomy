package net.ch4rlesexe.economy.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.ch4rlesexe.economy.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatListener implements Listener {
    private final CombatTagService combat;
    private final StatsService stats;
    private final Set<UUID> combatLogCounted = ConcurrentHashMap.newKeySet();

    public CombatListener(CombatTagService combat, StatsService stats) {
        this.combat = combat;
        this.stats = stats;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) attacker = p;
        }
        if (attacker == null || attacker.equals(victim)) return;

        combat.tagBoth(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!combat.killOnLogout()) {
            combat.clear(player.getUniqueId());
            return;
        }
        if (!combat.isTagged(player.getUniqueId())) return;
        if (player.hasPermission("reconomy.combat.bypass")
                || player.hasPermission("reconomy.economy.admin")) {
            combat.clear(player.getUniqueId());
            return;
        }

        UUID killerId = combat.lastAttacker(player.getUniqueId());
        String victimName = player.getName();
        String killerName = null;
        if (killerId != null) {
            Player online = Bukkit.getPlayer(killerId);
            killerName = online != null ? online.getName()
                    : Bukkit.getOfflinePlayer(killerId).getName();
        }

        combatLogCounted.add(player.getUniqueId());
        combat.clear(player.getUniqueId());
        stats.recordCombatLog(player.getUniqueId(), victimName, killerId, killerName);
        player.setHealth(0.0);

        if (killerName != null) {
            Bukkit.broadcast(Component.text(
                    "rEconomy » " + victimName + " combat-logged and was slain by " + killerName + ".",
                    NamedTextColor.RED));
            if (killerId != null) {
                Player killerOnline = Bukkit.getPlayer(killerId);
                if (killerOnline != null) {
                    killerOnline.sendMessage("§crEconomy §8» §7Kill credited — §f" + victimName + " §7combat-logged.");
                }
            }
        } else {
            Bukkit.broadcast(Component.text(
                    "rEconomy » " + victimName + " logged out during combat and died.",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();
        combat.clear(victimId);

        if (combatLogCounted.remove(victimId)) {

            return;
        }

        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victimId)) {
            stats.recordKill(killer.getUniqueId(), killer.getName(), victimId, victim.getName());
        } else {
            stats.recordDeath(victimId, victim.getName());
        }
    }
}
