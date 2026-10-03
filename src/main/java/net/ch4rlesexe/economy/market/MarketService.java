package net.ch4rlesexe.economy.market;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MarketService implements Listener {
    public record Item(String material, double basePrice, double currentPrice, int stock, boolean frozen) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private BukkitTask engineTask;

    public MarketService(REconomyPlugin plugin, Database database, EconomyService economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
    }

    public void seedDefaults() {
        Object[][] seeds = {
                {"COBBLESTONE", 1.0, 10000},
                {"IRON_INGOT", 25.0, 500},
                {"GOLD_INGOT", 40.0, 400},
                {"DIAMOND", 200.0, 100},
                {"EMERALD", 150.0, 150},
                {"OAK_LOG", 5.0, 2000},
                {"WHEAT", 3.0, 3000},
                {"COOKED_BEEF", 8.0, 1000},
                {"ARROW", 2.0, 2000},
                {"GUNPOWDER", 30.0, 300}
        };
        for (Object[] row : seeds) {
            upsertIfMissing((String) row[0], (Double) row[1], (Integer) row[2]);
        }
    }

    private void upsertIfMissing(String material, double base, int stock) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT IGNORE INTO market_items (material, base_price, current_price, stock)
                 VALUES (?,?,?,?)
                 """)) {
            ps.setString(1, material);
            ps.setDouble(2, base);
            ps.setDouble(3, base);
            ps.setInt(4, stock);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("seed: " + e.getMessage());
        }
    }

    public void startEngine() {
        long minutes = plugin.getConfig().getLong("market.revert-interval-minutes", 5);
        long ticks = Math.max(1, minutes) * 60L * 20L;
        engineTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::softRevert, ticks, ticks);
    }

    public void stopEngine() {
        if (engineTask != null) engineTask.cancel();
    }

    private void softRevert() {
        double damping = 0.05;
        try (Connection c = database.connection();
             PreparedStatement sel = c.prepareStatement(
                     "SELECT material, base_price, current_price, stock, frozen FROM market_items");
             ResultSet rs = sel.executeQuery()) {
            while (rs.next()) {
                if (rs.getBoolean(5)) continue;
                String mat = rs.getString(1);
                double base = rs.getDouble(2);
                double cur = rs.getDouble(3);
                double next = cur + (base - cur) * damping;
                setPrice(mat, next, false);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("market revert: " + e.getMessage());
        }
    }

    public List<Item> all() {
        List<Item> list = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT material, base_price, current_price, stock, frozen FROM market_items ORDER BY material");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Item(rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getInt(4), rs.getBoolean(5)));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("market all: " + e.getMessage());
        }
        return list;
    }

    public Optional<Item> get(String material) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT material, base_price, current_price, stock, frozen FROM market_items WHERE material=?")) {
            ps.setString(1, material.toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Item(rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getInt(4), rs.getBoolean(5)));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public synchronized boolean buy(Player player, String material, int amount) {
        Optional<Item> opt = get(material);
        if (opt.isEmpty() || amount <= 0) return false;
        Item item = opt.get();
        if (item.stock() < amount) {
            player.sendMessage("§cNot enough stock.");
            return false;
        }
        double cost = item.currentPrice() * amount;
        if (!economy.withdraw(player.getUniqueId(), cost, "market-buy")) {
            player.sendMessage("§cInsufficient funds.");
            return false;
        }
        Material mat = Material.matchMaterial(item.material());
        if (mat == null) {
            economy.deposit(player.getUniqueId(), cost, "market-refund");
            return false;
        }
        applyTrade(item.material(), amount, true);
        ItemStack stack = new ItemStack(mat, amount);
        player.getInventory().addItem(stack).values()
                .forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        player.sendMessage("§crEconomy §8» §7Bought §f" + amount + "x " + mat + " §7for §f" + economy.format(cost));
        return true;
    }

    public synchronized boolean sell(Player player, String material, int amount) {
        Optional<Item> opt = get(material);
        if (opt.isEmpty() || amount <= 0) return false;
        Item item = opt.get();
        Material mat = Material.matchMaterial(item.material());
        if (mat == null) return false;
        ItemStack need = new ItemStack(mat, amount);
        if (!player.getInventory().containsAtLeast(need, amount)) {
            player.sendMessage("§cYou don't have that.");
            return false;
        }
        double payout = item.currentPrice() * amount * 0.85;
        player.getInventory().removeItem(need);
        economy.deposit(player.getUniqueId(), payout, "market-sell");
        applyTrade(item.material(), amount, false);
        player.sendMessage("§crEconomy §8» §7Sold §f" + amount + "x " + mat + " §7for §f" + economy.format(payout));
        return true;
    }

    private void applyTrade(String material, int amount, boolean buy) {
        Optional<Item> opt = get(material);
        if (opt.isEmpty()) return;
        Item item = opt.get();
        if (item.frozen()) {
            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE market_items SET stock = stock + ? WHERE material=?")) {
                ps.setInt(1, buy ? -amount : amount);
                ps.setString(2, material);
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
            return;
        }
        double volatility = plugin.getConfig().getDouble("market.volatility", 0.02);
        double minF = plugin.getConfig().getDouble("market.min-price-factor", 0.25);
        double maxF = plugin.getConfig().getDouble("market.max-price-factor", 4.0);
        double factor = buy ? (1.0 + volatility * amount) : (1.0 - volatility * amount);
        double next = Math.max(item.basePrice() * minF, Math.min(item.basePrice() * maxF, item.currentPrice() * factor));
        int stockDelta = buy ? -amount : amount;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 UPDATE market_items SET current_price=?, stock = GREATEST(0, stock + ?),
                   buy_volume = buy_volume + ?, sell_volume = sell_volume + ? WHERE material=?
                 """);
             PreparedStatement hist = c.prepareStatement(
                     "INSERT INTO market_price_history (material, price, stock) VALUES (?,?,?)")) {
            ps.setDouble(1, next);
            ps.setInt(2, stockDelta);
            ps.setInt(3, buy ? amount : 0);
            ps.setInt(4, buy ? 0 : amount);
            ps.setString(5, material);
            ps.executeUpdate();
            hist.setString(1, material);
            hist.setDouble(2, next);
            hist.setInt(3, Math.max(0, item.stock() + stockDelta));
            hist.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("applyTrade: " + e.getMessage());
        }
    }

    public void setPrice(String material, double price, boolean recordHistory) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE market_items SET current_price=? WHERE material=?")) {
            ps.setDouble(1, price);
            ps.setString(2, material.toUpperCase(Locale.ROOT));
            ps.executeUpdate();
            if (recordHistory) {
                Optional<Item> item = get(material);
                if (item.isPresent()) {
                    try (PreparedStatement hist = c.prepareStatement(
                            "INSERT INTO market_price_history (material, price, stock) VALUES (?,?,?)")) {
                        hist.setString(1, material.toUpperCase(Locale.ROOT));
                        hist.setDouble(2, price);
                        hist.setInt(3, item.get().stock());
                        hist.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("setPrice: " + e.getMessage());
        }
    }

    public void setStock(String material, int stock) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE market_items SET stock=? WHERE material=?")) {
            ps.setInt(1, stock);
            ps.setString(2, material.toUpperCase(Locale.ROOT));
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void setBase(String material, double base) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE market_items SET base_price=? WHERE material=?")) {
            ps.setDouble(1, base);
            ps.setString(2, material.toUpperCase(Locale.ROOT));
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void setFrozen(String material, boolean frozen) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE market_items SET frozen=? WHERE material=?")) {
            ps.setBoolean(1, frozen);
            ps.setString(2, material.toUpperCase(Locale.ROOT));
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54, "§cMarket");
        int slot = 0;
        for (Item item : all()) {
            if (slot >= 45) break;
            Material mat = Material.matchMaterial(item.material());
            if (mat == null) continue;
            ItemStack stack = new ItemStack(mat);
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName("§f" + item.material());
            meta.setLore(List.of(
                    "§7Price: §f" + economy.format(item.currentPrice()),
                    "§7Stock: §f" + item.stock(),
                    "§7Base: §8" + economy.format(item.basePrice()),
                    item.frozen() ? "§cFROZEN" : "§aLive",
                    "§eLeft: buy 1 §8| §eShift-left: buy 16",
                    "§eRight: sell 1 §8| §eShift-right: sell 16"
            ));
            stack.setItemMeta(meta);
            inv.setItem(slot++, stack);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle() == null || !event.getView().getTitle().contains("Market")) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        String mat = clicked.getType().name();
        int amount = event.isShiftClick() ? 16 : 1;
        if (event.isLeftClick()) buy(player, mat, amount);
        else if (event.isRightClick()) sell(player, mat, amount);
        Bukkit.getScheduler().runTask(plugin, () -> openGui(player));
    }
}
