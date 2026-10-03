package net.ch4rlesexe.economy.util;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.combat.CombatTagService;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownService {
    private final REconomyPlugin plugin;
    private final Map<String, Long> until = new ConcurrentHashMap<>();
    private CombatTagService combat;

    public CooldownService(REconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void setCombat(CombatTagService combat) {
        this.combat = combat;
    }

    public int seconds(String key) {
        return Math.max(0, plugin.getConfig().getInt("cooldowns." + key, 5));
    }


    public boolean tryUse(Player player, String key) {
        if (combat != null && !combat.allowTeleport(player)) {
            return false;
        }
        if (hasExplicitBypass(player)) {
            return true;
        }
        int secs = seconds(key);
        if (secs <= 0) return true;

        String mapKey = player.getUniqueId() + ":" + key;
        long now = System.currentTimeMillis();
        Long readyAt = until.get(mapKey);
        if (readyAt != null && now < readyAt) {
            long left = (readyAt - now + 999) / 1000L;
            player.sendMessage("§crEconomy §8» §7Wait §f" + left + "s §7before using that again.");
            return false;
        }
        until.put(mapKey, now + secs * 1000L);
        return true;
    }


    public boolean canStart(Player player, String key) {
        if (hasExplicitBypass(player)) return true;
        int secs = seconds(key);
        if (secs <= 0) return true;
        String mapKey = player.getUniqueId() + ":" + key;
        Long readyAt = until.get(mapKey);
        long now = System.currentTimeMillis();
        if (readyAt != null && now < readyAt) {
            long left = (readyAt - now + 999) / 1000L;
            player.sendMessage("§crEconomy §8» §7Wait §f" + left + "s §7before using that again.");
            return false;
        }
        return true;
    }


    private boolean hasExplicitBypass(Player player) {
        if (!plugin.getConfig().getBoolean("cooldowns.allow-bypass-permission", false)) {
            return false;
        }
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (info.getPermission().equalsIgnoreCase("reconomy.cooldown.bypass") && info.getValue()) {
                return true;
            }
        }
        return false;
    }

    public long remaining(UUID uuid, String key) {
        Long readyAt = until.get(uuid + ":" + key);
        if (readyAt == null) return 0;
        long left = readyAt - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000L;
    }
}
