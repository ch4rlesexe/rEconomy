package net.ch4rlesexe.economy.leaderboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.stats.ActivityStatsService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
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

public final class LeaderboardGui implements Listener {
    public static final String TITLE_MAIN = "Leaderboards";
    public static final String TITLE_STATS = "Player Stats";
    public static final String TITLE_ACTIVITY = "Activity";
    public static final String TITLE_TEAMS = "Team Boards";
    public static final String TITLE_MOBS = "Mob Leaderboards";
    public static final String TITLE_CROPS = "Crop Leaderboards";
    public static final String TITLE_PREFIX = "Top · ";

    private final REconomyPlugin plugin;
    private final LeaderboardService boards;
    private final NamespacedKey keyAction;
    private final NamespacedKey keyValue;

    public LeaderboardGui(REconomyPlugin plugin, LeaderboardService boards) {
        this.plugin = plugin;
        this.boards = boards;
        this.keyAction = new NamespacedKey(plugin, "top_action");
        this.keyValue = new NamespacedKey(plugin, "top_value");
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_MAIN, NamedTextColor.GOLD, TextDecoration.BOLD));
        fillRow(inv, 0, Material.BLACK_STAINED_GLASS_PANE);
        fillRow(inv, 2, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, plain(Material.NETHER_STAR, "rEconomy Leaderboards", NamedTextColor.GOLD, List.of(
                "Choose a category",
                "/baltop · /killtop · /stats"
        )));


        placeEven(inv, 1, List.of(
                action(Material.PLAYER_HEAD, "Player Stats", NamedTextColor.AQUA, List.of(
                        "Money · Playtime · PvP",
                        "Kills, deaths, K/D, streak",
                        "",
                        "Click to open"
                ), "hub", "stats"),
                action(Material.IRON_SWORD, "Activity", NamedTextColor.GREEN, List.of(
                        "Mob kills · Crops farmed",
                        "Per-type boards inside",
                        "",
                        "Click to open"
                ), "hub", "activity"),
                action(Material.WHITE_BANNER, "Teams", NamedTextColor.YELLOW, List.of(
                        "Team size · Claims · Wealth",
                        "Coins + claim chest valuables",
                        "",
                        "Click to open"
                ), "hub", "teams")
        ), 0);

        inv.setItem(22, action(Material.BARRIER, "Close", NamedTextColor.RED, List.of(), "close", ""));
        player.openInventory(inv);
    }

    public void openStatsHub(Player player) {
        Inventory inv = Bukkit.createInventory(player, 36,
                Component.text(TITLE_STATS, NamedTextColor.AQUA, TextDecoration.BOLD));
        fillRow(inv, 0, Material.CYAN_STAINED_GLASS_PANE);
        fillRow(inv, 3, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, plain(Material.PLAYER_HEAD, "Player Stats", NamedTextColor.AQUA, List.of(
                "Individual player rankings"
        )));

        placeEven(inv, 1, List.of(
                cat(Material.GOLD_INGOT, LeaderboardService.Category.MONEY, NamedTextColor.GOLD),
                cat(Material.CLOCK, LeaderboardService.Category.PLAYTIME, NamedTextColor.YELLOW),
                cat(Material.DIAMOND_SWORD, LeaderboardService.Category.KILLS, NamedTextColor.AQUA)
        ), 0);
        placeEven(inv, 2, List.of(
                cat(Material.SKELETON_SKULL, LeaderboardService.Category.DEATHS, NamedTextColor.GRAY),
                cat(Material.NETHERITE_SWORD, LeaderboardService.Category.KDR, NamedTextColor.LIGHT_PURPLE),
                cat(Material.BLAZE_POWDER, LeaderboardService.Category.STREAK, NamedTextColor.RED)
        ), 0);

        inv.setItem(31, action(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of("Main menu"), "back_main", ""));
        player.openInventory(inv);
    }

    public void openActivityHub(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_ACTIVITY, NamedTextColor.GREEN, TextDecoration.BOLD));
        fillRow(inv, 0, Material.GREEN_STAINED_GLASS_PANE);
        fillRow(inv, 2, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, plain(Material.IRON_SWORD, "Activity", NamedTextColor.GREEN, List.of(
                "PVE and farming boards"
        )));

        placeEven(inv, 1, List.of(
                action(Material.ZOMBIE_HEAD, "Mob Kills", NamedTextColor.GREEN, List.of(
                        "Total mobs + per-mob boards",
                        "",
                        "Click to browse"
                ), "hub", "mobs"),
                action(Material.WHEAT, "Crops", NamedTextColor.YELLOW, List.of(
                        "Total crops + per-crop boards",
                        "",
                        "Click to browse"
                ), "hub", "crops")
        ), 0);

        inv.setItem(22, action(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of("Main menu"), "back_main", ""));
        player.openInventory(inv);
    }

    public void openTeamsHub(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_TEAMS, NamedTextColor.YELLOW, TextDecoration.BOLD));
        fillRow(inv, 0, Material.YELLOW_STAINED_GLASS_PANE);
        fillRow(inv, 2, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, plain(Material.WHITE_BANNER, "Team Boards", NamedTextColor.YELLOW, List.of(
                "How teams compare"
        )));

        placeEven(inv, 1, List.of(
                cat(Material.RED_BANNER, LeaderboardService.Category.TEAM_MEMBERS, NamedTextColor.RED),
                cat(Material.FILLED_MAP, LeaderboardService.Category.TEAM_CLAIMS, NamedTextColor.GREEN),
                cat(Material.EMERALD_BLOCK, LeaderboardService.Category.TEAM_WEALTH, NamedTextColor.DARK_GREEN)
        ), 0);

        inv.setItem(22, action(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of("Main menu"), "back_main", ""));
        player.openInventory(inv);
    }

    public void openMobHub(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_MOBS, NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
        fillRow(inv, 0, Material.GREEN_STAINED_GLASS_PANE);
        fillRow(inv, 5, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, action(Material.IRON_SWORD, "All Mobs", NamedTextColor.GREEN,
                List.of("Combined mob kill board", "Click to view"), "mobs_all", ""));

        List<ItemStack> mobs = new ArrayList<>();
        for (EntityType type : ActivityStatsService.TRACKED_MOBS) {
            mobs.add(action(mobIcon(type), ActivityStatsService.pretty(type.name()), NamedTextColor.WHITE,
                    List.of("Top killers", "Click to view"), "mob", type.name()));
        }
        placeGrid(inv, mobs, 9, 45);
        inv.setItem(49, action(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of("Activity"), "back_activity", ""));
        player.openInventory(inv);
    }

    public void openCropHub(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_CROPS, NamedTextColor.GOLD, TextDecoration.BOLD));
        fillRow(inv, 0, Material.YELLOW_STAINED_GLASS_PANE);
        fillRow(inv, 5, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, action(Material.HAY_BLOCK, "All Crops", NamedTextColor.YELLOW,
                List.of("Combined farming board", "Click to view"), "crops_all", ""));

        List<ItemStack> crops = new ArrayList<>();
        for (Map.Entry<String, Material> e : ActivityStatsService.TRACKED_CROPS.entrySet()) {
            crops.add(action(cropIcon(e.getKey(), e.getValue()),
                    ActivityStatsService.pretty(e.getKey()), NamedTextColor.WHITE,
                    List.of("Top farmers", "Click to view"), "crop", e.getKey()));
        }
        placeGrid(inv, crops, 9, 45);
        inv.setItem(49, action(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of("Activity"), "back_activity", ""));
        player.openInventory(inv);
    }

    public void openCategory(Player player, LeaderboardService.Category category) {
        boolean heads = switch (category) {
            case TEAM_MEMBERS, TEAM_CLAIMS, TEAM_WEALTH -> false;
            default -> true;
        };
        String back = switch (category) {
            case TEAM_MEMBERS, TEAM_CLAIMS, TEAM_WEALTH -> "back_teams";
            case MOB_KILLS, CROPS -> "back_activity";
            default -> "back_stats";
        };
        openEntries(player, category.title(), category.lore(), boards.top(category, 45), back, heads);
    }

    public void openMobBoard(Player player, EntityType type) {
        String title = ActivityStatsService.pretty(type.name());
        openEntries(player, title, "Most " + title + " kills", boards.topMob(type, 45), "back_mobs", true);
    }

    public void openCropBoard(Player player, String cropKey) {
        String title = ActivityStatsService.pretty(cropKey);
        openEntries(player, title, "Most " + title + " farmed", boards.topCrop(cropKey, 45), "back_crops", true);
    }

    private void openEntries(Player player, String title, String lore, List<LeaderboardService.Entry> entries,
                             String backAction, boolean useHeads) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_PREFIX + title, NamedTextColor.GOLD, TextDecoration.BOLD));
        fillRow(inv, 0, Material.BLACK_STAINED_GLASS_PANE);
        fillRow(inv, 5, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, plain(Material.BOOK, title, NamedTextColor.YELLOW, List.of(
                lore,
                entries.isEmpty() ? "No data yet" : entries.size() + " ranked"
        )));


        List<ItemStack> ranked = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            NamedTextColor color = switch (i) {
                case 0 -> NamedTextColor.GOLD;
                case 1 -> NamedTextColor.WHITE;
                case 2 -> NamedTextColor.RED;
                default -> NamedTextColor.GRAY;
            };
            ranked.add(rankItem(i + 1, entries.get(i), color, useHeads));
        }
        placeGrid(inv, ranked, 9, 45);

        String backLore = switch (backAction) {
            case "back_mobs" -> "Mob types";
            case "back_crops" -> "Crop types";
            case "back_stats" -> "Player stats";
            case "back_activity" -> "Activity";
            case "back_teams" -> "Teams";
            default -> "Main menu";
        };
        inv.setItem(49, action(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of(backLore), backAction, ""));
        player.openInventory(inv);
    }

    public void sendChat(Player player, LeaderboardService.Category category) {
        List<LeaderboardService.Entry> entries = boards.top(category, 10);
        player.sendMessage("§crEconomy §8» §fTop §e" + category.title());
        if (entries.isEmpty()) {
            player.sendMessage("§7No data yet.");
            return;
        }
        int i = 1;
        for (LeaderboardService.Entry e : entries) {
            String prefix = switch (i) {
                case 1 -> "§6#1";
                case 2 -> "§f#2";
                case 3 -> "§c#3";
                default -> "§8#" + i;
            };
            player.sendMessage(prefix + " §f" + e.name() + " §7— §e" + e.detail());
            i++;
        }
        player.sendMessage("§8Open §f/top §8for the full GUI.");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        boolean ours = title.equals(TITLE_MAIN) || title.equals(TITLE_STATS) || title.equals(TITLE_ACTIVITY)
                || title.equals(TITLE_TEAMS) || title.equals(TITLE_MOBS) || title.equals(TITLE_CROPS)
                || title.startsWith(TITLE_PREFIX);
        if (!ours) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String act = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(keyValue, PersistentDataType.STRING);
        if (act == null || act.equals("noop")) return;

        switch (act) {
            case "close" -> player.closeInventory();
            case "hub" -> {
                switch (value == null ? "" : value) {
                    case "stats" -> openStatsHub(player);
                    case "activity" -> openActivityHub(player);
                    case "teams" -> openTeamsHub(player);
                    case "mobs" -> openMobHub(player);
                    case "crops" -> openCropHub(player);
                    default -> {
                    }
                }
            }
            case "category" -> {
                try {
                    openCategory(player, LeaderboardService.Category.valueOf(value));
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "mobs_all" -> openEntries(player, "Mob Kills", "Total mobs killed",
                    boards.top(LeaderboardService.Category.MOB_KILLS, 45), "back_mobs", true);
            case "crops_all" -> openEntries(player, "Crops", "Crops farmed / harvested",
                    boards.top(LeaderboardService.Category.CROPS, 45), "back_crops", true);
            case "mob" -> {
                try {
                    openMobBoard(player, EntityType.valueOf(value));
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "crop" -> openCropBoard(player, value);
            case "back_main" -> openMain(player);
            case "back_stats" -> openStatsHub(player);
            case "back_activity" -> openActivityHub(player);
            case "back_teams" -> openTeamsHub(player);
            case "back_mobs" -> openMobHub(player);
            case "back_crops" -> openCropHub(player);
            default -> {
            }
        }
    }

    private void placeEven(Inventory inv, int row, List<ItemStack> items, int colOffset) {
        int n = items.size();
        if (n == 0) return;
        int start = row * 9;
        int usable = 9 - colOffset;
        if (n == 1) {
            inv.setItem(start + colOffset + usable / 2, items.get(0));
            return;
        }
        for (int i = 0; i < n; i++) {
            int col = colOffset + (int) Math.round(i * (usable - 1) / (double) (n - 1));
            inv.setItem(start + col, items.get(i));
        }
    }

    private void placeGrid(Inventory inv, List<ItemStack> items, int from, int toExclusive) {
        int slot = from;
        for (ItemStack item : items) {
            if (slot >= toExclusive) break;
            inv.setItem(slot++, item);
        }
    }

    private void fillRow(Inventory inv, int row, Material mat) {
        int start = row * 9;
        for (int i = start; i < start + 9; i++) {
            inv.setItem(i, pane(mat));
        }
    }

    private ItemStack rankItem(int rank, LeaderboardService.Entry e, NamedTextColor nameColor, boolean useHead) {
        Material mat = switch (rank) {
            case 1 -> useHead ? Material.PLAYER_HEAD : Material.GOLD_BLOCK;
            case 2 -> useHead ? Material.PLAYER_HEAD : Material.IRON_BLOCK;
            case 3 -> useHead ? Material.PLAYER_HEAD : Material.COPPER_BLOCK;
            default -> useHead ? Material.PLAYER_HEAD : Material.GRAY_DYE;
        };
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (useHead && meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(e.name()));
            meta = skull;
        }
        meta.displayName(Component.text("#" + rank + "  " + e.name(), nameColor)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(e.detail(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        if (rank <= 3) {
            lore.add(Component.empty());
            lore.add(Component.text(switch (rank) {
                case 1 -> "1st place";
                case 2 -> "2nd place";
                default -> "3rd place";
            }, nameColor).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "noop");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "noop");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack cat(Material mat, LeaderboardService.Category category, NamedTextColor color) {
        return action(mat, category.title(), color,
                List.of(category.lore(), "", "Click to view"), "category", category.name());
    }

    private ItemStack action(Material mat, String name, NamedTextColor color, List<String> lore,
                             String act, String value) {
        Material safe = mat.isItem() ? mat : Material.PAPER;
        ItemStack item = new ItemStack(safe);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            if (line.isEmpty()) lines.add(Component.empty());
            else lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, act);
        if (value != null) {
            meta.getPersistentDataContainer().set(keyValue, PersistentDataType.STRING, value);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack plain(Material mat, String name, NamedTextColor color, List<String> lore) {
        return action(mat, name, color, lore, "noop", "");
    }

    private Material cropIcon(String key, Material blockMat) {
        Material icon = switch (key) {
            case "SWEET_BERRIES" -> Material.SWEET_BERRIES;
            case "COCOA" -> Material.COCOA_BEANS;
            case "KELP" -> Material.KELP;
            case "TORCHFLOWER" -> Material.TORCHFLOWER;
            case "PITCHER_POD" -> Material.PITCHER_POD;
            case "MELON" -> Material.MELON_SLICE;
            case "PUMPKIN" -> Material.PUMPKIN;
            case "NETHER_WART" -> Material.NETHER_WART;
            case "BEETROOTS" -> Material.BEETROOT;
            case "CARROTS" -> Material.CARROT;
            case "POTATOES" -> Material.POTATO;
            case "WHEAT" -> Material.WHEAT;
            case "SUGAR_CANE" -> Material.SUGAR_CANE;
            case "CACTUS" -> Material.CACTUS;
            case "BAMBOO" -> Material.BAMBOO;
            default -> blockMat;
        };
        return icon.isItem() ? icon : Material.WHEAT;
    }

    private Material mobIcon(EntityType type) {
        return switch (type) {
            case ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER -> Material.ZOMBIE_HEAD;
            case SKELETON, STRAY, BOGGED -> Material.SKELETON_SKULL;
            case WITHER_SKELETON, WITHER -> Material.WITHER_SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case ENDERMAN, ENDERMITE -> Material.ENDER_PEARL;
            case BLAZE -> Material.BLAZE_ROD;
            case GHAST -> Material.GHAST_TEAR;
            case SPIDER, CAVE_SPIDER -> Material.SPIDER_EYE;
            case SLIME -> Material.SLIME_BALL;
            case MAGMA_CUBE -> Material.MAGMA_CREAM;
            case WITCH -> Material.GLASS_BOTTLE;
            case PHANTOM -> Material.PHANTOM_MEMBRANE;
            case GUARDIAN, ELDER_GUARDIAN -> Material.PRISMARINE_SHARD;
            case SHULKER -> Material.SHULKER_SHELL;
            case PILLAGER, VINDICATOR, EVOKER, RAVAGER, VEX -> Material.CROSSBOW;
            case PIGLIN, PIGLIN_BRUTE -> Material.GOLD_INGOT;
            case HOGLIN, ZOGLIN -> Material.COOKED_PORKCHOP;
            case WARDEN -> Material.SCULK_CATALYST;
            case BREEZE -> Material.BREEZE_ROD;
            case IRON_GOLEM -> Material.IRON_BLOCK;
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            case SILVERFISH -> Material.STONE_BRICKS;
            default -> Material.BONE;
        };
    }
}
