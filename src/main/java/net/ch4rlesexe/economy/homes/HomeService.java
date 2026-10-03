package net.ch4rlesexe.economy.homes;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class HomeService {
    private final REconomyPlugin plugin;
    private final Database database;

    public HomeService(REconomyPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }


    public int limitFor(Player player) {
        int best = -1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission().toLowerCase(Locale.ROOT);
            if (!perm.startsWith("reconomy.homes.")) continue;
            String suffix = perm.substring("reconomy.homes.".length());
            if (suffix.equals("use") || suffix.equals("*")) continue;
            try {
                int n = Integer.parseInt(suffix);
                if (n > best) best = n;
            } catch (NumberFormatException ignored) {
            }
        }
        if (best > 0) return best;
        return plugin.getConfig().getInt("homes.default-limit", 2);
    }

    public List<String> list(UUID uuid) {
        List<String> names = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT name FROM homes WHERE uuid=? ORDER BY name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("homes list: " + e.getMessage());
        }
        return names;
    }

    public boolean setHome(Player player, String name) {
        name = name.toLowerCase(Locale.ROOT);
        List<String> existing = list(player.getUniqueId());
        if (!existing.contains(name) && existing.size() >= limitFor(player)) {
            return false;
        }
        Location loc = player.getLocation();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO homes (uuid, name, world, x, y, z, yaw, pitch)
                 VALUES (?,?,?,?,?,?,?,?)
                 ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z),
                   yaw=VALUES(yaw), pitch=VALUES(pitch)
                 """)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, name);
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setFloat(7, loc.getYaw());
            ps.setFloat(8, loc.getPitch());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("setHome: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(UUID uuid, String name) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM homes WHERE uuid=? AND name=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase(Locale.ROOT));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public Location get(UUID uuid, String name) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT world,x,y,z,yaw,pitch FROM homes WHERE uuid=? AND name=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase(Locale.ROOT));
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
}
