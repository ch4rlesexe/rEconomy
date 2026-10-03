package net.ch4rlesexe.economy.tab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.namecolor.NameColorService;
import net.ch4rlesexe.economy.team.TeamService;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class TabListService implements Listener {
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SEC = LegacyComponentSerializer.legacySection();

    private final REconomyPlugin plugin;
    private final TeamService teams;
    private NameColorService nameColors;
    private Chat vaultChat;

    public TabListService(REconomyPlugin plugin, TeamService teams) {
        this.plugin = plugin;
        this.teams = teams;
        hookVault();
    }

    public void setNameColors(NameColorService nameColors) {
        this.nameColors = nameColors;
    }

    private void hookVault() {
        try {
            RegisteredServiceProvider<Chat> reg = Bukkit.getServicesManager().getRegistration(Chat.class);
            if (reg != null) {
                vaultChat = reg.getProvider();
                plugin.getLogger().info("Tab list hooked Vault Chat for prefixes.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Vault Chat unavailable for tab prefixes: " + t.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) refresh(player);
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) refresh(player);
        }, 20L);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;

        Component prefix = prefixComponent(player);
        Component name = coloredName(player);
        Component tag = teamTagComponent(player);

        Component list = Component.empty()
                .append(prefix)
                .append(name)
                .append(tag)
                .decoration(TextDecoration.ITALIC, false);
        player.playerListName(list);
    }

    private Component coloredName(Player player) {
        if (nameColors != null) {
            var def = nameColors.get(nameColors.activeId(player.getUniqueId()))
                    .orElse(null);
            return nameColors.render(player.getName(), def);
        }
        return Component.text(player.getName(), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component teamTagComponent(Player player) {
        return teams.getByPlayer(player.getUniqueId())
                .map(t -> Component.text(" [" + t.tag() + "]", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .orElse(Component.empty());
    }

    private Component prefixComponent(Player player) {
        if (vaultChat == null) return Component.empty();
        String raw;
        try {
            raw = vaultChat.getPlayerPrefix(player);
        } catch (Throwable t) {
            return Component.empty();
        }
        if (raw == null || raw.isBlank()) return Component.empty();

        String normalized = raw.replace('§', '&');
        try {
            return AMP.deserialize(normalized).decoration(TextDecoration.ITALIC, false);
        } catch (Exception e) {
            try {
                return SEC.deserialize(raw).decoration(TextDecoration.ITALIC, false);
            } catch (Exception e2) {
                return Component.text(raw).decoration(TextDecoration.ITALIC, false);
            }
        }
    }
}
