package net.ch4rlesexe.economy.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.economy.EconomyService;
import net.ch4rlesexe.economy.government.GovernmentService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuctionService implements Listener {
    public static final String GUI_TITLE = "rEconomy AH";
    public static final String GUI_MINE = "rEconomy AH — Mine";

    public record Listing(long id, UUID seller, ItemStack item, double buyout, double highestBid,
                          UUID highestBidder, long expiresAt) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private GovernmentService government;

    public AuctionService(REconomyPlugin plugin, Database database, EconomyService economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
    }

    public void setGovernment(GovernmentService government) {
        this.government = government;
    }

    public boolean list(Player player, double buyout) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage("§crEconomy §8» §7Hold an item to list.");
            return false;
        }
        if (buyout <= 0) {
            player.sendMessage("§crEconomy §8» §7Buyout must be positive.");
            return false;
        }
        int max = plugin.getConfig().getInt("auction.max-listings-per-player", 10);
        if (countListings(player.getUniqueId()) >= max) {
            player.sendMessage("§crEconomy §8» §7Listing limit reached.");
            return false;
        }
        double fee = plugin.getConfig().getDouble("auction.listing-fee", 10);
        if (!economy.withdraw(player.getUniqueId(), fee, "ah-fee")) {
            player.sendMessage("§crEconomy §8» §7Need " + economy.format(fee) + " listing fee.");
            return false;
        }
        long hours = plugin.getConfig().getLong("auction.duration-hours", 24);
        long expires = System.currentTimeMillis() + hours * 3600_000L;
        ItemStack listed = hand.clone();
        byte[] blob = serialize(listed);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO auctions (seller_uuid, item_blob, buyout, expires_at) VALUES (?,?,?,?)")) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setBytes(2, blob);
            ps.setDouble(3, buyout);
            ps.setLong(4, expires);
            ps.executeUpdate();
            hand.setAmount(0);
            if (fee > 1e-9 && government != null) {
                government.collect(fee, "ah-listing-fee");
            }
            player.sendMessage("§crEconomy §8» §7Listed on §fAH §7for §a" + economy.format(buyout)
                    + " §8(fee " + economy.format(fee) + ")");
            return true;
        } catch (SQLException e) {
            economy.deposit(player.getUniqueId(), fee, "ah-fee-refund");
            plugin.getLogger().severe("ah list: " + e.getMessage());
            return false;
        }
    }

    private int countListings(UUID uuid) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM auctions WHERE seller_uuid=? AND collected=0 AND expires_at>?")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public List<Listing> active() {
        return queryListings(
                "SELECT id, seller_uuid, item_blob, buyout, highest_bid, highest_bidder, expires_at "
                        + "FROM auctions WHERE collected=0 AND expires_at>? ORDER BY id DESC LIMIT 45",
                null);
    }

    public List<Listing> mine(UUID seller) {
        return queryListings(
                "SELECT id, seller_uuid, item_blob, buyout, highest_bid, highest_bidder, expires_at "
                        + "FROM auctions WHERE collected=0 AND seller_uuid=? AND expires_at>? ORDER BY id DESC LIMIT 45",
                seller);
    }

    private List<Listing> queryListings(String sql, UUID sellerFilter) {
        List<Listing> list = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (sellerFilter != null) {
                ps.setString(1, sellerFilter.toString());
                ps.setLong(2, System.currentTimeMillis());
            } else {
                ps.setLong(1, System.currentTimeMillis());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID bidder = rs.getString(6) == null ? null : UUID.fromString(rs.getString(6));
                    list.add(new Listing(rs.getLong(1), UUID.fromString(rs.getString(2)), deserialize(rs.getBytes(3)),
                            rs.getDouble(4), rs.getDouble(5), bidder, rs.getLong(7)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("ah query: " + e.getMessage());
        }
        return list;
    }

    public void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(GUI_TITLE, NamedTextColor.RED, TextDecoration.BOLD));
        int slot = 0;
        for (Listing listing : active()) {
            if (slot >= 45) break;
            inv.setItem(slot++, displayListing(listing, player.getUniqueId()));
        }
        ItemStack help = new ItemStack(Material.EMERALD);
        ItemMeta hm = help.getItemMeta();
        hm.displayName(Component.text("List held item", NamedTextColor.GREEN));
        hm.lore(List.of(Component.text("/ah list <price>", NamedTextColor.GRAY)));
        help.setItemMeta(hm);
        inv.setItem(48, help);

        ItemStack mine = new ItemStack(Material.CHEST);
        ItemMeta mm = mine.getItemMeta();
        mm.displayName(Component.text("My Listings", NamedTextColor.GOLD));
        mm.lore(List.of(
                Component.text("Cancel your items here", NamedTextColor.GRAY),
                Component.text("or /ah cancel <id>", NamedTextColor.DARK_GRAY)));
        mine.setItemMeta(mm);
        inv.setItem(50, mine);

        player.openInventory(inv);
    }

    public void openMine(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(GUI_MINE, NamedTextColor.GOLD, TextDecoration.BOLD));
        int slot = 0;
        for (Listing listing : mine(player.getUniqueId())) {
            if (slot >= 45) break;
            inv.setItem(slot++, displayListing(listing, player.getUniqueId()));
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.displayName(Component.text("Back", NamedTextColor.GRAY));
        back.setItemMeta(bm);
        inv.setItem(49, back);
        player.openInventory(inv);
    }

    private ItemStack displayListing(Listing listing, UUID viewer) {
        ItemStack display = listing.item().clone();
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.text("#" + listing.id(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Buyout: ", NamedTextColor.GRAY)
                .append(Component.text(economy.format(listing.buyout()), NamedTextColor.WHITE)));
        String seller = Bukkit.getOfflinePlayer(listing.seller()).getName();
        lore.add(Component.text("Seller: ", NamedTextColor.GRAY)
                .append(Component.text(seller == null ? "?" : seller, NamedTextColor.YELLOW)));
        if (listing.seller().equals(viewer)) {
            lore.add(Component.text("Right-click to cancel", NamedTextColor.RED));
        } else {
            lore.add(Component.text("Left-click to buy", NamedTextColor.GREEN));
        }
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    public boolean buyout(Player buyer, long id) {
        Listing listing = get(id);
        if (listing == null) {
            buyer.sendMessage("§crEconomy §8» §7Listing gone.");
            return false;
        }
        if (listing.seller().equals(buyer.getUniqueId())) {
            buyer.sendMessage("§crEconomy §8» §7Can't buy your own listing. Right-click to cancel.");
            return false;
        }
        double buyout = listing.buyout();
        double salesTaxPct = Math.max(0, Math.min(25,
                plugin.getConfig().getDouble("auction.sales-tax-percent", 0)));
        double salesTax = Math.floor(buyout * (salesTaxPct / 100.0) * 100.0) / 100.0;
        double sellerGets = buyout - salesTax;
        if (!economy.withdraw(buyer.getUniqueId(), buyout, "ah-buyout")) {
            buyer.sendMessage("§crEconomy §8» §7Need " + economy.format(buyout) + ".");
            return false;
        }
        if (sellerGets > 1e-9) {
            economy.deposit(listing.seller(), sellerGets, "ah-sale");
        }
        if (salesTax > 1e-9 && government != null) {
            government.collect(salesTax, "ah-sales-tax");
        }
        if (listing.highestBidder() != null && listing.highestBid() > 0) {
            economy.deposit(listing.highestBidder(), listing.highestBid(), "ah-bid-refund");
        }
        markCollected(id);
        buyer.getInventory().addItem(listing.item()).values()
                .forEach(i -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), i));
        buyer.sendMessage("§crEconomy §8» §7Bought §f#" + id + " §7for §a" + economy.format(buyout)
                + " §8(bal " + economy.format(economy.getBalance(buyer.getUniqueId())) + ")");
        Player seller = Bukkit.getPlayer(listing.seller());
        if (seller != null) {
            seller.sendMessage("§crEconomy §8» §7Your AH listing §f#" + id + " §7sold for §a"
                    + economy.format(sellerGets)
                    + (salesTax > 1e-9 ? " §8(§7tax §f" + economy.format(salesTax) + "§8)" : ""));
        }
        return true;
    }


    public boolean cancel(Player player, long id) {
        Listing listing = get(id);
        if (listing == null) {
            player.sendMessage("§crEconomy §8» §7Listing gone.");
            return false;
        }
        if (!listing.seller().equals(player.getUniqueId())
                && !player.hasPermission("reconomy.economy.admin")) {
            player.sendMessage("§crEconomy §8» §7Not your listing.");
            return false;
        }
        if (listing.highestBidder() != null && listing.highestBid() > 0) {
            economy.deposit(listing.highestBidder(), listing.highestBid(), "ah-bid-refund");
        }
        markCollected(id);
        player.getInventory().addItem(listing.item()).values()
                .forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        player.sendMessage("§crEconomy §8» §7Cancelled AH §f#" + id + " §7— item returned.");
        return true;
    }

    private Listing get(long id) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, seller_uuid, item_blob, buyout, highest_bid, highest_bidder, expires_at "
                             + "FROM auctions WHERE id=? AND collected=0")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                UUID bidder = rs.getString(6) == null ? null : UUID.fromString(rs.getString(6));
                return new Listing(rs.getLong(1), UUID.fromString(rs.getString(2)), deserialize(rs.getBytes(3)),
                        rs.getDouble(4), rs.getDouble(5), bidder, rs.getLong(7));
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private void markCollected(long id) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE auctions SET collected=1 WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        boolean main = title.contains(GUI_TITLE) && !title.contains("Mine");
        boolean mineGui = title.contains(GUI_MINE);
        if (!main && !mineGui) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String display = PlainTextComponentSerializer.plainText().serialize(
                clicked.getItemMeta().displayName() == null
                        ? Component.empty()
                        : clicked.getItemMeta().displayName());
        if (display.equals("My Listings")) {
            openMine(player);
            return;
        }
        if (display.equals("Back")) {
            openGui(player);
            return;
        }
        if (display.equals("List held item")) {
            player.closeInventory();
            player.sendMessage("§crEconomy §8» §7Hold an item, then §f/ah list <price>");
            return;
        }

        Long id = extractId(clicked);
        if (id == null) return;
        if (event.isRightClick()) {
            cancel(player, id);
            if (mineGui) openMine(player);
            else openGui(player);
            return;
        }
        if (event.isLeftClick()) {
            buyout(player, id);
            if (mineGui) openMine(player);
            else openGui(player);
        }
    }

    private static Long extractId(ItemStack clicked) {
        List<Component> lore = clicked.getItemMeta().lore();
        if (lore == null) return null;
        for (Component line : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.startsWith("#")) {
                try {
                    return Long.parseLong(plain.substring(1).trim());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static byte[] serialize(ItemStack item) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeObject(item);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ItemStack deserialize(byte[] data) {
        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            return (ItemStack) ois.readObject();
        } catch (Exception e) {
            return new ItemStack(Material.STONE);
        }
    }
}
