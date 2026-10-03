package net.ch4rlesexe.economy.livingshop;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopEconomyService {
    public record ItemState(
            String shop,
            String product,
            String material,
            String itemName,
            double buyPrice,
            double sellPrice,
            int supply,
            int stockRemaining,
            int purchased,
            int sold,
            double basePrice,
            double sdRate,
            double demand,
            double bonus,
            double base,
            int week
    ) {
        public boolean unlimited() {
            return supply < 0;
        }
    }

    private final REconomyPlugin plugin;
    private final Database database;
    private final ShopCatalog catalog;
    private final Map<String, ItemState> cache = new ConcurrentHashMap<>();
    private volatile int currentWeek = 0;
    private volatile boolean enabled;

    public ShopEconomyService(REconomyPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        File us = Bukkit.getPluginManager().getPlugin("UltimateShop") != null
                ? Bukkit.getPluginManager().getPlugin("UltimateShop").getDataFolder()
                : new File(plugin.getDataFolder().getParentFile(), "UltimateShop");
        this.catalog = new ShopCatalog(us, plugin.getLogger());
        this.enabled = plugin.getConfig().getBoolean("living-shop.enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ShopCatalog catalog() {
        return catalog;
    }

    public Path defaultCsvPath() {
        String rel = plugin.getConfig().getString("living-shop.csv-path", "manual-economy.csv");
        File f = new File(rel);
        if (f.isAbsolute()) return f.toPath();
        return new File(plugin.getDataFolder(), rel).toPath();
    }

    public int currentWeek() {
        return currentWeek;
    }

    public void load() {
        if (!enabled) return;
        ensureMeta();
        currentWeek = readMetaInt("week", 0);
        reloadCache();
        plugin.getLogger().info("[LivingShop] Loaded " + cache.size() + " tracked item(s), week=" + currentWeek);
    }

    public void reloadCache() {
        cache.clear();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT shop, product, material, item_name, buy_price, sell_price,
                        supply, stock_remaining, purchased, sold,
                        base_price, sd_rate, demand, bonus, base_amount, week
                 FROM shop_economy_items
                 """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ItemState st = mapRow(rs);
                cache.put(st.shop().toLowerCase() + "/" + st.product(), st);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[LivingShop] cache load: " + e.getMessage());
        }
    }

    public Optional<ItemState> get(String shop, String product) {
        return Optional.ofNullable(cache.get(shop.toLowerCase() + "/" + product));
    }

    public List<ItemState> allStates() {
        return new ArrayList<>(cache.values());
    }

    public int cappedCount() {
        int n = 0;
        for (ItemState s : cache.values()) {
            if (!s.unlimited()) n++;
        }
        return n;
    }


    public boolean canBuy(String shop, String product, int amount) {
        ItemState st = cache.get(shop.toLowerCase() + "/" + product);
        if (st == null || st.unlimited()) return true;
        return st.stockRemaining() >= amount;
    }

    public void onTransaction(String shop, String product, boolean buy, int amount, Player player) {
        if (!enabled || amount <= 0) return;
        String key = shop.toLowerCase() + "/" + product;
        ItemState st = cache.get(key);
        if (st == null) {

            ensureItemFromCatalog(shop, product);
            st = cache.get(key);
            if (st == null) return;
        }

        int newPurchased = st.purchased();
        int newSold = st.sold();
        int newStock = st.stockRemaining();
        if (buy) {
            newPurchased += amount;
            if (!st.unlimited()) {
                newStock = Math.max(0, newStock - amount);
            }
        } else {
            newSold += amount;
            if (!st.unlimited()) {
                newStock = newStock + amount;
            }
        }

        ItemState next = new ItemState(
                st.shop(), st.product(), st.material(), st.itemName(),
                st.buyPrice(), st.sellPrice(), st.supply(), newStock,
                newPurchased, newSold, st.basePrice(), st.sdRate(), st.demand(),
                st.bonus(), st.base(), st.week()
        );
        cache.put(key, next);
        persistState(next);
        logTx(shop, product, buy, amount, player, next);
    }

    public ApplyResult applyCsv(Path path) {
        ApplyResult result = new ApplyResult();
        List<ManualEconomyRow> rows;
        try {
            rows = ManualEconomyCsv.read(path);
        } catch (Exception e) {
            result.error = "Failed reading CSV: " + e.getMessage();
            return result;
        }
        if (rows.isEmpty()) {
            result.error = "CSV had no item rows.";
            return result;
        }

        int week = currentWeek + 1;
        Map<String, ShopProduct> byKey = new HashMap<>();
        Map<String, ShopProduct> byItem = new HashMap<>();
        for (ShopProduct p : catalog.loadAll()) {
            byKey.put(p.key(), p);
            byItem.putIfAbsent(p.itemName.toLowerCase(Locale.ROOT), p);
            byItem.putIfAbsent(p.material.toLowerCase(Locale.ROOT), p);
        }

        int updated = 0;
        int skipped = 0;
        for (ManualEconomyRow row : rows) {
            ShopProduct matched = null;
            if (row.shop != null && !row.shop.isBlank() && row.product != null && !row.product.isBlank()) {
                matched = byKey.get(row.shop.trim().toLowerCase(Locale.ROOT) + "/" + row.product.trim());
            }
            if (matched == null && row.item != null && !row.item.isBlank()) {
                matched = byItem.get(row.item.trim().toLowerCase(Locale.ROOT));
            }
            if (matched == null) {
                skipped++;
                result.warnings.add("No shop match for: " + row.item + " (" + row.shop + "/" + row.product + ")");
                continue;
            }

            Double buy = row.buyPrice;
            Double sell = row.sellPrice;
            if (buy == null && sell == null) {
                skipped++;
                continue;
            }
            boolean ok = catalog.updatePrices(matched.shop, matched.product, buy, sell);
            if (!ok) {
                skipped++;
                result.warnings.add("YAML update failed: " + matched.key());
                continue;
            }

            int stockCap = row.resolveStockCap();
            int stockRemaining = stockCap < 0 ? 0 : stockCap;
            double buyV = buy != null ? buy : matched.buyPrice;
            double sellV = sell != null ? sell : matched.sellPrice;
            double baseStock = row.base != null ? row.base
                    : (stockCap < 0 ? 0 : stockCap);
            ItemState st = new ItemState(
                    matched.shop, matched.product, matched.material, matched.itemName,
                    buyV, sellV, stockCap, stockRemaining,
                    0, 0,
                    row.basePrice != null ? row.basePrice : buyV,
                    row.sdRate != null ? row.sdRate : 1.0,
                    row.demand != null ? row.demand : 1.0,
                    row.bonus != null ? row.bonus : 1.0,
                    baseStock,
                    week
            );
            upsertItem(st);
            cache.put(st.shop().toLowerCase() + "/" + st.product(), st);
            updated++;
        }

        currentWeek = week;
        writeMetaInt("week", week);
        reloadShopPlugin();
        result.updated = updated;
        result.skipped = skipped;
        result.week = week;
        return result;
    }

    public Path exportCsv(Path path) throws Exception {
        List<ShopProduct> products = catalog.loadAll();
        Map<String, ItemState> states = new HashMap<>(cache);
        int playersOnline = Bukkit.getOnlinePlayers().size();
        List<ManualEconomyRow> rows = new ArrayList<>();
        for (ShopProduct p : products) {
            ItemState st = states.get(p.key());
            ManualEconomyRow row = new ManualEconomyRow();
            row.shop = p.shop;
            row.product = p.product;
            row.item = p.itemName;
            if (st != null) {
                row.buyPrice = st.buyPrice();
                row.sellPrice = st.sellPrice();
                row.basePrice = st.basePrice();

                if (st.unlimited()) {
                    row.supply = -1;
                    row.stock = null;
                } else {
                    row.supply = 1;
                    row.stock = st.stockRemaining();
                }
                row.purchased = st.purchased();
                row.sold = st.sold();
                row.holdover = st.unlimited() ? null : st.stockRemaining();
                row.sdRate = st.sdRate();
                row.demand = st.demand();
                row.bonus = st.bonus();
                row.base = st.base() > 0 ? st.base() : (st.unlimited() ? 0 : (double) st.supply());
                row.week = st.week();
                row.playerCount = playersOnline;
                row.toDate = st.purchased() + st.sold();
            } else {
                row.buyPrice = p.hasBuy ? p.buyPrice : null;
                row.sellPrice = p.hasSell ? p.sellPrice : null;
                row.basePrice = row.buyPrice;
                row.supply = 1;
                row.stock = null;
                row.purchased = 0;
                row.sold = 0;
                row.holdover = null;
                row.sdRate = 1.0;
                row.demand = 1.0;
                row.bonus = 1.0;
                row.base = 0.0;
                row.week = currentWeek;
                row.playerCount = playersOnline;
                row.toDate = 0;
            }
            rows.add(row);
        }
        ManualEconomyCsv.write(path, rows);
        return path;
    }

    public void seedFromCatalogIfEmpty() {
        if (!cache.isEmpty()) return;
        List<ShopProduct> products = catalog.loadAll();
        for (ShopProduct p : products) {
            ItemState st = new ItemState(
                    p.shop, p.product, p.material, p.itemName,
                    p.buyPrice, p.sellPrice, -1, 0,
                    0, 0, p.buyPrice, 1.0, 1.0, 1.0, 0, currentWeek
            );
            upsertItem(st);
            cache.put(p.key(), st);
        }
        plugin.getLogger().info("[LivingShop] Seeded " + products.size() + " items as unlimited.");
    }

    private void ensureItemFromCatalog(String shop, String product) {
        for (ShopProduct p : catalog.loadAll()) {
            if (p.shop.equalsIgnoreCase(shop) && p.product.equals(product)) {
                ItemState st = new ItemState(
                        p.shop, p.product, p.material, p.itemName,
                        p.buyPrice, p.sellPrice, -1, 0,
                        0, 0, p.buyPrice, 1.0, 1.0, 1.0, 0, currentWeek
                );
                upsertItem(st);
                cache.put(p.key(), st);
                return;
            }
        }
    }

    private void persistState(ItemState st) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 UPDATE shop_economy_items
                 SET stock_remaining=?, purchased=?, sold=?
                 WHERE shop=? AND product=?
                 """)) {
            ps.setInt(1, st.stockRemaining());
            ps.setInt(2, st.purchased());
            ps.setInt(3, st.sold());
            ps.setString(4, st.shop());
            ps.setString(5, st.product());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[LivingShop] persist: " + e.getMessage());
        }
    }

    private void upsertItem(ItemState st) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO shop_economy_items
                   (shop, product, material, item_name, buy_price, sell_price,
                    supply, stock_remaining, purchased, sold,
                    base_price, sd_rate, demand, bonus, base_amount, week)
                 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                 ON DUPLICATE KEY UPDATE
                   material=VALUES(material),
                   item_name=VALUES(item_name),
                   buy_price=VALUES(buy_price),
                   sell_price=VALUES(sell_price),
                   supply=VALUES(supply),
                   stock_remaining=VALUES(stock_remaining),
                   purchased=VALUES(purchased),
                   sold=VALUES(sold),
                   base_price=VALUES(base_price),
                   sd_rate=VALUES(sd_rate),
                   demand=VALUES(demand),
                   bonus=VALUES(bonus),
                   base_amount=VALUES(base_amount),
                   week=VALUES(week)
                 """)) {
            ps.setString(1, st.shop());
            ps.setString(2, st.product());
            ps.setString(3, st.material());
            ps.setString(4, st.itemName());
            ps.setDouble(5, st.buyPrice());
            ps.setDouble(6, st.sellPrice());
            ps.setInt(7, st.supply());
            ps.setInt(8, st.stockRemaining());
            ps.setInt(9, st.purchased());
            ps.setInt(10, st.sold());
            ps.setDouble(11, st.basePrice());
            ps.setDouble(12, st.sdRate());
            ps.setDouble(13, st.demand());
            ps.setDouble(14, st.bonus());
            ps.setDouble(15, st.base());
            ps.setInt(16, st.week());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[LivingShop] upsert: " + e.getMessage());
        }
    }

    private void logTx(String shop, String product, boolean buy, int amount, Player player, ItemState after) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO shop_economy_tx
                   (shop, product, buy_or_sell, amount, player_uuid, player_name,
                    stock_after, purchased_after, sold_after)
                 VALUES (?,?,?,?,?,?,?,?,?)
                 """)) {
            ps.setString(1, shop);
            ps.setString(2, product);
            ps.setString(3, buy ? "buy" : "sell");
            ps.setInt(4, amount);
            ps.setString(5, player != null ? player.getUniqueId().toString() : null);
            ps.setString(6, player != null ? player.getName() : null);
            ps.setInt(7, after.stockRemaining());
            ps.setInt(8, after.purchased());
            ps.setInt(9, after.sold());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[LivingShop] tx log: " + e.getMessage());
        }
    }

    private void reloadShopPlugin() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getPluginManager().getPlugin("UltimateShop") == null) return;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "shop reload");
            plugin.getLogger().info("[LivingShop] Ran /shop reload");
        });
    }

    private void ensureMeta() {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT IGNORE INTO shop_economy_meta (meta_key, meta_value) VALUES ('week', '0')
                 """)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[LivingShop] meta: " + e.getMessage());
        }
    }

    private int readMetaInt(String key, int def) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT meta_value FROM shop_economy_meta WHERE meta_key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Integer.parseInt(rs.getString(1));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[LivingShop] read meta: " + e.getMessage());
        }
        return def;
    }

    private void writeMetaInt(String key, int value) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO shop_economy_meta (meta_key, meta_value) VALUES (?,?)
                 ON DUPLICATE KEY UPDATE meta_value=VALUES(meta_value)
                 """)) {
            ps.setString(1, key);
            ps.setString(2, Integer.toString(value));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[LivingShop] write meta: " + e.getMessage());
        }
    }

    private static ItemState mapRow(ResultSet rs) throws SQLException {
        return new ItemState(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getDouble(5), rs.getDouble(6), rs.getInt(7), rs.getInt(8),
                rs.getInt(9), rs.getInt(10), rs.getDouble(11), rs.getDouble(12),
                rs.getDouble(13), rs.getDouble(14), rs.getDouble(15), rs.getInt(16)
        );
    }

    public static final class ApplyResult {
        public int updated;
        public int skipped;
        public int week;
        public String error;
        public final List<String> warnings = new ArrayList<>();

        public boolean ok() {
            return error == null;
        }
    }
}
