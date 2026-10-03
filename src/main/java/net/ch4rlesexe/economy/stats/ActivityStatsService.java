package net.ch4rlesexe.economy.stats;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActivityStatsService {
    public record NamedCount(String name, long value, String detail) {}


    public static final List<EntityType> TRACKED_MOBS = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.SLIME,
            EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.GHAST, EntityType.WITHER_SKELETON,
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN, EntityType.ZOGLIN,
            EntityType.DROWNED, EntityType.HUSK, EntityType.STRAY, EntityType.PHANTOM,
            EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.SHULKER, EntityType.VEX,
            EntityType.VINDICATOR, EntityType.EVOKER, EntityType.PILLAGER, EntityType.RAVAGER,
            EntityType.WARDEN, EntityType.BREEZE, EntityType.BOGGED, EntityType.IRON_GOLEM,
            EntityType.ZOMBIE_VILLAGER, EntityType.SILVERFISH, EntityType.ENDERMITE,
            EntityType.WITHER, EntityType.ENDER_DRAGON
    );


    public static final Map<String, Material> TRACKED_CROPS = new LinkedHashMap<>();

    static {
        TRACKED_CROPS.put("WHEAT", Material.WHEAT);
        TRACKED_CROPS.put("CARROTS", Material.CARROTS);
        TRACKED_CROPS.put("POTATOES", Material.POTATOES);
        TRACKED_CROPS.put("BEETROOTS", Material.BEETROOTS);
        TRACKED_CROPS.put("NETHER_WART", Material.NETHER_WART);
        TRACKED_CROPS.put("COCOA", Material.COCOA);
        TRACKED_CROPS.put("SWEET_BERRIES", Material.SWEET_BERRY_BUSH);
        TRACKED_CROPS.put("MELON", Material.MELON);
        TRACKED_CROPS.put("PUMPKIN", Material.PUMPKIN);
        TRACKED_CROPS.put("SUGAR_CANE", Material.SUGAR_CANE);
        TRACKED_CROPS.put("CACTUS", Material.CACTUS);
        TRACKED_CROPS.put("BAMBOO", Material.BAMBOO);
        TRACKED_CROPS.put("KELP", Material.KELP_PLANT);
        TRACKED_CROPS.put("TORCHFLOWER", Material.TORCHFLOWER_CROP);
        TRACKED_CROPS.put("PITCHER_POD", Material.PITCHER_CROP);
    }

    private static final Set<Material> AGEABLE_CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.TORCHFLOWER_CROP, Material.PITCHER_CROP
    );

    private final REconomyPlugin plugin;
    private final Database database;

    public ActivityStatsService(REconomyPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void ensure(UUID uuid, String name) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO player_activity (uuid, name) VALUES (?,?)
                 ON DUPLICATE KEY UPDATE name=VALUES(name)
                 """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name == null ? "?" : name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("activity ensure: " + e.getMessage());
        }
    }


    public void syncFromBukkit(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        ensure(uuid, name);

        long playTicks = safeStat(player, Statistic.PLAY_ONE_MINUTE);
        int mobTotal = safeStat(player, Statistic.MOB_KILLS);
        int cropMinedTotal = 0;
        for (Material mat : TRACKED_CROPS.values()) {
            cropMinedTotal += safeBlockStat(player, mat);
        }

        try (Connection c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE player_activity SET name=?,
                      playtime_ticks=?,
                      mob_kills=GREATEST(mob_kills, ?),
                      crop_harvests=GREATEST(crop_harvests, ?)
                    WHERE uuid=?
                    """)) {
                ps.setString(1, name);
                ps.setLong(2, playTicks);
                ps.setInt(3, mobTotal);
                ps.setInt(4, cropMinedTotal);
                ps.setString(5, uuid.toString());
                ps.executeUpdate();
            }

            for (EntityType type : TRACKED_MOBS) {
                int kills = safeEntityStat(player, type);
                if (kills <= 0) continue;
                upsertMob(c, uuid, type.name(), kills, true);
            }

            for (Map.Entry<String, Material> e : TRACKED_CROPS.entrySet()) {
                int mined = safeBlockStat(player, e.getValue());
                if (mined <= 0) continue;
                upsertCrop(c, uuid, e.getKey(), mined, true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("activity sync: " + e.getMessage());
        }
    }

    public void syncAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            syncFromBukkit(p);
        }
    }

    public void recordMobKill(Player killer, EntityType type) {
        if (type == null || type == EntityType.PLAYER) return;
        UUID uuid = killer.getUniqueId();
        ensure(uuid, killer.getName());
        try (Connection c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_activity SET name=?, mob_kills=mob_kills+1 WHERE uuid=?")) {
                ps.setString(1, killer.getName());
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
            upsertMob(c, uuid, type.name(), 1, false);
        } catch (SQLException e) {
            plugin.getLogger().severe("recordMobKill: " + e.getMessage());
        }
    }

    public void recordCropHarvest(Player player, String cropKey, int amount) {
        if (amount < 1) return;
        UUID uuid = player.getUniqueId();
        ensure(uuid, player.getName());
        try (Connection c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_activity SET name=?, crop_harvests=crop_harvests+? WHERE uuid=?")) {
                ps.setString(1, player.getName());
                ps.setInt(2, amount);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
            upsertCrop(c, uuid, cropKey, amount, false);
        } catch (SQLException e) {
            plugin.getLogger().severe("recordCropHarvest: " + e.getMessage());
        }
    }

    public static String cropKeyFor(Material mat) {
        for (Map.Entry<String, Material> e : TRACKED_CROPS.entrySet()) {
            if (e.getValue() == mat) return e.getKey();
        }

        if (mat == Material.KELP) return "KELP";
        return null;
    }

    public static boolean isTrackedAgeableCrop(Material mat) {
        return AGEABLE_CROPS.contains(mat);
    }

    public List<NamedCount> topPlaytime(int limit) {
        return topActivity("playtime_ticks", limit, v -> formatPlaytime(v) + " played");
    }

    public List<NamedCount> topMobKills(int limit) {
        return topActivity("mob_kills", limit, v -> v + " mob kills");
    }

    public List<NamedCount> topCrops(int limit) {
        return topActivity("crop_harvests", limit, v -> v + " crops farmed");
    }

    public List<NamedCount> topMobType(String entityType, int limit) {
        List<NamedCount> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT COALESCE(a.name, m.uuid), m.kills
                 FROM player_mob_kills m
                 LEFT JOIN player_activity a ON a.uuid = m.uuid
                 WHERE m.entity_type=?
                 ORDER BY m.kills DESC
                 LIMIT ?
                 """)) {
            ps.setString(1, entityType);
            ps.setInt(2, clamp(limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int k = rs.getInt(2);
                    out.add(new NamedCount(rs.getString(1), k, k + " " + pretty(entityType)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("topMobType: " + e.getMessage());
        }
        return out;
    }

    public List<NamedCount> topCropType(String crop, int limit) {
        List<NamedCount> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT COALESCE(a.name, c.uuid), c.amount
                 FROM player_crop_harvests c
                 LEFT JOIN player_activity a ON a.uuid = c.uuid
                 WHERE c.crop=?
                 ORDER BY c.amount DESC
                 LIMIT ?
                 """)) {
            ps.setString(1, crop);
            ps.setInt(2, clamp(limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int n = rs.getInt(2);
                    out.add(new NamedCount(rs.getString(1), n, n + " " + pretty(crop)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("topCropType: " + e.getMessage());
        }
        return out;
    }

    public long playtimeTicks(UUID uuid) {
        return scalarActivity(uuid, "playtime_ticks");
    }

    public int mobKills(UUID uuid) {
        return (int) scalarActivity(uuid, "mob_kills");
    }

    public int cropHarvests(UUID uuid) {
        return (int) scalarActivity(uuid, "crop_harvests");
    }

    private long scalarActivity(UUID uuid, String col) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + col + " FROM player_activity WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("scalarActivity: " + e.getMessage());
        }
        return 0;
    }

    private List<NamedCount> topActivity(String column, int limit, java.util.function.LongFunction<String> detail) {
        List<NamedCount> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, " + column + " FROM player_activity WHERE " + column + " > 0 "
                             + "ORDER BY " + column + " DESC LIMIT ?")) {
            ps.setInt(1, clamp(limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long v = rs.getLong(2);
                    out.add(new NamedCount(rs.getString(1), v, detail.apply(v)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("topActivity " + column + ": " + e.getMessage());
        }
        return out;
    }

    private void upsertMob(Connection c, UUID uuid, String type, int amount, boolean absolute) throws SQLException {
        if (absolute) {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO player_mob_kills (uuid, entity_type, kills) VALUES (?,?,?)
                    ON DUPLICATE KEY UPDATE kills=GREATEST(kills, VALUES(kills))
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type);
                ps.setInt(3, amount);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO player_mob_kills (uuid, entity_type, kills) VALUES (?,?,?)
                    ON DUPLICATE KEY UPDATE kills=kills+VALUES(kills)
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type);
                ps.setInt(3, amount);
                ps.executeUpdate();
            }
        }
    }

    private void upsertCrop(Connection c, UUID uuid, String crop, int amount, boolean absolute) throws SQLException {
        if (absolute) {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO player_crop_harvests (uuid, crop, amount) VALUES (?,?,?)
                    ON DUPLICATE KEY UPDATE amount=GREATEST(amount, VALUES(amount))
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, crop);
                ps.setInt(3, amount);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO player_crop_harvests (uuid, crop, amount) VALUES (?,?,?)
                    ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount)
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, crop);
                ps.setInt(3, amount);
                ps.executeUpdate();
            }
        }
    }

    private static int safeStat(OfflinePlayer player, Statistic stat) {
        try {
            return player.getStatistic(stat);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private static int safeEntityStat(OfflinePlayer player, EntityType type) {
        try {
            return player.getStatistic(Statistic.KILL_ENTITY, type);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return 0;
        }
    }

    private static int safeBlockStat(OfflinePlayer player, Material mat) {
        try {
            return player.getStatistic(Statistic.MINE_BLOCK, mat);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return 0;
        }
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    public static String formatPlaytime(long ticks) {

        long seconds = ticks / 20L;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long mins = (seconds % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    public static String pretty(String key) {
        String[] parts = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
