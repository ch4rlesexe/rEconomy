package net.ch4rlesexe.economy.team;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class TeamChatPrompt implements Listener {
    public enum Kind { CREATE_NAME, CREATE_TAG, INVITE_NAME, JOIN_NAME }

    public record Pending(Kind kind, String pendingName) {}

    private final Plugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private BiConsumer<Player, String[]> onCreateDone;
    private BiConsumer<Player, String> onInvite;
    private BiConsumer<Player, String> onJoin;

    public TeamChatPrompt(Plugin plugin) {
        this.plugin = plugin;
    }

    public void setHandlers(BiConsumer<Player, String[]> onCreateDone,
                            BiConsumer<Player, String> onInvite,
                            BiConsumer<Player, String> onJoin) {
        this.onCreateDone = onCreateDone;
        this.onInvite = onInvite;
        this.onJoin = onJoin;
    }

    public void askCreate(Player player) {
        pending.put(player.getUniqueId(), new Pending(Kind.CREATE_NAME, null));
        player.closeInventory();
        player.sendMessage("§crEconomy §8» §7Type a team §fname §7in chat (3-16 chars).");
        player.sendMessage("§7Type §fcancel §7to abort.");
    }

    public void askInvite(Player player) {
        pending.put(player.getUniqueId(), new Pending(Kind.INVITE_NAME, null));
        player.closeInventory();
        player.sendMessage("§crEconomy §8» §7Type the §fplayer name §7to invite.");
        player.sendMessage("§7Type §fcancel §7to abort.");
    }

    public void askJoinName(Player player) {
        pending.put(player.getUniqueId(), new Pending(Kind.JOIN_NAME, null));
        player.closeInventory();
        player.sendMessage("§crEconomy §8» §7Type the §fteam name §7to join.");
        player.sendMessage("§7Closed teams need an invite. Type §fcancel §7to abort.");
    }

    public void clear(UUID uuid) {
        pending.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Pending p = pending.get(event.getPlayer().getUniqueId());
        if (p == null) return;
        event.setCancelled(true);
        String msg = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("cancel")) {
                pending.remove(player.getUniqueId());
                player.sendMessage("§crEconomy §8» §7Cancelled.");
                return;
            }
            handle(player, p, msg);
        });
    }

    private void handle(Player player, Pending p, String msg) {

        Pending current = pending.get(player.getUniqueId());
        if (current == null) return;
        switch (current.kind()) {
            case CREATE_NAME -> {
                String name = msg.replaceAll("[^A-Za-z0-9_]", "");
                if (name.length() < 3 || name.length() > 16) {
                    player.sendMessage("§cInvalid name. Try again (3-16 alphanumeric).");
                    return;
                }
                pending.put(player.getUniqueId(), new Pending(Kind.CREATE_TAG, name));
                player.sendMessage("§crEconomy §8» §7Now type a §ftag §7(1-6 chars), e.g. §fRED");
            }
            case CREATE_TAG -> {
                String tag = msg.replaceAll("[^A-Za-z0-9]", "");
                if (tag.isEmpty() || tag.length() > 6) {
                    player.sendMessage("§cInvalid tag. Try again (1-6 alphanumeric).");
                    return;
                }
                String teamName = current.pendingName();
                pending.remove(player.getUniqueId());
                if (onCreateDone != null) onCreateDone.accept(player, new String[]{teamName, tag});
            }
            case INVITE_NAME -> {
                pending.remove(player.getUniqueId());
                if (onInvite != null) onInvite.accept(player, msg);
            }
            case JOIN_NAME -> {
                pending.remove(player.getUniqueId());
                if (onJoin != null) onJoin.accept(player, msg);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
