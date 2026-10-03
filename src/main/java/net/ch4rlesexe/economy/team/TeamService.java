package net.ch4rlesexe.economy.team;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class TeamService {
    public record Team(long id, String name, String tag, UUID owner, boolean open) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private Runnable onRosterChange;

    public TeamService(REconomyPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }


    public void setOnRosterChange(Runnable onRosterChange) {
        this.onRosterChange = onRosterChange;
    }

    private void notifyRosterChange() {
        if (onRosterChange == null) return;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, onRosterChange);
    }

    public Optional<Team> getByPlayer(UUID uuid) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT t.id, t.name, t.tag, t.owner_uuid, t.open_join FROM teams t
                 JOIN team_members m ON m.team_id = t.id WHERE m.uuid=?
                 """)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(readTeam(rs));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public Optional<Team> getByName(String name) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, tag, owner_uuid, open_join FROM teams WHERE LOWER(name)=?")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(readTeam(rs));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public Optional<Team> getById(long id) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, tag, owner_uuid, open_join FROM teams WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(readTeam(rs));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private static Team readTeam(ResultSet rs) throws SQLException {
        return new Team(rs.getLong(1), rs.getString(2), rs.getString(3),
                UUID.fromString(rs.getString(4)), rs.getBoolean(5));
    }

    public List<Team> listOpen() {
        List<Team> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, tag, owner_uuid, open_join FROM teams WHERE open_join=1 ORDER BY name LIMIT 45")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readTeam(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("listOpen: " + e.getMessage());
        }
        return out;
    }

    public boolean create(UUID owner, String name, String tag) {
        if (getByPlayer(owner).isPresent()) return false;
        name = name.replaceAll("[^A-Za-z0-9_]", "");
        tag = tag.replaceAll("[^A-Za-z0-9]", "");
        if (name.length() < 3 || name.length() > 16 || tag.isEmpty() || tag.length() > 6) return false;
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO teams (name, tag, owner_uuid, open_join) VALUES (?,?,?,0)",
                    Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement mem = c.prepareStatement(
                         "INSERT INTO team_members (team_id, uuid, role) VALUES (?,?,?)")) {
                ins.setString(1, name);
                ins.setString(2, tag);
                ins.setString(3, owner.toString());
                ins.executeUpdate();
                long id;
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
                mem.setLong(1, id);
                mem.setString(2, owner.toString());
                mem.setString(3, "owner");
                mem.executeUpdate();
                c.commit();
                return true;
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

    public boolean setOpen(UUID actor, boolean open) {
        Optional<Team> team = getByPlayer(actor);
        if (team.isEmpty()) return false;
        String role = getRole(actor).orElse("member");
        if (!canManageClaims(role)) return false;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE teams SET open_join=? WHERE id=?")) {
            ps.setBoolean(1, open);
            ps.setLong(2, team.get().id());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean invite(UUID actor, UUID target) {
        Optional<Team> team = getByPlayer(actor);
        if (team.isEmpty()) return false;
        String role = getRole(actor).orElse("member");
        if (!canManageClaims(role)) return false;
        if (getByPlayer(target).isPresent()) return false;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO team_invites (team_id, uuid) VALUES (?,?) ON DUPLICATE KEY UPDATE uuid=VALUES(uuid)")) {
            ps.setLong(1, team.get().id());
            ps.setString(2, target.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean hasInvite(long teamId, UUID uuid) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM team_invites WHERE team_id=? AND uuid=?")) {
            ps.setLong(1, teamId);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public List<Team> listInvites(UUID uuid) {
        List<Team> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT t.id, t.name, t.tag, t.owner_uuid, t.open_join FROM teams t
                 JOIN team_invites i ON i.team_id=t.id WHERE i.uuid=?
                 """)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readTeam(rs));
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    public boolean leave(UUID uuid) {
        Optional<Team> team = getByPlayer(uuid);
        if (team.isEmpty()) return false;
        if (team.get().owner().equals(uuid)) {
            return disband(team.get().id());
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM team_members WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean disband(long teamId) {
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement a = c.prepareStatement("DELETE FROM claims WHERE team_id=?");
                 PreparedStatement trusts = c.prepareStatement("DELETE FROM claim_trusts WHERE team_id=?");
                 PreparedStatement invites = c.prepareStatement("DELETE FROM team_invites WHERE team_id=?");
                 PreparedStatement homePerms = c.prepareStatement("DELETE FROM team_home_perms WHERE team_id=?");
                 PreparedStatement homes = c.prepareStatement("DELETE FROM team_homes WHERE team_id=?");
                 PreparedStatement b = c.prepareStatement(
                         "DELETE FROM team_relations WHERE team_a=? OR team_b=?");
                 PreparedStatement d = c.prepareStatement("DELETE FROM team_members WHERE team_id=?");
                 PreparedStatement e = c.prepareStatement("DELETE FROM teams WHERE id=?")) {
                a.setLong(1, teamId);
                a.executeUpdate();
                trusts.setLong(1, teamId);
                trusts.executeUpdate();
                invites.setLong(1, teamId);
                invites.executeUpdate();
                homePerms.setLong(1, teamId);
                homePerms.executeUpdate();
                homes.setLong(1, teamId);
                homes.executeUpdate();
                b.setLong(1, teamId);
                b.setLong(2, teamId);
                b.executeUpdate();
                d.setLong(1, teamId);
                d.executeUpdate();
                e.setLong(1, teamId);
                e.executeUpdate();
                c.commit();
                return true;
            } catch (SQLException ex) {
                c.rollback();
                return false;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public Optional<String> getRole(UUID uuid) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT role FROM team_members WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getString(1).toLowerCase(Locale.ROOT));
            }
        } catch (SQLException ignored) {
        }
        return Optional.empty();
    }

    public boolean setRole(UUID actor, UUID target, String newRole) {
        Optional<Team> team = getByPlayer(actor);
        Optional<Team> targetTeam = getByPlayer(target);
        if (team.isEmpty() || targetTeam.isEmpty() || team.get().id() != targetTeam.get().id()) return false;
        if (!team.get().owner().equals(actor)) return false;
        newRole = newRole.toLowerCase(Locale.ROOT);
        if (!List.of("admin", "member", "recruit").contains(newRole)) return false;
        if (target.equals(team.get().owner())) return false;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE team_members SET role=? WHERE uuid=? AND team_id=?")) {
            ps.setString(1, newRole);
            ps.setString(2, target.toString());
            ps.setLong(3, team.get().id());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean kick(UUID actor, UUID target) {
        Optional<Team> team = getByPlayer(actor);
        if (team.isEmpty() || target.equals(team.get().owner())) return false;
        String actorRole = getRole(actor).orElse("member");
        if (!actorRole.equals("owner") && !actorRole.equals("admin")) return false;
        Optional<String> targetRole = getRole(target);
        if (targetRole.isEmpty()) return false;
        if (actorRole.equals("admin") && targetRole.get().equals("admin")) return false;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM team_members WHERE uuid=? AND team_id=?")) {
            ps.setString(1, target.toString());
            ps.setLong(2, team.get().id());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public static boolean canManageClaims(String role) {
        return role.equals("owner") || role.equals("admin");
    }

    public static boolean canManageTrust(String role) {
        return role.equals("owner") || role.equals("admin");
    }

    public int memberCount(long teamId) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM team_members WHERE team_id=?")) {
            ps.setLong(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            return 1;
        }
        return 1;
    }


    public boolean join(UUID player, String teamName) {
        if (getByPlayer(player).isPresent()) return false;
        Optional<Team> team = getByName(teamName);
        if (team.isEmpty()) return false;
        if (!team.get().open() && !hasInvite(team.get().id(), player)) return false;
        return addMember(team.get().id(), player);
    }

    public boolean joinById(UUID player, long teamId) {
        if (getByPlayer(player).isPresent()) return false;
        Optional<Team> team = getById(teamId);
        if (team.isEmpty()) return false;
        if (!team.get().open() && !hasInvite(teamId, player)) return false;
        return addMember(teamId, player);
    }

    private boolean addMember(long teamId, UUID player) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO team_members (team_id, uuid, role) VALUES (?,?,?)");
             PreparedStatement inv = c.prepareStatement(
                     "DELETE FROM team_invites WHERE team_id=? AND uuid=?")) {
            ps.setLong(1, teamId);
            ps.setString(2, player.toString());
            ps.setString(3, "member");
            ps.executeUpdate();
            inv.setLong(1, teamId);
            inv.setString(2, player.toString());
            inv.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }


    @Deprecated
    public boolean inviteJoin(UUID player, String teamName) {
        return join(player, teamName);
    }

    public void setRelation(long a, long b, String relation) {
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO team_relations (team_a, team_b, relation) VALUES (?,?,?)
                 ON DUPLICATE KEY UPDATE relation=VALUES(relation)
                 """)) {
            ps.setLong(1, low);
            ps.setLong(2, high);
            ps.setString(3, relation);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("setRelation: " + e.getMessage());
        }
    }

    public String relation(long a, long b) {
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT relation FROM team_relations WHERE team_a=? AND team_b=?")) {
            ps.setLong(1, low);
            ps.setLong(2, high);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException ignored) {
        }
        return "neutral";
    }

    public List<String> members(long teamId) {
        List<String> list = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, role FROM team_members WHERE team_id=?")) {
            ps.setLong(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString(2) + ":" + rs.getString(1));
            }
        } catch (SQLException ignored) {
        }
        return list;
    }
}
