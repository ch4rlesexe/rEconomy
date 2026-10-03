package net.ch4rlesexe.economy.namecolor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.ch4rlesexe.economy.economy.EconomyService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NameColorGui implements Listener {
    public static final String TITLE = "Name Colors";
    public static final String TITLE_CONFIRM = "Confirm Color Purchase";

    private final NameColorService colors;
    private final EconomyService economy;
    private final NamespacedKey idKey;
    private final Map<UUID, String> confirmBuy = new ConcurrentHashMap<>();

    public NameColorGui(net.ch4rlesexe.economy.REconomyPlugin plugin,
                        NameColorService colors, EconomyService economy) {
        this.colors = colors;
        this.economy = economy;
        this.idKey = new NamespacedKey(plugin, "name_color_id");
    }

    public void open(Player player) {
        confirmBuy.remove(player.getUniqueId());
        List<NameColorService.ColorDef> list = colors.colors();
        int rows = Math.min(6, Math.max(3, (int) Math.ceil((list.size() + 9) / 9.0)));
        Inventory inv = Bukkit.createInventory(player, rows * 9,
                Component.text(TITLE, NamedTextColor.GOLD, TextDecoration.BOLD));

        inv.setItem(4, infoItem(player));
        int slot = 9;
        for (NameColorService.ColorDef def : list) {
            if (slot >= inv.getSize() - 1) break;
            inv.setItem(slot++, colorItem(player, def));
        }
        inv.setItem(inv.getSize() - 5, button(Material.BARRIER, "Close", NamedTextColor.RED, List.of()));
        player.openInventory(inv);
    }

    private void openConfirm(Player player, NameColorService.ColorDef def) {
        confirmBuy.put(player.getUniqueId(), def.id());
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_CONFIRM, NamedTextColor.YELLOW, TextDecoration.BOLD));
        inv.setItem(11, button(Material.LIME_CONCRETE, "Yes", NamedTextColor.GREEN, List.of(
                "Buy " + def.name() + " for " + economy.format(def.price()),
                "You keep this color forever"
        )));
        ItemStack preview = new ItemStack(def.icon());
        ItemMeta meta = preview.getItemMeta();
        meta.displayName(colors.preview(def));
        meta.lore(List.of(
                Component.text("Preview: ", NamedTextColor.GRAY)
                        .append(colors.render(player.getName(), def))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Cost: " + economy.format(def.price()), NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        preview.setItemMeta(meta);
        inv.setItem(13, preview);
        inv.setItem(15, button(Material.RED_CONCRETE, "No", NamedTextColor.RED, List.of("Back to shop")));
        player.openInventory(inv);
    }

    private ItemStack infoItem(Player player) {
        String active = colors.activeId(player.getUniqueId());
        NameColorService.ColorDef def = colors.get(active).orElse(null);
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Your name", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Preview: ", NamedTextColor.GRAY)
                .append(colors.render(player.getName(), def))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Username stays the same.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Buy once — keep forever.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Balance: " + economy.format(economy.getBalance(player.getUniqueId())),
                NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack colorItem(Player player, NameColorService.ColorDef def) {
        boolean owned = colors.owns(player.getUniqueId(), def.id());
        boolean active = def.id().equals(colors.activeId(player.getUniqueId()));
        ItemStack item = new ItemStack(def.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colors.preview(def));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Preview: ", NamedTextColor.GRAY)
                .append(colors.render(player.getName(), def))
                .decoration(TextDecoration.ITALIC, false));
        if (active) {
            lore.add(Component.text("EQUIPPED", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (owned || def.price() <= 0) {
            lore.add(Component.text("Owned — click to equip", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Price: " + economy.format(def.price()), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to buy (confirmation next)", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if ("gradient".equals(def.type())) {
            lore.add(Component.text("Gradient", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material mat, String name, NamedTextColor color, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains(TITLE_CONFIRM)) {
            event.setCancelled(true);
            handleConfirmClick(player, event.getCurrentItem());
            return;
        }
        if (!title.contains(TITLE)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        String display = PlainTextComponentSerializer.plainText().serialize(
                clicked.getItemMeta().displayName() == null ? Component.empty() : clicked.getItemMeta().displayName());
        if (display.equals("Close")) {
            player.closeInventory();
            return;
        }
        String id = clicked.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id == null) return;

        NameColorService.ColorDef def = colors.get(id).orElse(null);
        if (def == null) return;

        if (colors.owns(player.getUniqueId(), id)) {
            if (colors.equip(player, id)) {
                player.sendMessage("§crEconomy §8» §7Name color set to §f" + def.name() + "§7.");
            }
            open(player);
            return;
        }

        openConfirm(player, def);
    }

    private void handleConfirmClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        String display = PlainTextComponentSerializer.plainText().serialize(
                clicked.getItemMeta().displayName() == null ? Component.empty() : clicked.getItemMeta().displayName());
        if (display.equals("No") || display.equals("Cancel")) {
            open(player);
            return;
        }
        if (!display.equals("Yes") && !display.equals("Confirm Purchase")) return;

        String id = confirmBuy.get(player.getUniqueId());
        if (id == null) {
            open(player);
            return;
        }
        NameColorService.ColorDef def = colors.get(id).orElse(null);
        if (def == null) {
            open(player);
            return;
        }
        confirmBuy.remove(player.getUniqueId());
        if (economy.getBalance(player.getUniqueId()) + 1e-9 < def.price()) {
            player.sendMessage("§cNeed " + economy.format(def.price()) + ".");
            open(player);
            return;
        }
        if (!colors.buy(player, id)) {
            player.sendMessage("§cCould not buy that color.");
            open(player);
            return;
        }
        colors.equip(player, id);
        player.sendMessage("§crEconomy §8» §aUnlocked §f" + def.name() + " §7forever for §f"
                + economy.format(def.price()) + "§7.");
        open(player);
    }
}
