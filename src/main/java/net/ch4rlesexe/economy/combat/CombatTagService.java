package net.ch4rlesexe.economy.combat;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatTagService {
    private final REconomyPlugin plugin;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();

    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();
    private int taskId = -1;

    public CombatTagService(REconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        taggedUntil.clear();
        lastAttacker.clear();
    }

    public int durationSeconds() {
        return Math.max(1, plugin.getConfig().getInt("combat.duration-seconds", 20));
    }

    public boolean killOnLogout() {
        return plugin.getConfig().getBoolean("combat.kill-on-logout", true);
    }

    public boolean blockTeleports() {
        return plugin.getConfig().getBoolean("combat.block-teleports", true);
    }

    public void tag(Player player) {
        if (player == null || !player.isOnline()) return;
        if (player.hasPermission("reconomy.combat.bypass")
                || player.hasPermission("reconomy.economy.admin")) {
            return;
        }
        boolean was = isTagged(player.getUniqueId());
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationSeconds() * 1000L);
        if (!was) {
            player.sendMessage("§crEconomy §8» §cCombat tagged §7for §f" + durationSeconds() + "s§7. Don't log out!");
        }
    }

    public void tagBoth(Player attacker, Player victim) {
        tag(attacker);
        tag(victim);
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
    }

    public UUID lastAttacker(UUID victim) {
        return lastAttacker.get(victim);
    }

    public boolean isTagged(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            taggedUntil.remove(uuid);
            return false;
        }
        return true;
    }

    public long remainingSeconds(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000L;
    }

    public void clear(UUID uuid) {
        taggedUntil.remove(uuid);
        lastAttacker.remove(uuid);
    }

    public boolean allowTeleport(Player player) {
        if (!blockTeleports()) return true;
        if (player.hasPermission("reconomy.combat.bypass")
                || player.hasPermission("reconomy.economy.admin")) {
            return true;
        }
        if (!isTagged(player.getUniqueId())) return true;
        long left = remainingSeconds(player.getUniqueId());
        player.sendMessage("§crEconomy §8» §7Can't teleport while in combat §8(§f" + left + "s§8)");
        return false;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = taggedUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (now >= e.getValue()) {
                it.remove();
                lastAttacker.remove(e.getKey());
                if (p != null && p.isOnline()) {
                    p.sendMessage("§crEconomy §8» §aYou are no longer in combat.");
                    p.sendActionBar(Component.empty());
                }
                continue;
            }
            if (p != null && p.isOnline()) {
                long left = (e.getValue() - now + 999) / 1000L;
                p.sendActionBar(Component.text("Combat: " + left + "s", NamedTextColor.RED));
            }
        }
    }
}
