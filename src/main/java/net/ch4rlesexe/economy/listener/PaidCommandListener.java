package net.ch4rlesexe.economy.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

public final class PaidCommandListener implements Listener {
    private static final Set<String> BLOCKED_NICK = Set.of(
            "nick", "nickother", "nonick", "nickoff", "nickname"
    );

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String label = commandLabel(event.getMessage());
        if (label == null) return;
        if (!BLOCKED_NICK.contains(label)) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§crEconomy §8» §7You can't change your nickname.");
        event.getPlayer().sendMessage("§crEconomy §8» §7Buy name colors with §f/namecolor§7.");
    }

    private static String commandLabel(String message) {
        if (message == null) return null;
        String raw = message.trim();
        if (!raw.startsWith("/")) return null;
        String withoutSlash = raw.substring(1);
        int space = withoutSlash.indexOf(' ');
        String label = (space < 0 ? withoutSlash : withoutSlash.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        return label.isEmpty() ? null : label;
    }
}
