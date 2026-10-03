package net.ch4rlesexe.economy.economy;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.government.GovernmentService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EconomyService {
    private final REconomyPlugin plugin;
    private final Database database;
    private final double startingBalance;

    public EconomyService(REconomyPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.startingBalance = plugin.getConfig().getDouble("starting-balance", 500);
    }

    public String symbol() {
        return plugin.getConfig().getString("currency-symbol", "$");
    }

    public String format(double amount) {
        return symbol() + String.format("%,.2f", amount);
    }

    public void ensureAccount(UUID uuid, String name) {
        boolean gov = GovernmentService.ACCOUNT_UUID.equals(uuid);
        String display = gov ? GovernmentService.ACCOUNT_NAME
                : (name == null ? "unknown" : name);
        double initial = gov ? 0.0 : startingBalance;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO balances (uuid, name, balance) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, display);
            ps.setDouble(3, initial);
            ps.executeUpdate();
            if (name != null || gov) {
                try (PreparedStatement rename = c.prepareStatement("UPDATE balances SET name=? WHERE uuid=?")) {
                    rename.setString(1, display);
                    rename.setString(2, uuid.toString());
                    rename.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("ensureAccount: " + e.getMessage());
        }
    }

    public void setDisplayName(UUID uuid, String name) {
        if (name == null) return;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE balances SET name=? WHERE uuid=?")) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("setDisplayName: " + e.getMessage());
        }
    }

    public double getBalance(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (GovernmentService.ACCOUNT_UUID.equals(uuid)) {
            ensureAccount(uuid, GovernmentService.ACCOUNT_NAME);
        } else {
            ensureAccount(uuid, online != null ? online.getName() : null);
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT balance FROM balances WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("getBalance: " + e.getMessage());
        }
        return 0;
    }

    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) + 1e-9 >= amount;
    }

    public synchronized boolean withdraw(UUID uuid, double amount, String reason) {
        if (amount < 0) {
            return false;
        }
        return adjust(uuid, -amount, reason);
    }

    public synchronized boolean deposit(UUID uuid, double amount, String reason) {
        if (amount < 0) {
            return false;
        }
        return adjust(uuid, amount, reason);
    }

    public synchronized boolean setBalance(UUID uuid, double amount, String reason) {
        if (amount < 0) {
            return false;
        }
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                Player online = Bukkit.getPlayer(uuid);
                String name = online != null ? online.getName() : "unknown";
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT IGNORE INTO balances (uuid, name, balance) VALUES (?, ?, ?)")) {
                    ins.setString(1, uuid.toString());
                    ins.setString(2, name);
                    ins.setDouble(3, startingBalance);
                    ins.executeUpdate();
                }
                double current = 0;
                try (PreparedStatement sel = c.prepareStatement("SELECT balance FROM balances WHERE uuid=?")) {
                    sel.setString(1, uuid.toString());
                    try (ResultSet rs = sel.executeQuery()) {
                        if (rs.next()) current = rs.getDouble(1);
                    }
                }
                double delta = amount - current;
                try (PreparedStatement upd = c.prepareStatement("UPDATE balances SET balance=? WHERE uuid=?")) {
                    upd.setDouble(1, amount);
                    upd.setString(2, uuid.toString());
                    upd.executeUpdate();
                }
                try (PreparedStatement tx = c.prepareStatement(
                        "INSERT INTO transactions (uuid, amount, reason) VALUES (?,?,?)")) {
                    tx.setString(1, uuid.toString());
                    tx.setDouble(2, delta);
                    tx.setString(3, reason == null ? "admin-set" : reason);
                    tx.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("setBalance: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean transfer(UUID from, UUID to, double amount, String reason) {
        if (amount <= 0) {
            return false;
        }
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                if (!adjustOn(c, from, -amount, reason) || !adjustOn(c, to, amount, reason)) {
                    c.rollback();
                    return false;
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("transfer: " + e.getMessage());
            return false;
        }
    }

    private boolean adjust(UUID uuid, double delta, String reason) {
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                boolean ok = adjustOn(c, uuid, delta, reason);
                if (!ok) {
                    c.rollback();
                    return false;
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("adjust: " + e.getMessage());
            return false;
        }
    }

    private boolean adjustOn(Connection c, UUID uuid, double delta, String reason) throws SQLException {
        boolean gov = GovernmentService.ACCOUNT_UUID.equals(uuid);
        Player online = Bukkit.getPlayer(uuid);
        String name = gov ? GovernmentService.ACCOUNT_NAME
                : (online != null ? online.getName() : "unknown");
        double initial = gov ? 0.0 : startingBalance;
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT IGNORE INTO balances (uuid, name, balance) VALUES (?, ?, ?)")) {
            ins.setString(1, uuid.toString());
            ins.setString(2, name);
            ins.setDouble(3, initial);
            ins.executeUpdate();
        }
        try (PreparedStatement upd = c.prepareStatement(
                "UPDATE balances SET balance = balance + ? WHERE uuid=? AND balance + ? >= -0.0001")) {
            upd.setDouble(1, delta);
            upd.setString(2, uuid.toString());
            upd.setDouble(3, delta);
            if (upd.executeUpdate() == 0) {
                return false;
            }
        }
        try (PreparedStatement tx = c.prepareStatement(
                "INSERT INTO transactions (uuid, amount, reason) VALUES (?,?,?)")) {
            tx.setString(1, uuid.toString());
            tx.setDouble(2, delta);
            tx.setString(3, reason);
            tx.executeUpdate();
        }
        return true;
    }

    public List<TopEntry> top(int limit) {
        List<TopEntry> list = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, balance FROM balances WHERE uuid<>? ORDER BY balance DESC LIMIT ?")) {
            ps.setString(1, GovernmentService.ACCOUNT_UUID.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TopEntry(rs.getString(1), rs.getDouble(2)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("top: " + e.getMessage());
        }
        return list;
    }

    public record TopEntry(String name, double balance) {}
}
