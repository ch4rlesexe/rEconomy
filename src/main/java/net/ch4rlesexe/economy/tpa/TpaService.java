package net.ch4rlesexe.economy.tpa;

import net.ch4rlesexe.economy.REconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaService {
    public enum Type { TO, HERE }

    public record Request(UUID from, UUID to, Type type, long expiresAt) {}

    private final REconomyPlugin plugin;

    private final Map<UUID, Request> pending = new ConcurrentHashMap<>();

    public TpaService(REconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player from, Player to, Type type) {
        cancelOutgoing(from.getUniqueId(), false);
        long timeout = plugin.getConfig().getLong("tpa.timeout-seconds", 60) * 1000L;
        pending.put(to.getUniqueId(), new Request(from.getUniqueId(), to.getUniqueId(), type,
                System.currentTimeMillis() + timeout));
        String verb = type == Type.TO ? "teleport to you" : "teleport you to them";
        to.sendMessage("§crEconomy §8» §f" + from.getName() + " §7requested to " + verb + ".");
        to.sendMessage("§7Type §f/tpaccept §7or §f/tpdeny");
        from.sendMessage("§crEconomy §8» §7Request sent to §f" + to.getName());
        from.sendMessage("§7Type §f/tpcancel §7to cancel.");
    }

    public Request peek(UUID target) {
        Request r = pending.get(target);
        if (r == null) return null;
        if (System.currentTimeMillis() > r.expiresAt()) {
            pending.remove(target);
            return null;
        }
        return r;
    }

    public Request take(UUID target) {
        Request r = pending.remove(target);
        if (r == null) return null;
        if (System.currentTimeMillis() > r.expiresAt()) return null;
        return r;
    }

    public void deny(UUID target) {
        pending.remove(target);
    }


    public boolean cancelOutgoing(UUID from, boolean notify) {
        Iterator<Map.Entry<UUID, Request>> it = pending.entrySet().iterator();
        boolean cancelled = false;
        while (it.hasNext()) {
            Map.Entry<UUID, Request> e = it.next();
            Request r = e.getValue();
            if (!r.from().equals(from)) continue;
            it.remove();
            cancelled = true;
            if (notify) {
                Player target = Bukkit.getPlayer(r.to());
                if (target != null) {
                    Player sender = Bukkit.getPlayer(from);
                    String name = sender != null ? sender.getName() : "Someone";
                    target.sendMessage("§crEconomy §8» §f" + name + " §7cancelled their teleport request.");
                }
            }
        }
        return cancelled;
    }
}
