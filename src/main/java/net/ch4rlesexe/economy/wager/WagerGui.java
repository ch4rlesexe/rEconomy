package net.ch4rlesexe.economy.wager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WagerGui implements Listener {
    public static final String TITLE_MAIN = "rEconomy Wagers";
    public static final String TITLE_PLAYERS = "Wager - Pick Player";
    public static final String TITLE_AMOUNT = "Wager - Pick Amount";

    private static final double[] PRESETS = {10, 25, 50, 100, 250, 500, 1000, 2500};

    private final REconomyPlugin plugin;
    private final WagerService wagers;
    private final EconomyService economy;
    private final NamespacedKey keyAction;
    private final NamespacedKey keyValue;
    private final Map<UUID, UUID> pendingTarget = new ConcurrentHashMap<>();

    public WagerGui(REconomyPlugin plugin, WagerService wagers, EconomyService economy) {
        this.plugin = plugin;
        this.wagers = wagers;
        this.economy = economy;
        this.keyAction = new NamespacedKey(plugin, "wager_action");
        this.keyValue = new NamespacedKey(plugin, "wager_value");
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_MAIN, NamedTextColor.RED, TextDecoration.BOLD));

        Optional<WagerService.Wager> active = wagers.getActive(player.getUniqueId());
        Optional<WagerService.Wager> pending = wagers.pendingFor(player.getUniqueId());

        List<String> statusLore = new ArrayList<>();
        statusLore.add("Balance: " + economy.format(economy.getBalance(player.getUniqueId())));
        statusLore.add("Min wager: " + economy.format(wagers.minAmount()));
        if (pending.isPresent() && pending.get().target().equals(player.getUniqueId())) {
            WagerService.Wager w = pending.get();
            statusLore.add("");
            statusLore.add("Incoming: " + nameOf(w.challenger()) + " for " + economy.format(w.amount()));
            statusLore.add("Accept or deny below");
        } else if (active.isPresent()) {
            WagerService.Wager w = active.get();
            statusLore.add("");
            statusLore.add("Status: " + w.status().toUpperCase());
            statusLore.add("Amount: " + economy.format(w.amount()) + " each");
            statusLore.add("Pot: " + economy.format(w.amount() * 2));
            statusLore.add("Vs: " + otherName(w, player.getUniqueId()));
        } else {
            statusLore.add("");
            statusLore.add("No active wager");
        }
        inv.setItem(4, tagged(Material.GOLD_INGOT, "Your Wager", NamedTextColor.GOLD, statusLore, "noop", ""));

        inv.setItem(11, tagged(Material.DIAMOND_SWORD, "Challenge Player", NamedTextColor.AQUA,
                List.of("Pick an online player", "Then choose an amount"), "challenge", ""));

        boolean canAccept = pending.isPresent() && pending.get().target().equals(player.getUniqueId())
                && "pending".equals(pending.get().status());
        inv.setItem(13, tagged(canAccept ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                "Accept", NamedTextColor.GREEN,
                List.of(canAccept ? "Match the wager amount" : "No incoming wager"),
                "accept", ""));
        inv.setItem(14, tagged(canAccept ? Material.RED_CONCRETE : Material.GRAY_CONCRETE,
                "Deny", NamedTextColor.RED,
                List.of(canAccept ? "Refund challenger" : "No incoming wager"),
                "deny", ""));

        boolean isActive = active.isPresent() && "active".equals(active.get().status());
        inv.setItem(15, tagged(isActive ? Material.NETHER_STAR : Material.GRAY_DYE,
                "I Won", NamedTextColor.YELLOW,
                List.of(isActive ? "Claim the pot for yourself" : "Only when wager is active"),
                "win", ""));
        inv.setItem(16, tagged(active.isPresent() ? Material.BARRIER : Material.GRAY_DYE,
                "Cancel", NamedTextColor.DARK_RED,
                List.of(active.isPresent() ? "Refund escrowed coins" : "Nothing to cancel"),
                "cancel", ""));

        inv.setItem(22, tagged(Material.ARROW, "Close", NamedTextColor.GRAY, List.of(), "close", ""));
        player.openInventory(inv);
    }

    public void openPlayers(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_PLAYERS, NamedTextColor.AQUA, TextDecoration.BOLD));
        inv.setItem(4, tagged(Material.PAPER, "Online Players", NamedTextColor.YELLOW,
                List.of("Click someone to wager"), "noop", ""));

        int slot = 9;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            if (slot >= 45) break;
            inv.setItem(slot++, skull(online, "challenge_player", online.getUniqueId().toString()));
        }
        inv.setItem(49, tagged(Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), "back_main", ""));
        player.openInventory(inv);
    }

    public void openAmount(Player player, UUID targetId) {
        pendingTarget.put(player.getUniqueId(), targetId);
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_AMOUNT, NamedTextColor.GOLD, TextDecoration.BOLD));

        String targetName = nameOf(targetId);
        inv.setItem(4, tagged(Material.NAME_TAG, "Vs " + targetName, NamedTextColor.YELLOW,
                List.of("Your balance: " + economy.format(economy.getBalance(player.getUniqueId())),
                        "Pick how much each side puts up"),
                "noop", ""));

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        for (int i = 0; i < PRESETS.length && i < slots.length; i++) {
            double amt = PRESETS[i];
            if (amt < wagers.minAmount()) continue;
            boolean afford = economy.getBalance(player.getUniqueId()) + 1e-9 >= amt;
            inv.setItem(slots[i], tagged(afford ? Material.EMERALD : Material.COAL,
                    economy.format(amt), afford ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY,
                    List.of("Pot: " + economy.format(amt * 2),
                            afford ? "Click to send wager" : "Not enough coins"),
                    "amount", Double.toString(amt)));
        }
        inv.setItem(18, tagged(Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), "back_players", ""));
        inv.setItem(26, tagged(Material.BARRIER, "Cancel", NamedTextColor.RED, List.of(), "back_main", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.equals(TITLE_MAIN) && !title.equals(TITLE_PLAYERS) && !title.equals(TITLE_AMOUNT)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(keyValue, PersistentDataType.STRING);
        if (action == null || action.equals("noop")) return;

        switch (action) {
            case "close" -> player.closeInventory();
            case "back_main" -> openMain(player);
            case "back_players" -> openPlayers(player);
            case "challenge" -> openPlayers(player);
            case "challenge_player" -> {
                try {
                    openAmount(player, UUID.fromString(value));
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "amount" -> {
                UUID targetId = pendingTarget.get(player.getUniqueId());
                if (targetId == null) {
                    openMain(player);
                    return;
                }
                Player target = Bukkit.getPlayer(targetId);
                if (target == null) {
                    player.sendMessage("§cPlayer went offline.");
                    pendingTarget.remove(player.getUniqueId());
                    openMain(player);
                    return;
                }
                try {
                    double amount = Double.parseDouble(value);
                    player.closeInventory();
                    if (wagers.challenge(player, target, amount)) {
                        pendingTarget.remove(player.getUniqueId());
                    } else {
                        openMain(player);
                    }
                } catch (NumberFormatException e) {
                    openMain(player);
                }
            }
            case "accept" -> {
                player.closeInventory();
                wagers.accept(player);
            }
            case "deny" -> {
                player.closeInventory();
                if (!wagers.deny(player)) player.sendMessage("§cNo pending wager.");
            }
            case "win" -> {
                player.closeInventory();
                wagers.declareWinner(player, player.getUniqueId(), false);
            }
            case "cancel" -> {
                player.closeInventory();
                if (wagers.cancel(player)) player.sendMessage("§crEconomy §8» §7Wager cancelled.");
                else player.sendMessage("§cNo wager to cancel.");
            }
            default -> {
            }
        }
    }

    private ItemStack skull(Player target, String action, String value) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(Component.text(target.getName(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Balance: " + economy.format(economy.getBalance(target.getUniqueId())), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Click to wager", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(keyValue, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack tagged(Material mat, String name, NamedTextColor color, List<String> lore,
                             String action, String value) {
        ItemStack item = new ItemStack(mat.isItem() ? mat : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        if (value != null) {
            meta.getPersistentDataContainer().set(keyValue, PersistentDataType.STRING, value);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        return off.getName() != null ? off.getName() : uuid.toString().substring(0, 8);
    }

    private static String otherName(WagerService.Wager w, UUID self) {
        return nameOf(w.challenger().equals(self) ? w.target() : w.challenger());
    }
}
