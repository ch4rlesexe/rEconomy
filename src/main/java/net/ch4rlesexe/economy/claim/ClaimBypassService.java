package net.ch4rlesexe.economy.claim;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimBypassService {
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(UUID uuid) {
        return uuid != null && enabled.contains(uuid);
    }

    public boolean isEnabled(Player player) {
        return player != null && isEnabled(player.getUniqueId());
    }

    public boolean canUse(Player player) {
        return player != null && player.hasPermission("reconomy.claims.bypass");
    }


    public Boolean toggle(Player player) {
        if (!canUse(player)) return null;
        UUID id = player.getUniqueId();
        if (enabled.contains(id)) {
            enabled.remove(id);
            return false;
        }
        enabled.add(id);
        return true;
    }

    public void clear(UUID uuid) {
        if (uuid != null) enabled.remove(uuid);
    }
}
