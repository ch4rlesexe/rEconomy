package net.ch4rlesexe.economy.heads;

import fr.maxlego08.head.api.Head;
import fr.maxlego08.head.api.HeadManager;
import fr.maxlego08.head.api.enums.HeadCategory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HeadsShop implements Listener {
    public static final String TITLE_MAIN = "Heads Shop";
    public static final String TITLE_LIST = "Heads Shop — ";

    private final REconomyPlugin plugin;
    private final EconomyService economy;
    private final NamespacedKey headIdKey;
    private final NamespacedKey actionKey;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> awaitingSearch = ConcurrentHashMap.newKeySet();

    private record Session(String mode, HeadCategory category, String query, int page) {}

    public HeadsShop(REconomyPlugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.headIdKey = new NamespacedKey(plugin, "heads_shop_id");
        this.actionKey = new NamespacedKey(plugin, "heads_shop_action");
    }

    public boolean available() {
        return manager() != null;
    }

    private HeadManager manager() {
        RegisteredServiceProvider<HeadManager> reg =
                Bukkit.getServicesManager().getRegistration(HeadManager.class);
        return reg == null ? null : reg.getProvider();
    }

    public double price() {
        return plugin.getConfig().getDouble("heads.price", 75);
    }

    public void openMain(Player player) {
        if (!available()) {
            player.sendMessage("§crEconomy §8» §7Heads shop unavailable (zHead not loaded).");
            return;
        }
        if (!player.hasPermission("reconomy.heads.use")) {
            player.sendMessage("§crEconomy §8» §7No permission.");
            return;
        }
        Inventory inv = Bukkit.createInventory(player, 27, Component.text(TITLE_MAIN));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
        HeadCategory[] cats = HeadCategory.values();
        for (int i = 0; i < cats.length && i < slots.length; i++) {
            HeadCategory cat = cats[i];
            ItemStack icon = named(Material.PLAYER_HEAD, pretty(cat.name()));
            loreBuy(icon, "Browse " + pretty(cat.name()), null);
            tagAction(icon, "cat:" + cat.name());
            inv.setItem(slots[i], icon);
        }
        ItemStack search = named(Material.COMPASS, "Search");
        loreBuy(search, "Click, then type a search in chat", null);
        tagAction(search, "search");
        inv.setItem(24, search);

        ItemStack info = named(Material.GOLD_NUGGET, "Price: " + economy.format(price()));
        loreBuy(info, "Every head costs coins — no free gives", null);
        inv.setItem(4, info);
        player.openInventory(inv);
    }

    public void openSearch(Player player, String query, int page) {
        HeadManager mgr = manager();
        if (mgr == null) return;
        List<Head> heads = mgr.search(query);
        sessions.put(player.getUniqueId(), new Session("search", null, query, page));
        openList(player, TITLE_LIST + "Search", heads, page, "search:" + query);
    }

    public void openCategory(Player player, HeadCategory category, int page) {
        HeadManager mgr = manager();
        if (mgr == null) return;
        List<Head> heads = mgr.getHeads(category);
        sessions.put(player.getUniqueId(), new Session("category", category, null, page));
        openList(player, TITLE_LIST + pretty(category.name()), heads, page, "cat:" + category.name());
    }

    private void openList(Player player, String title, List<Head> heads, int page, String ignoredBackAction) {
        HeadManager mgr = manager();
        if (mgr == null) return;
        int perPage = 45;
        int maxPage = Math.max(1, (int) Math.ceil(heads.size() / (double) perPage));
        page = Math.max(1, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(player, 54, Component.text(title));
        int start = (page - 1) * perPage;
        int end = Math.min(heads.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Head head = heads.get(i);
            ItemStack stack = mgr.createItemStack(head);
            if (stack == null) continue;
            loreBuy(stack, "§aLeft-click §7to buy §a" + economy.format(price()), "§8ID: " + head.getId());
            tagHead(stack, head.getId());
            inv.setItem(i - start, stack);
        }
        ItemStack back = named(Material.ARROW, "Back");
        tagAction(back, "main");
        inv.setItem(45, back);
        if (page > 1) {
            ItemStack prev = named(Material.PAPER, "Page " + (page - 1));
            tagAction(prev, "page:" + (page - 1));
            inv.setItem(48, prev);
        }
        ItemStack pageInfo = named(Material.BOOK, "Page " + page + "/" + maxPage);
        loreBuy(pageInfo, heads.size() + " heads", null);
        inv.setItem(49, pageInfo);
        if (page < maxPage) {
            ItemStack next = named(Material.PAPER, "Page " + (page + 1));
            tagAction(next, "page:" + (page + 1));
            inv.setItem(50, next);
        }
        player.openInventory(inv);
    }

    private void buy(Player player, String headId) {
        HeadManager mgr = manager();
        if (mgr == null) return;
        ItemStack stack = mgr.createItemStack(headId);
        if (stack == null) {
            player.sendMessage("§crEconomy §8» §7Head not found.");
            return;
        }
        double cost = price();
        if (!economy.withdraw(player.getUniqueId(), cost, "heads-buy")) {
            player.sendMessage("§crEconomy §8» §7Need " + economy.format(cost) + " to buy this head.");
            return;
        }
        var leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        player.sendMessage("§crEconomy §8» §7Bought head §f" + headId + " §8(§a" + economy.format(cost) + "§8)");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = plain(event.getView().title());
        if (!title.startsWith(TITLE_MAIN) && !title.startsWith(TITLE_LIST)) return;
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) return;
        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        if (meta.getPersistentDataContainer().has(headIdKey, PersistentDataType.STRING)) {
            String id = meta.getPersistentDataContainer().get(headIdKey, PersistentDataType.STRING);
            buy(player, id);
            return;
        }
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("main")) {
            openMain(player);
            return;
        }
        if (action.equals("search")) {
            awaitingSearch.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage("§crEconomy §8» §7Type a search term in chat §8(§7or §fcancel§8)");
            return;
        }
        if (action.startsWith("cat:")) {
            HeadCategory cat = HeadCategory.valueOf(action.substring(4));
            openCategory(player, cat, 1);
            return;
        }
        if (action.startsWith("page:")) {
            int page = Integer.parseInt(action.substring(5));
            Session s = sessions.get(player.getUniqueId());
            if (s == null) {
                openMain(player);
                return;
            }
            if ("search".equals(s.mode()) && s.query() != null) {
                openSearch(player, s.query(), page);
            } else if (s.category() != null) {
                openCategory(player, s.category(), page);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!awaitingSearch.remove(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        String msg = event.getMessage().trim();
        Player player = event.getPlayer();
        if (msg.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§crEconomy §8» §7Search cancelled.");
                openMain(player);
            });
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> openSearch(player, msg, 1));
    }

    private void tagHead(ItemStack stack, String id) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(headIdKey, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
    }

    private void tagAction(ItemStack stack, String action) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
    }

    private void loreBuy(ItemStack stack, String line1, String line2) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        List<Component> lore = new ArrayList<>();
        if (line1 != null) {
            lore.add(Component.text(line1.replace('&', '§')).decoration(TextDecoration.ITALIC, false));
        }
        if (line2 != null) {
            lore.add(Component.text(line2.replace('&', '§')).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
    }

    private ItemStack named(Material mat, String name) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String pretty(String name) {
        String[] parts = name.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
