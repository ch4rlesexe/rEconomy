package net.ch4rlesexe.economy.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.economy.EconomyService;
import net.ch4rlesexe.economy.util.CooldownService;
import net.ch4rlesexe.economy.util.TeleportWarmupService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtpGui implements Listener {
    public static final String TITLE = "Random Teleport";
    public static final String TITLE_CONFIRM = "Confirm RTP";

    private final REconomyPlugin plugin;
    private final RtpService rtp;
    private final EconomyService economy;
    private final CooldownService cooldowns;
    private final TeleportWarmupService warmup;
    private final NamespacedKey rangeKey;
    private final NamespacedKey actionKey;
    private final Map<UUID, Integer> pendingRange = new ConcurrentHashMap<>();

    public RtpGui(REconomyPlugin plugin, RtpService rtp, EconomyService economy,
                  CooldownService cooldowns, TeleportWarmupService warmup) {
        this.plugin = plugin;
        this.rtp = rtp;
        this.economy = economy;
        this.cooldowns = cooldowns;
        this.warmup = warmup;
        this.rangeKey = new NamespacedKey(plugin, "rtp_range");
        this.actionKey = new NamespacedKey(plugin, "rtp_action");
    }

    public void open(Player player) {
        if (!plugin.getConfig().getBoolean("rtp.enabled", true)) {
            player.sendMessage("§crEconomy §8» §7RTP is disabled.");
            return;
        }
        if (!player.hasPermission("reconomy.rtp.use")) {
            player.sendMessage("§crEconomy §8» §7No permission.");
            return;
        }
        Inventory inv = Bukkit.createInventory(player, 27, Component.text(TITLE));
        inv.setItem(11, rangeItem(Material.MAP, "1,000 blocks", 1000, "rtp.tiers.1k"));
        inv.setItem(13, rangeItem(Material.COMPASS, "3,000 blocks", 3000, "rtp.tiers.3k"));
        inv.setItem(15, rangeItem(Material.ENDER_PEARL, "5,000 blocks", 5000, "rtp.tiers.5k"));
        inv.setItem(22, named(Material.BARRIER, "Cancel", null, null));
        player.openInventory(inv);
    }

    private ItemStack rangeItem(Material mat, String label, int range, String costPath) {
        double cost = plugin.getConfig().getDouble(costPath + ".cost", 100);
        ItemStack stack = named(mat, label, List.of(
                "§7Teleport within §f" + label,
                "§7Cost: §a" + economy.format(cost),
                "§eClick to select"
        ), null);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(rangeKey, PersistentDataType.INTEGER, range);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "select");
        stack.setItemMeta(meta);
        return stack;
    }

    private void openConfirm(Player player, int range) {
        pendingRange.put(player.getUniqueId(), range);
        double cost = costFor(range);
        Inventory inv = Bukkit.createInventory(player, 27, Component.text(TITLE_CONFIRM));
        inv.setItem(11, actionItem(Material.LIME_CONCRETE, "Confirm", List.of(
                "§7Range: §f" + String.format("%,d", range) + " blocks",
                "§7Cost: §a" + economy.format(cost),
                "§aClick to teleport"
        ), "confirm"));
        inv.setItem(15, actionItem(Material.RED_CONCRETE, "Cancel", List.of("§7Back to range select"), "back"));
        player.openInventory(inv);
    }

    private ItemStack actionItem(Material mat, String name, List<String> lore, String action) {
        ItemStack stack = named(mat, name, lore, null);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack named(Material mat, String name, List<String> lore, String ignored) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        if (lore != null) {
            meta.lore(lore.stream()
                    .map(l -> Component.text(l.replace('&', '§')).decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public double costFor(int range) {
        if (range <= 1000) return plugin.getConfig().getDouble("rtp.tiers.1k.cost", 100);
        if (range <= 3000) return plugin.getConfig().getDouble("rtp.tiers.3k.cost", 250);
        return plugin.getConfig().getDouble("rtp.tiers.5k.cost", 500);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!title.equals(TITLE) && !title.equals(TITLE_CONFIRM)) return;
        event.setCancelled(true);
        ItemStack cur = event.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) return;
        ItemMeta meta = cur.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) {
            if (cur.getType() == Material.BARRIER) player.closeInventory();
            return;
        }
        switch (action) {
            case "select" -> {
                Integer range = meta.getPersistentDataContainer().get(rangeKey, PersistentDataType.INTEGER);
                if (range == null) return;
                openConfirm(player, range);
            }
            case "back" -> open(player);
            case "confirm" -> {
                Integer range = pendingRange.remove(player.getUniqueId());
                player.closeInventory();
                if (range == null) return;
                int r = range;
                double cost = costFor(r);
                warmup.begin(player, "rtp", p -> rtp.execute(p, r, cost));
            }
            default -> {}
        }
    }
}
