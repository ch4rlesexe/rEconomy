package net.ch4rlesexe.economy.stats;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StatsService {
    public record Stats(UUID uuid, String name, int kills, int deaths, int combatLogs,
                        int killstreak, int bestKillstreak) {
        public double kdr() {
            if (deaths <= 0) return kills;
            return Math.round((kills / (double) deaths) * 100.0) / 100.0;
        }
    }

    private final REconomyPlugin plugin;
    private final Database database;

    public StatsService(REconomyPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void ensure(UUID uuid, String name) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO player_stats (uuid, name) VALUES (?,?)
                 ON DUPLICATE KEY UPDATE name=VALUES(name)
                 """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name == null ? "Unknown" : name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("stats ensure: " + e.getMessage());
        }
    }

    public Stats get(UUID uuid) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, kills, deaths, combat_logs, killstreak, best_killstreak FROM player_stats WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                    ensure(uuid, off.getName());
                    return new Stats(uuid, off.getName() == null ? "?" : off.getName(), 0, 0, 0, 0, 0);
                }
                return new Stats(UUID.fromString(rs.getString(1)), rs.getString(2),
                        rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7));
            }
        } catch (SQLException e) {
            return new Stats(uuid, "?", 0, 0, 0, 0, 0);
        }
    }

    public void recordKill(UUID killer, String killerName, UUID victim, String victimName) {
        ensure(killer, killerName);
        ensure(victim, victimName);
        try (Connection c = database.connection()) {
            try (PreparedStatement k = c.prepareStatement(
                    "UPDATE player_stats SET kills=kills+1, killstreak=killstreak+1, name=? WHERE uuid=?")) {
                k.setString(1, killerName);
                k.setString(2, killer.toString());
                k.executeUpdate();
            }
            try (PreparedStatement b = c.prepareStatement(
                    "UPDATE player_stats SET best_killstreak=GREATEST(best_killstreak, killstreak) WHERE uuid=?")) {
                b.setString(1, killer.toString());
                b.executeUpdate();
            }
            try (PreparedStatement v = c.prepareStatement(
                    "UPDATE player_stats SET deaths=deaths+1, killstreak=0, name=? WHERE uuid=?")) {
                v.setString(1, victimName);
                v.setString(2, victim.toString());
                v.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("recordKill: " + e.getMessage());
        }
    }

    public void recordCombatLog(UUID victim, String victimName, UUID killer, String killerName) {
        ensure(victim, victimName);
        try (Connection c = database.connection();
             PreparedStatement v = c.prepareStatement(
                     "UPDATE player_stats SET deaths=deaths+1, combat_logs=combat_logs+1, killstreak=0, name=? WHERE uuid=?")) {
            v.setString(1, victimName);
            v.setString(2, victim.toString());
            v.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("recordCombatLog victim: " + e.getMessage());
        }
        if (killer != null && !killer.equals(victim)) {
            ensure(killer, killerName);
            try (Connection c = database.connection();
                 PreparedStatement k = c.prepareStatement(
                         "UPDATE player_stats SET kills=kills+1, killstreak=killstreak+1, name=? WHERE uuid=?");
                 PreparedStatement b = c.prepareStatement(
                         "UPDATE player_stats SET best_killstreak=GREATEST(best_killstreak, killstreak) WHERE uuid=?")) {
                k.setString(1, killerName == null ? "?" : killerName);
                k.setString(2, killer.toString());
                k.executeUpdate();
                b.setString(1, killer.toString());
                b.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("recordCombatLog killer: " + e.getMessage());
            }
        }
    }

    public void recordDeath(UUID victim, String victimName) {
        ensure(victim, victimName);
        try (Connection c = database.connection();
             PreparedStatement v = c.prepareStatement(
                     "UPDATE player_stats SET deaths=deaths+1, killstreak=0, name=? WHERE uuid=?")) {
            v.setString(1, victimName);
            v.setString(2, victim.toString());
            v.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("recordDeath: " + e.getMessage());
        }
    }

    public List<Stats> topKills(int limit) {
        return queryTop("kills DESC, deaths ASC", limit);
    }

    public List<Stats> topDeaths(int limit) {
        return queryTop("deaths DESC, kills ASC", limit);
    }

    public List<Stats> topStreak(int limit) {
        return queryTop("best_killstreak DESC, kills DESC", limit);
    }


    public List<Stats> topKdr(int limit, int minKills) {
        List<Stats> list = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, kills, deaths, combat_logs, killstreak, best_killstreak "
                             + "FROM player_stats WHERE kills>=? "
                             + "ORDER BY (kills / GREATEST(deaths, 1)) DESC, kills DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, minKills));
            ps.setInt(2, Math.max(1, Math.min(limit, 50)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(row(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("topKdr: " + e.getMessage());
        }
        return list;
    }

    private List<Stats> queryTop(String orderBy, int limit) {
        List<Stats> list = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, kills, deaths, combat_logs, killstreak, best_killstreak "
                             + "FROM player_stats ORDER BY " + orderBy + " LIMIT ?")) {
            ps.setInt(1, Math.max(1, Math.min(limit, 50)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(row(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("topStats(" + orderBy + "): " + e.getMessage());
        }
        return list;
    }

    private static Stats row(ResultSet rs) throws SQLException {
        return new Stats(UUID.fromString(rs.getString(1)), rs.getString(2),
                rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7));
    }
}
