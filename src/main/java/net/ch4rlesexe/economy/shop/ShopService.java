package net.ch4rlesexe.economy.shop;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.economy.EconomyService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class ShopService {
    public record Shop(long id, UUID owner, String world, int x, int y, int z,
                       char mode, double price, int amount, Material material) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private final EconomyService economy;

    public ShopService(REconomyPlugin plugin, Database database, EconomyService economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
    }

    public Optional<Shop> getAt(Block block) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, owner_uuid, world, x, y, z, mode, price, amount, material FROM shops WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, block.getWorld().getName());
            ps.setInt(2, block.getX());
            ps.setInt(3, block.getY());
            ps.setInt(4, block.getZ());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Shop(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                        rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getString(7).charAt(0),
                        rs.getDouble(8), rs.getInt(9), Material.matchMaterial(rs.getString(10))));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public boolean create(Player owner, Block signBlock, char mode, double price, int amount, Material material) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO shops (owner_uuid, world, x, y, z, mode, price, amount, material)
                 VALUES (?,?,?,?,?,?,?,?,?)
                 ON DUPLICATE KEY UPDATE owner_uuid=VALUES(owner_uuid), mode=VALUES(mode),
                   price=VALUES(price), amount=VALUES(amount), material=VALUES(material)
                 """)) {
            ps.setString(1, owner.getUniqueId().toString());
            ps.setString(2, signBlock.getWorld().getName());
            ps.setInt(3, signBlock.getX());
            ps.setInt(4, signBlock.getY());
            ps.setInt(5, signBlock.getZ());
            ps.setString(6, String.valueOf(mode));
            ps.setDouble(7, price);
            ps.setInt(8, amount);
            ps.setString(9, material.name());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("shop create: " + e.getMessage());
            return false;
        }
    }

    public void delete(Block signBlock) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM shops WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, signBlock.getWorld().getName());
            ps.setInt(2, signBlock.getX());
            ps.setInt(3, signBlock.getY());
            ps.setInt(4, signBlock.getZ());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public Block findChest(Block sign) {
        for (var face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.DOWN, org.bukkit.block.BlockFace.UP}) {
            Block rel = sign.getRelative(face);
            if (rel.getState() instanceof Chest) return rel;
        }
        return null;
    }

    public boolean trade(Player buyer, Shop shop, Block sign) {
        Block chestBlock = findChest(sign);
        if (chestBlock == null || !(chestBlock.getState() instanceof Chest chest)) {
            buyer.sendMessage("§cShop chest missing.");
            return false;
        }
        Inventory inv = chest.getBlockInventory();
        ItemStack stack = new ItemStack(shop.material(), shop.amount());
        if (shop.mode() == 'B') {

            if (!economy.has(buyer.getUniqueId(), shop.price())) {
                buyer.sendMessage("§cInsufficient funds.");
                return false;
            }
            if (!inv.containsAtLeast(stack, shop.amount())) {
                buyer.sendMessage("§cShop out of stock.");
                return false;
            }
            if (!economy.transfer(buyer.getUniqueId(), shop.owner(), shop.price(), "shop-buy")) {
                return false;
            }
            inv.removeItem(stack);
            buyer.getInventory().addItem(stack).values().forEach(leftover ->
                    buyer.getWorld().dropItemNaturally(buyer.getLocation(), leftover));
            buyer.sendMessage("§crEconomy §8» §7Bought §f" + shop.amount() + "x " + shop.material()
                    + " §7for §f" + economy.format(shop.price()));
            return true;
        }

        if (!buyer.getInventory().containsAtLeast(stack, shop.amount())) {
            buyer.sendMessage("§cYou lack items.");
            return false;
        }
        if (!economy.has(shop.owner(), shop.price())) {
            buyer.sendMessage("§cShop owner cannot afford this.");
            return false;
        }
        if (inv.firstEmpty() == -1 && !inv.containsAtLeast(stack, 1)) {

        }
        if (!economy.transfer(shop.owner(), buyer.getUniqueId(), shop.price(), "shop-sell")) {
            return false;
        }
        buyer.getInventory().removeItem(stack);
        inv.addItem(stack).values().forEach(leftover ->
                chestBlock.getWorld().dropItemNaturally(chestBlock.getLocation(), leftover));
        buyer.sendMessage("§crEconomy §8» §7Sold §f" + shop.amount() + "x " + shop.material()
                + " §7for §f" + economy.format(shop.price()));
        return true;
    }

    public void updateSign(Sign sign, Shop shop) {
        var side = sign.getSide(Side.FRONT);
        side.setLine(0, "§a[Shop]");
        side.setLine(1, shop.mode() == 'B' ? "§eBuy" : "§bSell");
        side.setLine(2, "§f" + economy.format(shop.price()));
        side.setLine(3, "§7" + shop.amount() + " " + shop.material().name());
        sign.update();
    }
}
