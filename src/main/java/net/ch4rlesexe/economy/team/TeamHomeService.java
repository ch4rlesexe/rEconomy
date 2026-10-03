package net.ch4rlesexe.economy.team;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class TeamHomeService {
    public record TeamHome(String name, String accessMode) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private final TeamService teams;
    private TeamUpgradeService upgrades;

    public TeamHomeService(REconomyPlugin plugin, Database database, TeamService teams) {
        this.plugin = plugin;
        this.database = database;
        this.teams = teams;
    }

    public void setUpgrades(TeamUpgradeService upgrades) {
        this.upgrades = upgrades;
    }

    public int limit(long teamId) {
        int base = plugin.getConfig().getInt("teams.home-limit", 2);
        int bonus = upgrades == null ? 0 : upgrades.bonusHomes(teamId);
        return base + bonus;
    }


    @Deprecated
    public int limit() {
        return plugin.getConfig().getInt("teams.home-limit", 2);
    }

    public List<TeamHome> list(long teamId) {
        List<TeamHome> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, access_mode FROM team_homes WHERE team_id=? ORDER BY name")) {
            ps.setLong(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new TeamHome(rs.getString(1), rs.getString(2)));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("team homes list: " + e.getMessage());
        }
        return out;
    }

    public boolean setHome(Player player, String name) {
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) return false;
        String role = teams.getRole(player.getUniqueId()).orElse("member");
        if (!TeamService.canManageClaims(role)) return false;

        final String homeName = sanitize(name);
        if (homeName.isEmpty()) return false;

        List<TeamHome> existing = list(team.get().id());
        Optional<TeamHome> prior = existing.stream().filter(h -> h.name().equals(homeName)).findFirst();
        if (prior.isEmpty() && existing.size() >= limit(team.get().id())) return false;

        Location loc = player.getLocation();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO team_homes (team_id, name, world, x, y, z, yaw, pitch, access_mode)
                 VALUES (?,?,?,?,?,?,?,?,?)
                 ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z),
                   yaw=VALUES(yaw), pitch=VALUES(pitch)
                 """)) {
            ps.setLong(1, team.get().id());
            ps.setString(2, homeName);
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setFloat(7, loc.getYaw());
            ps.setFloat(8, loc.getPitch());
            ps.setString(9, prior.map(TeamHome::accessMode).orElse("all"));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("team setHome: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(Player actor, String name) {
        Optional<TeamService.Team> team = teams.getByPlayer(actor.getUniqueId());
        if (team.isEmpty()) return false;
        String role = teams.getRole(actor.getUniqueId()).orElse("member");
        if (!TeamService.canManageClaims(role)) return false;
        name = sanitize(name);
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement perms = c.prepareStatement(
                    "DELETE FROM team_home_perms WHERE team_id=? AND home_name=?");
                 PreparedStatement home = c.prepareStatement(
                         "DELETE FROM team_homes WHERE team_id=? AND name=?")) {
                perms.setLong(1, team.get().id());
                perms.setString(2, name);
                perms.executeUpdate();
                home.setLong(1, team.get().id());
                home.setString(2, name);
                int n = home.executeUpdate();
                c.commit();
                return n > 0;
            } catch (SQLException e) {
                c.rollback();
                return false;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public Location getLocation(long teamId, String name) {
        name = sanitize(name);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT world,x,y,z,yaw,pitch FROM team_homes WHERE team_id=? AND name=?")) {
            ps.setLong(1, teamId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                World world = Bukkit.getWorld(rs.getString(1));
                if (world == null) return null;
                return new Location(world, rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                        rs.getFloat(5), rs.getFloat(6));
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public Optional<String> accessMode(long teamId, String name) {
        name = sanitize(name);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT access_mode FROM team_homes WHERE team_id=? AND name=?")) {
            ps.setLong(1, teamId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rs.getString(1).toLowerCase(Locale.ROOT));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public boolean canUse(UUID player, long teamId, String name) {
        Optional<TeamService.Team> team = teams.getByPlayer(player);
        if (team.isEmpty() || team.get().id() != teamId) return false;
        String role = teams.getRole(player).orElse("member");
        if (TeamService.canManageClaims(role)) return true;

        Optional<String> mode = accessMode(teamId, name);
        if (mode.isEmpty()) return false;
        if (mode.get().equals("all")) return true;

        return isAllowed(teamId, name, player);
    }

    public boolean teleport(Player player, String name) {
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) return false;
        name = sanitize(name);
        if (!canUse(player.getUniqueId(), team.get().id(), name)) return false;
        Location loc = getLocation(team.get().id(), name);
        if (loc == null) return false;
        player.teleportAsync(loc);
        return true;
    }

    public boolean setAccessMode(Player actor, String homeName, String mode) {
        Optional<TeamService.Team> team = teams.getByPlayer(actor.getUniqueId());
        if (team.isEmpty()) return false;
        String role = teams.getRole(actor.getUniqueId()).orElse("member");
        if (!TeamService.canManageClaims(role)) return false;
        homeName = sanitize(homeName);
        mode = mode.toLowerCase(Locale.ROOT);
        if (!mode.equals("all") && !mode.equals("list")) return false;
        if (accessMode(team.get().id(), homeName).isEmpty()) return false;

        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE team_homes SET access_mode=? WHERE team_id=? AND name=?")) {
            ps.setString(1, mode);
            ps.setLong(2, team.get().id());
            ps.setString(3, homeName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean addPerm(Player actor, String homeName, UUID target) {
        Optional<TeamService.Team> team = teams.getByPlayer(actor.getUniqueId());
        if (team.isEmpty()) return false;
        String role = teams.getRole(actor.getUniqueId()).orElse("member");
        if (!TeamService.canManageClaims(role)) return false;
        homeName = sanitize(homeName);
        if (accessMode(team.get().id(), homeName).isEmpty()) return false;

        Optional<TeamService.Team> targetTeam = teams.getByPlayer(target);
        if (targetTeam.isEmpty() || targetTeam.get().id() != team.get().id()) return false;

        try (Connection c = database.connection();
             PreparedStatement mode = c.prepareStatement(
                     "UPDATE team_homes SET access_mode='list' WHERE team_id=? AND name=?");
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO team_home_perms (team_id, home_name, uuid) VALUES (?,?,?)
                 ON DUPLICATE KEY UPDATE uuid=VALUES(uuid)
                 """)) {
            mode.setLong(1, team.get().id());
            mode.setString(2, homeName);
            mode.executeUpdate();
            ps.setLong(1, team.get().id());
            ps.setString(2, homeName);
            ps.setString(3, target.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean removePerm(Player actor, String homeName, UUID target) {
        Optional<TeamService.Team> team = teams.getByPlayer(actor.getUniqueId());
        if (team.isEmpty()) return false;
        String role = teams.getRole(actor.getUniqueId()).orElse("member");
        if (!TeamService.canManageClaims(role)) return false;
        homeName = sanitize(homeName);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM team_home_perms WHERE team_id=? AND home_name=? AND uuid=?")) {
            ps.setLong(1, team.get().id());
            ps.setString(2, homeName);
            ps.setString(3, target.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean clearPerms(Player actor, String homeName) {
        Optional<TeamService.Team> team = teams.getByPlayer(actor.getUniqueId());
        if (team.isEmpty()) return false;
        String role = teams.getRole(actor.getUniqueId()).orElse("member");
        if (!TeamService.canManageClaims(role)) return false;
        homeName = sanitize(homeName);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM team_home_perms WHERE team_id=? AND home_name=?")) {
            ps.setLong(1, team.get().id());
            ps.setString(2, homeName);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<UUID> allowedPlayers(long teamId, String homeName) {
        homeName = sanitize(homeName);
        List<UUID> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid FROM team_home_perms WHERE team_id=? AND home_name=?")) {
            ps.setLong(1, teamId);
            ps.setString(2, homeName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    public void deleteAllForTeam(long teamId) {
        try (Connection c = database.connection();
             PreparedStatement a = c.prepareStatement("DELETE FROM team_home_perms WHERE team_id=?");
             PreparedStatement b = c.prepareStatement("DELETE FROM team_homes WHERE team_id=?")) {
            a.setLong(1, teamId);
            a.executeUpdate();
            b.setLong(1, teamId);
            b.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("delete team homes: " + e.getMessage());
        }
    }

    private boolean isAllowed(long teamId, String name, UUID player) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM team_home_perms WHERE team_id=? AND home_name=? AND uuid=?")) {
            ps.setLong(1, teamId);
            ps.setString(2, sanitize(name));
            ps.setString(3, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static String sanitize(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    public static String nameOf(UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        return p.getName() != null ? p.getName() : uuid.toString().substring(0, 8);
    }
}
