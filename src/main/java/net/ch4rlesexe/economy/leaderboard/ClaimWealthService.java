package net.ch4rlesexe.economy.leaderboard;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.claim.ClaimService;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.market.MarketService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.EnderChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClaimWealthService {
    public record TeamWealth(long teamId, String name, String tag, double cash, double stored, double total) {}

    private static final Map<Material, Double> BASE_VALUES = new EnumMap<>(Material.class);

    static {

        put(Material.DIAMOND, 100);
        put(Material.DIAMOND_BLOCK, 900);
        put(Material.EMERALD, 25);
        put(Material.EMERALD_BLOCK, 225);
        put(Material.NETHERITE_INGOT, 500);
        put(Material.NETHERITE_BLOCK, 4500);
        put(Material.ANCIENT_DEBRIS, 200);
        put(Material.NETHERITE_SCRAP, 80);

        put(Material.GOLD_INGOT, 12);
        put(Material.GOLD_BLOCK, 108);
        put(Material.RAW_GOLD, 10);
        put(Material.RAW_GOLD_BLOCK, 90);
        put(Material.IRON_INGOT, 4);
        put(Material.IRON_BLOCK, 36);
        put(Material.RAW_IRON, 3);
        put(Material.RAW_IRON_BLOCK, 27);
        put(Material.COPPER_INGOT, 2);
        put(Material.COPPER_BLOCK, 18);
        put(Material.RAW_COPPER, 1.5);
        put(Material.LAPIS_LAZULI, 3);
        put(Material.LAPIS_BLOCK, 27);
        put(Material.REDSTONE, 1);
        put(Material.REDSTONE_BLOCK, 9);
        put(Material.COAL, 0.5);
        put(Material.COAL_BLOCK, 4.5);
        put(Material.QUARTZ, 2);
        put(Material.QUARTZ_BLOCK, 8);
        put(Material.AMETHYST_SHARD, 3);
        put(Material.AMETHYST_BLOCK, 12);

        put(Material.NETHERITE_SWORD, 600);
        put(Material.NETHERITE_PICKAXE, 650);
        put(Material.NETHERITE_AXE, 650);
        put(Material.NETHERITE_SHOVEL, 550);
        put(Material.NETHERITE_HOE, 550);
        put(Material.NETHERITE_HELMET, 700);
        put(Material.NETHERITE_CHESTPLATE, 1000);
        put(Material.NETHERITE_LEGGINGS, 900);
        put(Material.NETHERITE_BOOTS, 650);

        put(Material.DIAMOND_SWORD, 200);
        put(Material.DIAMOND_PICKAXE, 300);
        put(Material.DIAMOND_AXE, 300);
        put(Material.DIAMOND_SHOVEL, 100);
        put(Material.DIAMOND_HOE, 200);
        put(Material.DIAMOND_HELMET, 500);
        put(Material.DIAMOND_CHESTPLATE, 800);
        put(Material.DIAMOND_LEGGINGS, 700);
        put(Material.DIAMOND_BOOTS, 400);

        put(Material.TOTEM_OF_UNDYING, 150);
        put(Material.ELYTRA, 400);
        put(Material.ENCHANTED_GOLDEN_APPLE, 200);
        put(Material.GOLDEN_APPLE, 25);
        put(Material.BEACON, 500);
        put(Material.SHULKER_SHELL, 40);
        put(Material.NETHER_STAR, 300);
        put(Material.DRAGON_EGG, 1000);
        put(Material.HEART_OF_THE_SEA, 80);
        put(Material.ECHO_SHARD, 30);
        put(Material.HEAVY_CORE, 250);
        put(Material.TRIDENT, 120);
        put(Material.MACE, 200);
    }

    private static void put(Material mat, double value) {
        BASE_VALUES.put(mat, value);
    }

    private final REconomyPlugin plugin;
    private final Database database;
    private final ClaimService claims;
    private final MarketService market;

    private final CopyOnWriteArrayList<TeamWealth> cache = new CopyOnWriteArrayList<>();
    private volatile long lastScanMs;

    public ClaimWealthService(REconomyPlugin plugin, Database database,
                              ClaimService claims, MarketService market) {
        this.plugin = plugin;
        this.database = database;
        this.claims = claims;
        this.market = market;
    }

    public void start() {
        Bukkit.getScheduler().runTaskLater(plugin, this::rescan, 20L * 20);
        Bukkit.getScheduler().runTaskTimer(plugin, this::rescan, 20L * 60 * 5, 20L * 60 * 5);
    }

    public List<TeamWealth> top(int limit) {
        if (cache.isEmpty() || System.currentTimeMillis() - lastScanMs > 10 * 60_000L) {

            if (System.currentTimeMillis() - lastScanMs > 30_000L) {
                Bukkit.getScheduler().runTask(plugin, this::rescan);
            }
        }
        List<TeamWealth> list = new ArrayList<>(cache);
        list.sort((a, b) -> Double.compare(b.total(), a.total()));
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    public void rescan() {
        List<TeamRow> teamRows = loadTeams();
        List<TeamWealth> next = new ArrayList<>();
        for (TeamRow row : teamRows) {
            double cash = memberCash(row.id);
            double stored = storedInClaims(row.id);
            next.add(new TeamWealth(row.id, row.name, row.tag, cash, stored, cash + stored));
        }
        next.sort((a, b) -> Double.compare(b.total(), a.total()));
        cache.clear();
        cache.addAll(next);
        lastScanMs = System.currentTimeMillis();
        plugin.getLogger().info("Team wealth rescanned (" + next.size() + " teams).");
    }

    private double memberCash(long teamId) {
        double sum = 0;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT COALESCE(SUM(b.balance), 0)
                 FROM team_members tm
                 LEFT JOIN balances b ON b.uuid = tm.uuid
                 WHERE tm.team_id=?
                 """)) {
            ps.setLong(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) sum = rs.getDouble(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("memberCash: " + e.getMessage());
        }
        return sum;
    }

    private double storedInClaims(long teamId) {
        double total = 0;
        for (ClaimService.Claim claim : claims.list(teamId)) {
            World world = Bukkit.getWorld(claim.world());
            if (world == null) continue;
            boolean wasLoaded = world.isChunkLoaded(claim.x(), claim.z());
            Chunk chunk = world.getChunkAt(claim.x(), claim.z());
            total += valueChunkContainers(chunk);
            if (!wasLoaded) {
                world.unloadChunkRequest(claim.x(), claim.z());
            }
        }
        return total;
    }

    private double valueChunkContainers(Chunk chunk) {
        double total = 0;
        Set<Inventory> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof EnderChest) continue;
            if (!(state instanceof Container container)) continue;
            Inventory inv = container.getInventory();

            if (inv.getHolder() instanceof DoubleChest doubleChest) {
                inv = doubleChest.getInventory();
            }
            if (!seen.add(inv)) continue;
            total += valueInventory(inv);
        }
        return total;
    }

    private double valueInventory(Inventory inv) {
        double total = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            total += valueStack(stack);
        }
        return total;
    }

    private double valueStack(ItemStack stack) {
        Material type = stack.getType();
        int amount = stack.getAmount();
        double unit = unitValue(type);


        if (stack.getItemMeta() instanceof BlockStateMeta meta
                && meta.getBlockState() instanceof Container box) {
            unit += valueInventory(box.getInventory()) / Math.max(1, amount);
        }
        return unit * amount;
    }

    private double unitValue(Material type) {

        if (market != null) {
            var item = market.get(type.name());
            if (item.isPresent()) {
                return Math.max(0, item.get().currentPrice() * 0.85);
            }
        }
        return BASE_VALUES.getOrDefault(type, 0.0);
    }

    private record TeamRow(long id, String name, String tag) {}

    private List<TeamRow> loadTeams() {
        List<TeamRow> list = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT id, name, tag FROM teams");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new TeamRow(rs.getLong(1), rs.getString(2), rs.getString(3)));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("loadTeams wealth: " + e.getMessage());
        }
        return list;
    }
}
