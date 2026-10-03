package net.ch4rlesexe.economy.spawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.util.CooldownService;
import net.ch4rlesexe.economy.util.TeleportWarmupService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class SpawnService {
    private static final int RADIUS = 28;
    private static final int CLEAR_H = 14;

    private final REconomyPlugin plugin;
    private final Database database;
    private CooldownService cooldowns;
    private TeleportWarmupService warmup;
    private Location spawn;

    public SpawnService(REconomyPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void setCooldowns(CooldownService cooldowns) {
        this.cooldowns = cooldowns;
    }

    public void setWarmup(TeleportWarmupService warmup) {
        this.warmup = warmup;
    }

    public void ensureSpawn() {
        if (isBuilt()) {
            loadSpawn();
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isBuilt()) {
                loadSpawn();
                return;
            }
            try {
                buildSpawn();
            } catch (Exception e) {
                plugin.getLogger().severe("Spawn build failed: " + e.getMessage());
                e.printStackTrace();
            }
        }, 60L);
    }

    public void forceRebuild() {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM meta WHERE k='spawn_built'")) {
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                buildSpawn();
            } catch (Exception e) {
                plugin.getLogger().severe("Spawn rebuild failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private boolean isBuilt() {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT v FROM meta WHERE k='spawn_built'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "2".equals(rs.getString(1));
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void markBuilt() {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO meta (k,v) VALUES ('spawn_built','2') ON DUPLICATE KEY UPDATE v='2'")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("meta: " + e.getMessage());
        }
    }

    private void loadSpawn() {
        World world = Bukkit.getWorlds().get(0);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT k,v FROM meta WHERE k IN ('spawn_x','spawn_y','spawn_z','spawn_yaw')")) {
            Double x = null, y = null, z = null;
            float yaw = 180f;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    switch (rs.getString(1)) {
                        case "spawn_x" -> x = Double.parseDouble(rs.getString(2));
                        case "spawn_y" -> y = Double.parseDouble(rs.getString(2));
                        case "spawn_z" -> z = Double.parseDouble(rs.getString(2));
                        case "spawn_yaw" -> yaw = Float.parseFloat(rs.getString(2));
                    }
                }
            }
            if (x != null && y != null && z != null) {
                spawn = new Location(world, x, y, z, yaw, 0f);
                return;
            }
        } catch (Exception ignored) {
        }
        Location def = world.getSpawnLocation();
        spawn = new Location(world, def.getX(), def.getY(), def.getZ(), def.getYaw(), def.getPitch());
    }

    private int[] resolveCenter(World world) {
        int cfgX = plugin.getConfig().getInt("spawn.center-x", Integer.MIN_VALUE);
        int cfgZ = plugin.getConfig().getInt("spawn.center-z", Integer.MIN_VALUE);
        int cfgY = plugin.getConfig().getInt("spawn.base-y", -1);
        if (cfgX != Integer.MIN_VALUE && cfgZ != Integer.MIN_VALUE) {
            int y = cfgY >= 0 ? cfgY : Math.max(60, world.getHighestBlockYAt(cfgX, cfgZ));
            return new int[]{cfgX, y, cfgZ};
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT k,v FROM meta WHERE k IN ('spawn_cx','spawn_cz','spawn_base_y')")) {
            Integer cx = null, cz = null, by = null;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    switch (rs.getString(1)) {
                        case "spawn_cx" -> cx = Integer.parseInt(rs.getString(2));
                        case "spawn_cz" -> cz = Integer.parseInt(rs.getString(2));
                        case "spawn_base_y" -> by = Integer.parseInt(rs.getString(2));
                    }
                }
            }
            if (cx != null && cz != null) {
                int y = by != null ? by : Math.max(60, world.getHighestBlockYAt(cx, cz));
                return new int[]{cx, y, cz};
            }
        } catch (Exception ignored) {
        }
        Location center = world.getSpawnLocation();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int y = world.getHighestBlockYAt(cx, cz);
        if (y < 60) y = 64;
        return new int[]{cx, y, cz};
    }

    private void saveCenter(int cx, int y, int cz, Location spawnLoc) {
        upsertMeta("spawn_cx", Integer.toString(cx));
        upsertMeta("spawn_cz", Integer.toString(cz));
        upsertMeta("spawn_base_y", Integer.toString(y));
        upsertMeta("spawn_x", Double.toString(spawnLoc.getX()));
        upsertMeta("spawn_y", Double.toString(spawnLoc.getY()));
        upsertMeta("spawn_z", Double.toString(spawnLoc.getZ()));
        upsertMeta("spawn_yaw", Float.toString(spawnLoc.getYaw()));
    }

    private void upsertMeta(String k, String v) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO meta (k,v) VALUES (?,?) ON DUPLICATE KEY UPDATE v=VALUES(v)")) {
            ps.setString(1, k);
            ps.setString(2, v);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("meta " + k + ": " + e.getMessage());
        }
    }

    private void buildSpawn() {
        World world = Bukkit.getWorlds().get(0);
        int[] center = resolveCenter(world);
        final int cx = center[0];
        final int y = center[1];
        final int cz = center[2];

        clearVolume(world, cx, y, cz);
        buildFoundation(world, cx, y, cz);
        buildFloor(world, cx, y, cz);
        buildPaths(world, cx, y, cz);
        buildFountain(world, cx, y, cz);
        buildBrandPillars(world, cx, y, cz);
        buildOuterWall(world, cx, y, cz);
        buildGateways(world, cx, y, cz);
        buildShopPavilion(world, cx + 14, y, cz - 14);
        buildAuctionPavilion(world, cx - 14, y, cz - 14);
        buildGardenCourt(world, cx + 14, y, cz + 14);
        buildGardenCourt(world, cx - 14, y, cz + 14);
        plantAvenues(world, cx, y, cz);
        placeBenches(world, cx, y, cz);
        placeLighting(world, cx, y, cz);
        placeWayfinders(world, cx, y, cz);

        Location spawnLoc = new Location(world, cx + 0.5, y + 0.1, cz + 11.5, 180f, 0f);
        world.setSpawnLocation(spawnLoc);
        this.spawn = spawnLoc;
        saveCenter(cx, y, cz, spawnLoc);
        markBuilt();

        Bukkit.getScheduler().runTaskLater(plugin, () -> applyWorldGuard(cx, y, cz), 100L);
        plugin.getLogger().info("Built rEconomy spawn plaza v2 at " + cx + "," + y + "," + cz);
    }

    private void clearVolume(World world, int cx, int y, int cz) {
        int r = RADIUS + 4;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > (r + 1) * (r + 1)) continue;
                for (int dy = -4; dy <= CLEAR_H; dy++) {
                    world.getBlockAt(cx + x, y + dy, cz + z).setType(Material.AIR, false);
                }

                world.getBlockAt(cx + x, y - 4, cz + z).setType(Material.STONE, false);
                world.getBlockAt(cx + x, y - 3, cz + z).setType(Material.STONE, false);
            }
        }
    }

    private void buildFoundation(World world, int cx, int y, int cz) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double dist = Math.hypot(x, z);
                if (dist > RADIUS + 0.35) continue;
                Material fill = dist > RADIUS - 1.2 ? Material.DEEPSLATE : Material.COBBLESTONE;
                world.getBlockAt(cx + x, y - 2, cz + z).setType(fill, false);
            }
        }
    }

    private void buildFloor(World world, int cx, int y, int cz) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double dist = Math.hypot(x, z);
                if (dist > RADIUS + 0.35) continue;

                Block floor = world.getBlockAt(cx + x, y - 1, cz + z);
                if (dist >= RADIUS - 0.9) {
                    floor.setType(Material.POLISHED_DEEPSLATE, false);
                } else if (dist < 5.2) {
                    floor.setType(Material.POLISHED_DIORITE, false);
                } else if (dist < 9.5) {
                    floor.setType(((x + z) & 1) == 0 ? Material.SMOOTH_STONE : Material.STONE, false);
                } else if (isCardinalPath(x, z)) {

                    floor.setType(Material.PACKED_MUD, false);
                } else if (dist < 18) {

                    if (((x * 3 + z * 5) & 7) == 0) {
                        floor.setType(Material.MOSS_BLOCK, false);
                    } else if (((x + z * 2) & 3) == 0) {
                        floor.setType(Material.GRASS_BLOCK, false);
                    } else {
                        floor.setType(Material.COARSE_DIRT, false);
                    }
                } else {
                    floor.setType(((x + z) & 1) == 0
                            ? Material.POLISHED_ANDESITE
                            : Material.ANDESITE, false);
                }
            }
        }
    }

    private static boolean isCardinalPath(int x, int z) {
        return (Math.abs(x) <= 1 && Math.abs(z) >= 5)
                || (Math.abs(z) <= 1 && Math.abs(x) >= 5)
                || (Math.abs(Math.abs(x) - Math.abs(z)) <= 1 && Math.abs(x) >= 6 && Math.abs(x) <= 20);
    }

    private void buildPaths(World world, int cx, int y, int cz) {

        for (int i = 5; i <= RADIUS - 1; i++) {
            for (int w = -1; w <= 1; w++) {
                set(world, cx + w, y - 1, cz + i, Material.PACKED_MUD);
                set(world, cx + w, y - 1, cz - i, Material.PACKED_MUD);
                set(world, cx + i, y - 1, cz + w, Material.PACKED_MUD);
                set(world, cx - i, y - 1, cz + w, Material.PACKED_MUD);
            }

            set(world, cx - 2, y - 1, cz + i, Material.POLISHED_ANDESITE);
            set(world, cx + 2, y - 1, cz + i, Material.POLISHED_ANDESITE);
            set(world, cx - 2, y - 1, cz - i, Material.POLISHED_ANDESITE);
            set(world, cx + 2, y - 1, cz - i, Material.POLISHED_ANDESITE);
            set(world, cx + i, y - 1, cz - 2, Material.POLISHED_ANDESITE);
            set(world, cx + i, y - 1, cz + 2, Material.POLISHED_ANDESITE);
            set(world, cx - i, y - 1, cz - 2, Material.POLISHED_ANDESITE);
            set(world, cx - i, y - 1, cz + 2, Material.POLISHED_ANDESITE);
        }

        for (int i = 6; i <= 18; i++) {
            set(world, cx + i, y - 1, cz - i, Material.PACKED_MUD);
            set(world, cx + i + 1, y - 1, cz - i, Material.PACKED_MUD);
            set(world, cx - i, y - 1, cz - i, Material.PACKED_MUD);
            set(world, cx - i - 1, y - 1, cz - i, Material.PACKED_MUD);
            set(world, cx + i, y - 1, cz + i, Material.PACKED_MUD);
            set(world, cx - i, y - 1, cz + i, Material.PACKED_MUD);
        }
    }

    private void buildFountain(World world, int cx, int y, int cz) {

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                double d = Math.hypot(x, z);
                if (d > 4.2 || d < 2.4) continue;
                set(world, cx + x, y - 1, cz + z, Material.POLISHED_DEEPSLATE);
                if (d >= 3.6) {
                    stair(world, cx + x, y, cz + z, Material.POLISHED_DEEPSLATE_STAIRS, faceOut(x, z));
                }
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 && Math.abs(z) == 2) continue;
                set(world, cx + x, y - 1, cz + z, Material.WATER);
            }
        }

        set(world, cx, y - 1, cz, Material.POLISHED_DIORITE);
        for (int h = 0; h <= 3; h++) {
            set(world, cx, y + h, cz, Material.POLISHED_DIORITE);
        }
        set(world, cx, y + 4, cz, Material.WAXED_COPPER_BLOCK);
        set(world, cx, y + 5, cz, Material.LANTERN);


        set(world, cx + 1, y + 2, cz, Material.WAXED_CUT_COPPER);
        set(world, cx - 1, y + 2, cz, Material.WAXED_CUT_COPPER);
        set(world, cx, y + 2, cz + 1, Material.WAXED_CUT_COPPER);
        set(world, cx, y + 2, cz - 1, Material.WAXED_CUT_COPPER);
        set(world, cx + 1, y + 3, cz, Material.CHAIN);
        set(world, cx - 1, y + 3, cz, Material.CHAIN);
        set(world, cx, y + 3, cz + 1, Material.CHAIN);
        set(world, cx, y + 3, cz - 1, Material.CHAIN);
        set(world, cx + 1, y + 4, cz, Material.LANTERN);
        set(world, cx - 1, y + 4, cz, Material.LANTERN);
        set(world, cx, y + 4, cz + 1, Material.LANTERN);
        set(world, cx, y + 4, cz - 1, Material.LANTERN);


        set(world, cx + 4, y - 1, cz, Material.CRIMSON_NYLIUM);
        set(world, cx - 4, y - 1, cz, Material.CRIMSON_NYLIUM);
        set(world, cx, y - 1, cz + 4, Material.CRIMSON_NYLIUM);
        set(world, cx, y - 1, cz - 4, Material.CRIMSON_NYLIUM);
    }

    private void buildBrandPillars(World world, int cx, int y, int cz) {
        int[][] pts = {{6, 6}, {6, -6}, {-6, 6}, {-6, -6}};
        for (int[] p : pts) {
            int x = cx + p[0];
            int z = cz + p[1];
            set(world, x, y - 1, z, Material.POLISHED_BLACKSTONE);
            for (int h = 0; h < 4; h++) {
                set(world, x, y + h, z, Material.POLISHED_BLACKSTONE);
            }
            set(world, x, y + 4, z, Material.RED_CONCRETE);
            set(world, x, y + 5, z, Material.SOUL_LANTERN);
        }
    }

    private void buildOuterWall(World world, int cx, int y, int cz) {
        for (int angle = 0; angle < 360; angle++) {
            double rad = Math.toRadians(angle);
            int x = (int) Math.round(RADIUS * Math.cos(rad));
            int z = (int) Math.round(RADIUS * Math.sin(rad));

            if (Math.abs(x) <= 2 && Math.abs(z) >= RADIUS - 2) continue;
            if (Math.abs(z) <= 2 && Math.abs(x) >= RADIUS - 2) continue;

            set(world, cx + x, y - 1, cz + z, Material.POLISHED_DEEPSLATE);
            set(world, cx + x, y, cz + z, Material.DEEPSLATE_BRICKS);
            set(world, cx + x, y + 1, cz + z, Material.DEEPSLATE_BRICKS);
            if (angle % 15 == 0) {
                set(world, cx + x, y + 2, cz + z, Material.DEEPSLATE_BRICK_WALL);
                set(world, cx + x, y + 3, cz + z, Material.LANTERN);
            } else if (angle % 5 == 0) {
                slab(world, cx + x, y + 2, cz + z, Material.DEEPSLATE_BRICK_SLAB, true);
            }
        }
    }

    private void buildGateways(World world, int cx, int y, int cz) {

        buildArch(world, cx, y, cz + RADIUS, BlockFace.SOUTH, "WILD");
        buildArch(world, cx, y, cz - RADIUS, BlockFace.NORTH, "WILD");
        buildArch(world, cx + RADIUS, y, cz, BlockFace.EAST, "WILD");
        buildArch(world, cx - RADIUS, y, cz, BlockFace.WEST, "WILD");
    }

    private void buildArch(World world, int x, int y, int z, BlockFace out, String label) {
        boolean ns = out == BlockFace.NORTH || out == BlockFace.SOUTH;

        for (int h = 0; h < 5; h++) {
            if (ns) {
                set(world, x - 3, y + h, z, Material.POLISHED_DEEPSLATE);
                set(world, x + 3, y + h, z, Material.POLISHED_DEEPSLATE);
            } else {
                set(world, x, y + h, z - 3, Material.POLISHED_DEEPSLATE);
                set(world, x, y + h, z + 3, Material.POLISHED_DEEPSLATE);
            }
        }
        if (ns) {
            for (int dx = -3; dx <= 3; dx++) {
                set(world, x + dx, y + 5, z, Material.POLISHED_DEEPSLATE);
                if (Math.abs(dx) <= 1) {
                    set(world, x + dx, y + 4, z, Material.WAXED_CUT_COPPER);
                }
            }
            set(world, x - 3, y + 4, z, Material.POLISHED_DEEPSLATE);
            set(world, x + 3, y + 4, z, Material.POLISHED_DEEPSLATE);
            placeWallSign(world, x, y + 3, z + (out == BlockFace.SOUTH ? 1 : -1), out, label, "EXIT");
        } else {
            for (int dz = -3; dz <= 3; dz++) {
                set(world, x, y + 5, z + dz, Material.POLISHED_DEEPSLATE);
                if (Math.abs(dz) <= 1) {
                    set(world, x, y + 4, z + dz, Material.WAXED_CUT_COPPER);
                }
            }
            set(world, x, y + 4, z - 3, Material.POLISHED_DEEPSLATE);
            set(world, x, y + 4, z + 3, Material.POLISHED_DEEPSLATE);
            placeWallSign(world, x + (out == BlockFace.EAST ? 1 : -1), y + 3, z, out, label, "EXIT");
        }
        set(world, x, y + 6, z, Material.RED_CONCRETE);
        set(world, x, y + 7, z, Material.LANTERN);


        for (int w = -2; w <= 2; w++) {
            for (int h = 0; h < 4; h++) {
                if (ns) {
                    set(world, x + w, y + h, z, Material.AIR);
                    set(world, x + w, y - 1, z, Material.PACKED_MUD);
                } else {
                    set(world, x, y + h, z + w, Material.AIR);
                    set(world, x, y - 1, z + w, Material.PACKED_MUD);
                }
            }
        }
    }

    private void buildShopPavilion(World world, int ox, int y, int oz) {
        buildPavilion(world, ox, y, oz, Material.SPRUCE_PLANKS, Material.SPRUCE_STAIRS,
                Material.SPRUCE_SLAB, "SHOP", "/shop", NamedTextColor.GOLD);

        set(world, ox, y, oz, Material.CARTOGRAPHY_TABLE);
    }

    private void buildAuctionPavilion(World world, int ox, int y, int oz) {
        buildPavilion(world, ox, y, oz, Material.DARK_OAK_PLANKS, Material.DARK_OAK_STAIRS,
                Material.DARK_OAK_SLAB, "AUCTION", "/ah", NamedTextColor.AQUA);
        set(world, ox, y, oz, Material.LECTERN);
    }

    private void buildPavilion(World world, int ox, int y, int oz,
                               Material plank, Material stairMat, Material slabMat,
                               String title, String cmd, NamedTextColor accent) {

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                set(world, ox + x, y - 1, oz + z, Material.POLISHED_ANDESITE);
            }
        }

        int[][] cols = {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}};
        for (int[] c : cols) {
            for (int h = 0; h < 4; h++) {
                set(world, ox + c[0], y + h, oz + c[1], Material.STRIPPED_SPRUCE_LOG);
            }
        }

        for (int layer = 0; layer <= 3; layer++) {
            int s = 3 - layer;
            for (int x = -s; x <= s; x++) {
                for (int z = -s; z <= s; z++) {
                    if (layer == 0) {
                        set(world, ox + x, y + 4, oz + z, plank);
                    } else if (Math.abs(x) == s || Math.abs(z) == s) {
                        set(world, ox + x, y + 4 + layer, oz + z, plank);
                    }
                }
            }
        }
        set(world, ox, y + 8, oz, Material.RED_CONCRETE);
        set(world, ox, y + 9, oz, Material.LANTERN);


        for (int w = -1; w <= 1; w++) {
            stair(world, ox + w, y, oz + 3, stairMat, BlockFace.SOUTH);
        }
        slab(world, ox, y + 4, oz, slabMat, true);

        placeSign(world, ox, y, oz + 2, BlockFace.SOUTH, title, cmd, "rEconomy", accent);
    }

    private void buildGardenCourt(World world, int ox, int y, int oz) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    set(world, ox + x, y - 1, oz + z, Material.MOSSY_STONE_BRICKS);
                    if ((x + z) % 2 == 0) {
                        set(world, ox + x, y, oz + z, Material.MOSSY_STONE_BRICK_WALL);
                    }
                } else {
                    set(world, ox + x, y - 1, oz + z, Material.GRASS_BLOCK);
                }
            }
        }
        plantTree(world, ox, y, oz);
        set(world, ox + 2, y, oz, Material.POPPY);
        set(world, ox - 2, y, oz, Material.AZURE_BLUET);
        set(world, ox, y, oz + 2, Material.CORNFLOWER);
        set(world, ox, y, oz - 2, Material.ALLIUM);
        set(world, ox + 1, y, oz + 1, Material.RED_TULIP);
        set(world, ox - 1, y, oz - 1, Material.WHITE_TULIP);
    }

    private void plantAvenues(World world, int cx, int y, int cz) {
        int[][] trees = {
                {10, 4}, {10, -4}, {-10, 4}, {-10, -4},
                {4, 10}, {4, -10}, {-4, 10}, {-4, -10},
                {18, 8}, {18, -8}, {-18, 8}, {-18, -8},
                {8, 18}, {8, -18}, {-8, 18}, {-8, -18}
        };
        for (int[] t : trees) {

            if (Math.abs(t[0]) <= 2 || Math.abs(t[1]) <= 2) continue;
            plantTree(world, cx + t[0], y, cz + t[1]);
        }
    }

    private void placeBenches(World world, int cx, int y, int cz) {
        placeBench(world, cx + 8, y, cz + 5, BlockFace.SOUTH);
        placeBench(world, cx - 9, y, cz + 5, BlockFace.SOUTH);
        placeBench(world, cx + 8, y, cz - 5, BlockFace.NORTH);
        placeBench(world, cx - 9, y, cz - 5, BlockFace.NORTH);
        placeBench(world, cx + 5, y, cz + 8, BlockFace.EAST);
        placeBench(world, cx + 5, y, cz - 9, BlockFace.EAST);
        placeBench(world, cx - 5, y, cz + 8, BlockFace.WEST);
        placeBench(world, cx - 5, y, cz - 9, BlockFace.WEST);
    }

    private void placeLighting(World world, int cx, int y, int cz) {
        for (int angle = 0; angle < 360; angle += 20) {
            double rad = Math.toRadians(angle);
            int lx = cx + (int) Math.round((RADIUS - 3) * Math.cos(rad));
            int lz = cz + (int) Math.round((RADIUS - 3) * Math.sin(rad));
            if (Math.abs(lx - cx) <= 2 || Math.abs(lz - cz) <= 2) continue;
            set(world, lx, y - 1, lz, Material.POLISHED_DEEPSLATE);
            set(world, lx, y, lz, Material.DEEPSLATE_BRICK_WALL);
            set(world, lx, y + 1, lz, Material.LANTERN);
        }

        for (int i = 8; i <= 24; i += 4) {
            setLamp(world, cx + 3, y, cz + i);
            setLamp(world, cx - 3, y, cz + i);
            setLamp(world, cx + 3, y, cz - i);
            setLamp(world, cx - 3, y, cz - i);
            setLamp(world, cx + i, y, cz + 3);
            setLamp(world, cx + i, y, cz - 3);
            setLamp(world, cx - i, y, cz + 3);
            setLamp(world, cx - i, y, cz - 3);
        }
    }

    private void setLamp(World world, int x, int y, int z) {
        set(world, x, y - 1, z, Material.POLISHED_ANDESITE);
        set(world, x, y, z, Material.ANDESITE_WALL);
        set(world, x, y + 1, z, Material.LANTERN);
    }

    private void placeWayfinders(World world, int cx, int y, int cz) {
        placeSign(world, cx + 1, y, cz + 9, BlockFace.SOUTH,
                "ECONOMY", "SMP", "/spawn", NamedTextColor.RED);
        placeSign(world, cx - 1, y, cz + 9, BlockFace.SOUTH,
                "Welcome", "play.example.com", "Teams · Coins", NamedTextColor.GRAY);
        placeSign(world, cx + 9, y, cz, BlockFace.EAST,
                "SHOP", "Open /shop", "Buy & Sell", NamedTextColor.GOLD);
        placeSign(world, cx - 9, y, cz, BlockFace.WEST,
                "AUCTION", "Open /ah", "Player market", NamedTextColor.AQUA);
        placeSign(world, cx, y, cz - 9, BlockFace.NORTH,
                "CLAIMS", "/team · /claim", "Protect land", NamedTextColor.GREEN);
    }

    private void applyWorldGuard(int cx, int y, int cz) {
        try {
            int r = RADIUS + 2;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "//world world");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "//pos1 " + (cx - r) + "," + (y - 4) + "," + (cz - r));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "//pos2 " + (cx + r) + "," + (y + CLEAR_H) + "," + (cz + r));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg remove spawn");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg define spawn");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag spawn pvp deny");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag spawn build deny");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag spawn block-break deny");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag spawn block-place deny");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag spawn mob-spawning deny");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag spawn tnt deny");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag spawn creeper-explosion deny");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "rg flag spawn greet-message &cEconomy &8» &7Spawn Plaza");
            plugin.getLogger().info("Spawn WorldGuard region applied.");
        } catch (Exception e) {
            plugin.getLogger().warning("WG spawn protect failed: " + e.getMessage());
        }
    }



    private static void set(World world, int x, int y, int z, Material mat) {
        world.getBlockAt(x, y, z).setType(mat, false);
    }

    private static void slab(World world, int x, int y, int z, Material mat, boolean top) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(mat, false);
        if (b.getBlockData() instanceof Slab s) {
            s.setType(top ? Slab.Type.TOP : Slab.Type.BOTTOM);
            b.setBlockData(s, false);
        }
    }

    private static void stair(World world, int x, int y, int z, Material mat, BlockFace face) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(mat, false);
        if (b.getBlockData() instanceof Stairs s) {
            s.setFacing(face);
            s.setHalf(Bisected.Half.BOTTOM);
            b.setBlockData(s, false);
        }
    }

    private static BlockFace faceOut(int x, int z) {
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return z > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private static void plantTree(World world, int x, int y, int z) {
        set(world, x, y - 1, z, Material.PODZOL);
        for (int h = 0; h < 5; h++) {
            set(world, x, y + h, z, Material.DARK_OAK_LOG);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 6; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + Math.max(0, dy - 4) > 4) continue;
                    Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (b.getType().isAir()) b.setType(Material.DARK_OAK_LEAVES, false);
                }
            }
        }
    }

    private static void placeBench(World world, int x, int y, int z, BlockFace face) {
        BlockFace along = (face == BlockFace.NORTH || face == BlockFace.SOUTH)
                ? BlockFace.EAST : BlockFace.SOUTH;
        for (int i = 0; i < 2; i++) {
            int bx = x + (along == BlockFace.EAST ? i : 0);
            int bz = z + (along == BlockFace.SOUTH ? i : 0);
            stair(world, bx, y, bz, Material.SPRUCE_STAIRS, face);
        }
    }

    private void placeSign(World world, int x, int y, int z, BlockFace facing,
                           String l0, String l1, String l2, NamedTextColor color) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.SPRUCE_SIGN, false);
        if (b.getBlockData() instanceof Rotatable r) {
            r.setRotation(facing);
            b.setBlockData(r, false);
        }
        if (b.getState() instanceof Sign sign) {
            var side = sign.getSide(Side.FRONT);
            side.line(0, Component.text(l0, color, TextDecoration.BOLD));
            side.line(1, Component.text(l1, NamedTextColor.WHITE));
            side.line(2, Component.text(l2, NamedTextColor.DARK_GRAY));
            side.line(3, Component.empty());
            sign.update(true, false);
        }
    }

    private void placeWallSign(World world, int x, int y, int z, BlockFace facing,
                               String l0, String l1) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.SPRUCE_WALL_SIGN, false);
        if (b.getBlockData() instanceof Directional d) {
            d.setFacing(facing);
            b.setBlockData(d, false);
        }
        if (b.getState() instanceof Sign sign) {
            var side = sign.getSide(Side.FRONT);
            side.line(0, Component.text(l0, NamedTextColor.RED, TextDecoration.BOLD));
            side.line(1, Component.text(l1, NamedTextColor.GRAY));
            sign.update(true, false);
        }
    }

    public void setSpawn(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        world.setSpawnLocation(loc);
        this.spawn = loc.clone();
        upsertMeta("spawn_x", Double.toString(loc.getX()));
        upsertMeta("spawn_y", Double.toString(loc.getY()));
        upsertMeta("spawn_z", Double.toString(loc.getZ()));
        upsertMeta("spawn_yaw", Float.toString(loc.getYaw()));
        upsertMeta("spawn_cx", Integer.toString(loc.getBlockX()));
        upsertMeta("spawn_cz", Integer.toString(loc.getBlockZ()));
        upsertMeta("spawn_base_y", Integer.toString(loc.getBlockY()));

        plugin.getConfig().set("spawn.center-x", loc.getBlockX());
        plugin.getConfig().set("spawn.center-z", loc.getBlockZ());
        plugin.getConfig().set("spawn.base-y", loc.getBlockY());
        plugin.saveConfig();
        player.sendMessage("§crEconomy §8» §7Spawn set to §f"
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    }


    public Location getSpawn() {
        if (spawn == null) loadSpawn();
        return spawn == null ? null : spawn.clone();
    }


    public boolean isClaimForbidden(String worldName, int chunkX, int chunkZ) {
        if (!plugin.getConfig().getBoolean("claims.protect-spawn", true)) return false;
        Location center = getSpawn();
        if (center == null || center.getWorld() == null) return false;
        if (!center.getWorld().getName().equals(worldName)) return false;

        int radius = Math.max(0, plugin.getConfig().getInt("claims.spawn-radius", 48));
        int sx = center.getBlockX();
        int sz = center.getBlockZ();
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;
        int nearestX = Math.max(minX, Math.min(sx, maxX));
        int nearestZ = Math.max(minZ, Math.min(sz, maxZ));
        long dx = nearestX - sx;
        long dz = nearestZ - sz;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    public void teleport(Player player) {
        if (spawn == null) loadSpawn();
        if (spawn == null) {
            player.sendMessage("§crEconomy §8» §7Spawn is not set yet.");
            return;
        }
        Location dest = spawn.clone();
        if (warmup != null) {
            warmup.begin(player, "spawn", p -> {
                p.teleportAsync(dest);
                p.sendMessage("§crEconomy §8» §7Spawn.");
            });
            return;
        }
        if (cooldowns != null && !cooldowns.tryUse(player, "spawn")) {
            return;
        }
        player.teleportAsync(dest);
        player.sendMessage("§crEconomy §8» §7Spawn.");
    }
}
