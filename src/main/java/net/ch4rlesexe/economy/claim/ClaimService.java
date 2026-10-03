package net.ch4rlesexe.economy.claim;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.spawn.SpawnService;
import net.ch4rlesexe.economy.team.TeamService;
import net.ch4rlesexe.economy.team.TeamUpgradeService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class ClaimService {
    public record Claim(String world, int x, int z, long teamId) {}
    public record Trust(UUID uuid, String level) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private final TeamService teams;
    private TeamUpgradeService upgrades;
    private SpawnService spawn;

    public ClaimService(REconomyPlugin plugin, Database database, TeamService teams) {
        this.plugin = plugin;
        this.database = database;
        this.teams = teams;
    }

    public void setUpgrades(TeamUpgradeService upgrades) {
        this.upgrades = upgrades;
    }

    public void setSpawn(SpawnService spawn) {
        this.spawn = spawn;
    }

    public boolean isSpawnProtected(String world, int chunkX, int chunkZ) {
        return spawn != null && spawn.isClaimForbidden(world, chunkX, chunkZ);
    }

    public int perMember() {
        return Math.max(1, plugin.getConfig().getInt("claims.per-member", 10));
    }

    public int maxClaims(long teamId) {
        int members = Math.max(1, teams.memberCount(teamId));
        int bonus = upgrades == null ? 0 : upgrades.bonusClaims(teamId);
        return members * perMember() + bonus;
    }

    public Optional<Claim> get(String world, int chunkX, int chunkZ) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT team_id FROM claims WHERE world=? AND chunk_x=? AND chunk_z=?")) {
            ps.setString(1, world);
            ps.setInt(2, chunkX);
            ps.setInt(3, chunkZ);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Claim(world, chunkX, chunkZ, rs.getLong(1)));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public int count(long teamId) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM claims WHERE team_id=?")) {
            ps.setLong(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public boolean claim(UUID player, String world, int chunkX, int chunkZ) {
        Optional<TeamService.Team> team = teams.getByPlayer(player);
        if (team.isEmpty()) return false;
        String role = teams.getRole(player).orElse("member");
        if (!TeamService.canManageClaims(role)) return false;
        if (isSpawnProtected(world, chunkX, chunkZ)) return false;
        if (get(world, chunkX, chunkZ).isPresent()) return false;
        int max = maxClaims(team.get().id());
        if (count(team.get().id()) >= max) return false;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO claims (world, chunk_x, chunk_z, team_id) VALUES (?,?,?,?)")) {
            ps.setString(1, world);
            ps.setInt(2, chunkX);
            ps.setInt(3, chunkZ);
            ps.setLong(4, team.get().id());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean unclaim(UUID player, String world, int chunkX, int chunkZ) {
        Optional<TeamService.Team> team = teams.getByPlayer(player);
        if (team.isEmpty()) return false;
        String role = teams.getRole(player).orElse("member");
        if (!TeamService.canManageClaims(role)) return false;
        Optional<Claim> claim = get(world, chunkX, chunkZ);
        if (claim.isEmpty() || claim.get().teamId() != team.get().id()) return false;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM claims WHERE world=? AND chunk_x=? AND chunk_z=?")) {
            ps.setString(1, world);
            ps.setInt(2, chunkX);
            ps.setInt(3, chunkZ);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }


    public int unclaimAll(UUID player) {
        Optional<TeamService.Team> team = teams.getByPlayer(player);
        if (team.isEmpty()) return -1;
        String role = teams.getRole(player).orElse("member");
        if (!TeamService.canManageClaims(role)) return -1;
        long teamId = team.get().id();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM claims WHERE team_id=?")) {
            ps.setLong(1, teamId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("unclaimAll failed: " + e.getMessage());
            return -1;
        }
    }

    public List<Claim> list(long teamId) {
        List<Claim> list = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT world, chunk_x, chunk_z, team_id FROM claims WHERE team_id=?")) {
            ps.setLong(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Claim(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getLong(4)));
                }
            }
        } catch (SQLException ignored) {
        }
        return list;
    }

    public boolean trust(UUID actor, UUID target, String level) {
        Optional<TeamService.Team> team = teams.getByPlayer(actor);
        if (team.isEmpty()) return false;
        String role = teams.getRole(actor).orElse("member");
        if (!TeamService.canManageTrust(role)) return false;
        level = normalizeLevel(level);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO claim_trusts (team_id, uuid, level) VALUES (?,?,?)
                 ON DUPLICATE KEY UPDATE level=VALUES(level)
                 """)) {
            ps.setLong(1, team.get().id());
            ps.setString(2, target.toString());
            ps.setString(3, level);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean untrust(UUID actor, UUID target) {
        Optional<TeamService.Team> team = teams.getByPlayer(actor);
        if (team.isEmpty()) return false;
        String role = teams.getRole(actor).orElse("member");
        if (!TeamService.canManageTrust(role)) return false;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM claim_trusts WHERE team_id=? AND uuid=?")) {
            ps.setLong(1, team.get().id());
            ps.setString(2, target.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<Trust> trusts(long teamId) {
        List<Trust> list = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, level FROM claim_trusts WHERE team_id=?")) {
            ps.setLong(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Trust(UUID.fromString(rs.getString(1)), rs.getString(2)));
                }
            }
        } catch (SQLException ignored) {
        }
        return list;
    }

    public Optional<String> trustLevel(long teamId, UUID uuid) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT level FROM claim_trusts WHERE team_id=? AND uuid=?")) {
            ps.setLong(1, teamId);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getString(1));
            }
        } catch (SQLException ignored) {
        }
        return Optional.empty();
    }


    public enum Privilege {
        NONE,
        ACCESS,
        CONTAINER,
        BUILD
    }

    public Privilege privilege(UUID player, String world, int chunkX, int chunkZ) {
        Optional<Claim> claim = get(world, chunkX, chunkZ);
        if (claim.isEmpty()) return Privilege.BUILD;
        Optional<TeamService.Team> team = teams.getByPlayer(player);
        if (team.isPresent() && team.get().id() == claim.get().teamId()) {
            return Privilege.BUILD;
        }
        Optional<String> trust = trustLevel(claim.get().teamId(), player);
        if (trust.isEmpty()) return Privilege.NONE;
        return switch (trust.get()) {
            case "build" -> Privilege.BUILD;
            case "container" -> Privilege.CONTAINER;
            case "access" -> Privilege.ACCESS;
            default -> Privilege.NONE;
        };
    }

    public Privilege privilege(UUID player, org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) return Privilege.BUILD;
        return privilege(player, loc.getWorld().getName(),
                loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public boolean isClaimed(String world, int chunkX, int chunkZ) {
        return get(world, chunkX, chunkZ).isPresent();
    }

    public boolean isClaimed(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return isClaimed(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public Optional<Long> ownerTeamId(String world, int chunkX, int chunkZ) {
        return get(world, chunkX, chunkZ).map(Claim::teamId);
    }

    public boolean sameOwner(String world, int ax, int az, int bx, int bz) {
        Optional<Long> a = ownerTeamId(world, ax, az);
        Optional<Long> b = ownerTeamId(world, bx, bz);
        if (a.isEmpty() && b.isEmpty()) return true;
        return a.isPresent() && b.isPresent() && a.get().equals(b.get());
    }

    public boolean canBuild(UUID player, String world, int chunkX, int chunkZ) {
        return privilege(player, world, chunkX, chunkZ).ordinal() >= Privilege.BUILD.ordinal();
    }

    public boolean canBuild(UUID player, org.bukkit.Location loc) {
        return privilege(player, loc).ordinal() >= Privilege.BUILD.ordinal();
    }

    public boolean canContainer(UUID player, org.bukkit.Location loc) {
        return privilege(player, loc).ordinal() >= Privilege.CONTAINER.ordinal();
    }

    public boolean canAccess(UUID player, org.bukkit.Location loc) {
        return privilege(player, loc).ordinal() >= Privilege.ACCESS.ordinal();
    }


    public boolean canHurtEntities(UUID player, org.bukkit.Location loc) {
        return canBuild(player, loc);
    }

    private static String normalizeLevel(String level) {
        if (level == null) return "build";
        return switch (level.toLowerCase(Locale.ROOT)) {
            case "access", "container", "build" -> level.toLowerCase(Locale.ROOT);
            default -> "build";
        };
    }
}
