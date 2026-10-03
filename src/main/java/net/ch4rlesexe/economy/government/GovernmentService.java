package net.ch4rlesexe.economy.government;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GovernmentService {

    public static final UUID ACCOUNT_UUID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    public static final String ACCOUNT_NAME = "Government";
    public static final String PERMISSION = "reconomy.government.access";

    public record LedgerEntry(
            long id,
            String type,
            double amount,
            String actorName,
            String targetName,
            String reason,
            double balanceAfter,
            Timestamp createdAt
    ) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private final EconomyService economy;

    public GovernmentService(REconomyPlugin plugin, Database database, EconomyService economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        ensure();
    }

    public void ensure() {
        economy.ensureAccount(ACCOUNT_UUID, ACCOUNT_NAME);
        economy.setDisplayName(ACCOUNT_UUID, ACCOUNT_NAME);
    }

    public UUID uuid() {
        return ACCOUNT_UUID;
    }

    public double balance() {
        ensure();
        return economy.getBalance(ACCOUNT_UUID);
    }

    public boolean canAccess(Player player) {
        return player != null && player.hasPermission(PERMISSION);
    }


    public void collect(double amount, String reason) {
        if (amount <= 1e-9) return;
        ensure();
        String tag = reason == null ? "tax" : reason;
        if (!economy.deposit(ACCOUNT_UUID, amount, tag)) {
            plugin.getLogger().warning("Government collect failed (" + tag + "): " + amount);
            return;
        }
        log("collect", amount, null, null, null, null, tag);
    }


    public boolean withdrawTo(Player player, double amount) {
        if (!canAccess(player) || amount <= 0) return false;
        ensure();
        if (!economy.transfer(ACCOUNT_UUID, player.getUniqueId(), amount, "gov-withdraw:" + player.getName())) {
            return false;
        }
        log("withdraw", amount, player.getUniqueId(), player.getName(),
                player.getUniqueId(), player.getName(), "withdraw");
        return true;
    }


    public boolean depositFrom(Player player, double amount) {
        if (!canAccess(player) || amount <= 0) return false;
        ensure();
        if (!economy.transfer(player.getUniqueId(), ACCOUNT_UUID, amount, "gov-deposit:" + player.getName())) {
            return false;
        }
        log("deposit", amount, player.getUniqueId(), player.getName(),
                null, null, "deposit");
        return true;
    }


    public boolean pay(Player actor, OfflinePlayer target, double amount, String note) {
        if (!canAccess(actor) || target == null || target.getUniqueId() == null || amount <= 0) {
            return false;
        }
        ensure();
        UUID targetId = target.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : targetId.toString();
        economy.ensureAccount(targetId, targetName);
        String reason = note == null || note.isBlank()
                ? "gov-pay:" + actor.getName() + "->" + targetName
                : "gov-pay:" + actor.getName() + "->" + targetName + ":" + truncate(note, 40);
        if (!economy.transfer(ACCOUNT_UUID, targetId, amount, reason)) {
            return false;
        }
        log("pay", amount, actor.getUniqueId(), actor.getName(), targetId, targetName,
                note == null || note.isBlank() ? "pay" : truncate(note, 90));
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            online.sendMessage("§crEconomy §8» §7Government paid you §a" + economy.format(amount)
                    + (note == null || note.isBlank() ? "" : " §8(§7" + note + "§8)"));
        }
        return true;
    }

    public List<LedgerEntry> recent(int limit, int offset) {
        List<LedgerEntry> out = new ArrayList<>();
        int lim = Math.max(1, Math.min(50, limit));
        int off = Math.max(0, offset);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT id, type, amount, actor_name, target_name, reason, balance_after, created_at
                 FROM government_ledger
                 ORDER BY id DESC
                 LIMIT ? OFFSET ?
                 """)) {
            ps.setInt(1, lim);
            ps.setInt(2, off);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LedgerEntry(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getDouble(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            rs.getDouble(7),
                            rs.getTimestamp(8)
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("government ledger read: " + e.getMessage());
        }
        return out;
    }

    public int ledgerCount() {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM government_ledger");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("government ledger count: " + e.getMessage());
        }
        return 0;
    }

    private void log(String type, double amount, UUID actorUuid, String actorName,
                     UUID targetUuid, String targetName, String reason) {
        double after = balance();
        String safeReason = truncate(reason == null ? type : reason, 90);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO government_ledger
                   (type, amount, actor_uuid, actor_name, target_uuid, target_name, reason, balance_after)
                 VALUES (?,?,?,?,?,?,?,?)
                 """)) {
            ps.setString(1, type);
            ps.setDouble(2, amount);
            ps.setString(3, actorUuid == null ? null : actorUuid.toString());
            ps.setString(4, actorName);
            ps.setString(5, targetUuid == null ? null : targetUuid.toString());
            ps.setString(6, targetName);
            ps.setString(7, safeReason);
            ps.setDouble(8, after);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("government ledger write: " + e.getMessage());
        }
        plugin.getLogger().info("[Government] " + type + " $" + String.format("%.2f", amount)
                + " reason=" + safeReason
                + (actorName != null ? " by=" + actorName : "")
                + (targetName != null ? " to=" + targetName : "")
                + " bal=" + String.format("%.2f", after));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
