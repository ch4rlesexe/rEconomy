package net.ch4rlesexe.economy.vanish;

import net.ch4rlesexe.economy.REconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VanishService {
    private final REconomyPlugin plugin;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishService(REconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(UUID uuid) {
        return uuid != null && vanished.contains(uuid);
    }

    public boolean isVanished(Player player) {
        return player != null && isVanished(player.getUniqueId());
    }

    public Set<UUID> vanishedIds() {
        return Collections.unmodifiableSet(vanished);
    }

    public boolean canSee(Player viewer, Player target) {
        if (viewer == null || target == null) return true;
        if (!isVanished(target)) return true;
        if (viewer.getUniqueId().equals(target.getUniqueId())) return true;
        return viewer.hasPermission("reconomy.vanish.see");
    }


    public boolean enable(Player player) {
        vanished.add(player.getUniqueId());
        applyHide(player);
        return true;
    }


    public boolean disable(Player player) {
        vanished.remove(player.getUniqueId());
        applyShow(player);
        return true;
    }

    public boolean toggle(Player player) {
        if (isVanished(player)) {
            disable(player);
            return false;
        }
        enable(player);
        return true;
    }


    public void applyHide(Player vanishedPlayer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(vanishedPlayer.getUniqueId())) continue;
            if (canSee(other, vanishedPlayer)) {
                other.showPlayer(plugin, vanishedPlayer);
            } else {
                other.hidePlayer(plugin, vanishedPlayer);
            }
        }
    }


    public void applyShow(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            other.showPlayer(plugin, player);
        }
    }


    public void handleJoin(Player joining) {

        for (UUID id : vanished) {
            Player vanishedPlayer = Bukkit.getPlayer(id);
            if (vanishedPlayer == null || vanishedPlayer.equals(joining)) continue;
            if (canSee(joining, vanishedPlayer)) {
                joining.showPlayer(plugin, vanishedPlayer);
            } else {
                joining.hidePlayer(plugin, vanishedPlayer);
            }
        }

        if (isVanished(joining)) {
            applyHide(joining);
        }
    }
}
