package net.ch4rlesexe.economy.team;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.economy.EconomyService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class TeamUpgradeService {
    private final REconomyPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final TeamService teams;

    public TeamUpgradeService(REconomyPlugin plugin, Database database,
                              EconomyService economy, TeamService teams) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.teams = teams;
    }

    public int bonusHomes(long teamId) {
        return get(teamId, "bonus_homes");
    }

    public int bonusClaims(long teamId) {
        return get(teamId, "bonus_claims");
    }

    private int get(long teamId, String col) {
        ensure(teamId);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + col + " FROM team_upgrades WHERE team_id=?")) {
            ps.setLong(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("team upgrades get: " + e.getMessage());
        }
        return 0;
    }

    private void ensure(long teamId) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO team_upgrades (team_id, bonus_homes, bonus_claims) VALUES (?,?,?)")) {
            ps.setLong(1, teamId);
            ps.setInt(2, 0);
            ps.setInt(3, 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("team upgrades ensure: " + e.getMessage());
        }
    }

    public boolean buyHomeSlot(UUID buyer) {
        var team = teams.getByPlayer(buyer);
        if (team.isEmpty()) return false;
        if (!TeamService.canManageClaims(teams.getRole(buyer).orElse("member"))) return false;
        int bonus = bonusHomes(team.get().id());
        int maxBonus = plugin.getConfig().getInt("teams.home-bonus-max", 8);
        if (bonus >= maxBonus) return false;
        double cost = nextHomeCost(team.get().id());
        if (!economy.withdraw(buyer, cost, "team-home-upgrade")) return false;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE team_upgrades SET bonus_homes=bonus_homes+1 WHERE team_id=?")) {
            ps.setLong(1, team.get().id());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            economy.deposit(buyer, cost, "team-home-upgrade-refund");
            return false;
        }
    }


    public double costForClaims(long teamId, int amount) {
        if (amount < 1) return 0;
        double base = plugin.getConfig().getDouble("claims.upgrade-base-cost", 150);
        double growth = plugin.getConfig().getDouble("claims.upgrade-growth", 1.18);
        int start = bonusClaims(teamId);
        double total = 0;
        for (int i = 0; i < amount; i++) {
            total += base * Math.pow(growth, start + i);
        }
        return Math.ceil(total * 100.0) / 100.0;
    }

    public double nextClaimChunkCost(long teamId) {
        return costForClaims(teamId, 1);
    }

    public boolean buyClaimSlots(UUID buyer, int amount) {
        var team = teams.getByPlayer(buyer);
        if (team.isEmpty()) return false;
        if (!TeamService.canManageClaims(teams.getRole(buyer).orElse("member"))) return false;
        if (amount < 1) amount = 1;
        int bonus = bonusClaims(team.get().id());
        int maxBonus = plugin.getConfig().getInt("claims.bonus-max", 200);
        if (bonus + amount > maxBonus) return false;
        double cost = costForClaims(team.get().id(), amount);
        if (!economy.withdraw(buyer, cost, "team-claim-upgrade")) return false;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE team_upgrades SET bonus_claims=bonus_claims+? WHERE team_id=?")) {
            ps.setInt(1, amount);
            ps.setLong(2, team.get().id());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            economy.deposit(buyer, cost, "team-claim-upgrade-refund");
            return false;
        }
    }

    public double nextHomeCost(long teamId) {
        return plugin.getConfig().getDouble("teams.home-upgrade-cost", 750)
                * (bonusHomes(teamId) + 1);
    }


    @Deprecated
    public double claimUnitCost() {
        return plugin.getConfig().getDouble("claims.upgrade-base-cost", 150);
    }
}
