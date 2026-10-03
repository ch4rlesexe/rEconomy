package net.ch4rlesexe.economy.imagemap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ImageMapService implements Listener {
    private final REconomyPlugin plugin;
    private final Database database;
    private final File storeFile;
    private final File tilesDir;
    private final Map<Integer, StoredMap> stored = new ConcurrentHashMap<>();

    public record StoredMap(String url, int tiles, int tileX, int tileY) {}

    public ImageMapService(REconomyPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        File root = new File(plugin.getDataFolder(), "image-maps");
        this.storeFile = new File(root, "index.yml");
        this.tilesDir = new File(root, "tiles");
        if (!tilesDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tilesDir.mkdirs();
        }
    }

    public int maxTiles() {
        return Math.max(1, Math.min(4, plugin.getConfig().getInt("image-maps.max-tiles", 4)));
    }

    public int clampTiles(int tiles) {
        return Math.max(1, Math.min(maxTiles(), tiles));
    }

    public boolean dither() {
        return plugin.getConfig().getBoolean("image-maps.dither", true);
    }

    public double cost() {
        return plugin.getConfig().getDouble("image-maps.cost",
                plugin.getConfig().getDouble("paid-commands.createmap", 10000));
    }

    public int confirmSeconds() {
        return Math.max(5, plugin.getConfig().getInt("image-maps.confirm-seconds",
                plugin.getConfig().getInt("paid-commands.confirm-seconds", 15)));
    }

    public void load() {
        loadFromYaml();
        loadFromMysql();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int restored = 0;
            for (Map.Entry<Integer, StoredMap> e : stored.entrySet()) {
                try {
                    if (attach(e.getKey(), e.getValue())) {
                        restored++;
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to restore image map #" + e.getKey() + ": " + ex.getMessage());
                }
            }
            plugin.getLogger().info("Restored " + restored + "/" + stored.size() + " custom image map(s).");
        });
    }

    private void loadFromYaml() {

        File legacy = new File(plugin.getDataFolder(), "image-maps.yml");
        File file = storeFile.exists() ? storeFile : legacy;
        if (!file.exists()) return;
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("maps");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String url = section.getString(key + ".url");
                int tiles = section.getInt(key + ".tiles", 1);
                int tx = section.getInt(key + ".tile-x", 0);
                int ty = section.getInt(key + ".tile-y", 0);
                if (url == null || url.isBlank()) continue;
                stored.putIfAbsent(id, new StoredMap(url, tiles, tx, ty));
            } catch (Exception ignored) {
            }
        }
    }

    private void loadFromMysql() {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT map_id, url, tiles, tile_x, tile_y, tile_png FROM image_maps");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("map_id");
                StoredMap meta = new StoredMap(
                        rs.getString("url"),
                        rs.getInt("tiles"),
                        rs.getInt("tile_x"),
                        rs.getInt("tile_y"));
                stored.put(id, meta);
                byte[] png = rs.getBytes("tile_png");
                if (png != null && png.length > 0) {
                    writeTileFile(id, png);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load image maps from MySQL: " + e.getMessage());
        }
    }

    @EventHandler
    public void onMapInitialize(MapInitializeEvent event) {
        MapView view = event.getMap();
        StoredMap meta = stored.get(view.getId());
        if (meta == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                attach(view.getId(), meta);
            } catch (Exception ex) {
                plugin.getLogger().warning("MapInitialize restore #" + view.getId() + ": " + ex.getMessage());
            }
        });
    }

    private void saveYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<Integer, StoredMap> e : stored.entrySet()) {
            String p = "maps." + e.getKey();
            yaml.set(p + ".url", e.getValue().url());
            yaml.set(p + ".tiles", e.getValue().tiles());
            yaml.set(p + ".tile-x", e.getValue().tileX());
            yaml.set(p + ".tile-y", e.getValue().tileY());
        }
        try {
            if (!storeFile.getParentFile().exists()) {
                //noinspection ResultOfMethodCallIgnored
                storeFile.getParentFile().mkdirs();
            }
            yaml.save(storeFile);

            yaml.save(new File(plugin.getDataFolder(), "image-maps.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save image-maps index: " + e.getMessage());
        }
    }

    private File tileFile(int mapId) {
        return new File(tilesDir, mapId + ".png");
    }

    private void writeTileFile(int mapId, byte[] png) throws Exception {
        if (!tilesDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tilesDir.mkdirs();
        }
        Files.write(tileFile(mapId).toPath(), png);
    }

    private void writeTileFile(int mapId, BufferedImage image) throws Exception {
        writeTileFile(mapId, toPngBytes(image));
    }

    private static byte[] toPngBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return bos.toByteArray();
    }

    private BufferedImage loadCachedTile(int mapId) {
        File f = tileFile(mapId);
        if (f.isFile()) {
            try {
                return ImageIO.read(f);
            } catch (Exception ignored) {
            }
        }

        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT tile_png FROM image_maps WHERE map_id=?")) {
            ps.setInt(1, mapId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] png = rs.getBytes(1);
                    if (png != null && png.length > 0) {
                        writeTileFile(mapId, png);
                        return ImageIO.read(new ByteArrayInputStream(png));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void upsertMysql(int mapId, StoredMap meta, byte[] png) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO image_maps (map_id, url, tiles, tile_x, tile_y, tile_png)
                     VALUES (?,?,?,?,?,?)
                     ON DUPLICATE KEY UPDATE url=VALUES(url), tiles=VALUES(tiles),
                       tile_x=VALUES(tile_x), tile_y=VALUES(tile_y), tile_png=VALUES(tile_png)
                     """)) {
            ps.setInt(1, mapId);
            ps.setString(2, meta.url());
            ps.setInt(3, meta.tiles());
            ps.setInt(4, meta.tileX());
            ps.setInt(5, meta.tileY());
            ps.setBytes(6, png);
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not persist image map #" + mapId + " to MySQL: " + e.getMessage());
        }
    }


    private boolean attach(int mapId, StoredMap meta) throws Exception {
        BufferedImage tile = loadCachedTile(mapId);
        if (tile == null) {

            BufferedImage source = QualityMapRenderer.load(meta.url());
            if (source == null) return false;
            BufferedImage[][] tiles = QualityMapRenderer.prepareTiles(source, meta.tiles(), dither());
            tile = tiles[meta.tileY()][meta.tileX()];
            byte[] png = toPngBytes(tile);
            writeTileFile(mapId, png);
            upsertMysql(mapId, meta, png);
        }
        final BufferedImage finalTile = tile;
        Bukkit.getScheduler().runTask(plugin, () -> {
            MapView view = Bukkit.getMap(mapId);
            if (view == null) return;
            view.getRenderers().forEach(view::removeRenderer);
            view.addRenderer(new QualityMapRenderer(finalTile));
            view.setTrackingPosition(false);
        });
        return true;
    }


    public List<ItemStack> create(Player player, String url, int tiles) throws Exception {
        tiles = clampTiles(tiles);
        BufferedImage source = QualityMapRenderer.load(url);
        if (source == null) throw new IllegalStateException("Could not decode image");
        BufferedImage[][] prepared = QualityMapRenderer.prepareTiles(source, tiles, dither());
        List<ItemStack> items = new ArrayList<>();
        for (int ty = 0; ty < tiles; ty++) {
            for (int tx = 0; tx < tiles; tx++) {
                MapView view = Bukkit.createMap(player.getWorld());
                view.getRenderers().forEach(view::removeRenderer);
                BufferedImage tileImg = prepared[ty][tx];
                view.addRenderer(new QualityMapRenderer(tileImg));
                view.setTrackingPosition(false);

                ItemStack map = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) map.getItemMeta();
                meta.setMapView(view);
                String title = tiles == 1 ? "Custom Image Map" : "Image Map (" + (tx + 1) + "," + (ty + 1) + ")";
                meta.displayName(Component.text(title, NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text("High quality · " + tiles + "×" + tiles + " tiles", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Place in item frames to assemble", NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
                map.setItemMeta(meta);
                items.add(map);

                StoredMap storedMeta = new StoredMap(url, tiles, tx, ty);
                stored.put(view.getId(), storedMeta);
                byte[] png = toPngBytes(tileImg);
                writeTileFile(view.getId(), png);
                upsertMysql(view.getId(), storedMeta, png);
            }
        }
        saveYaml();
        return items;
    }

    public static String normalizeUrl(String[] args) {
        if (args == null || args.length == 0 || args[0].isBlank()) {
            return null;
        }
        String url = String.join(" ", args).trim();
        String lower = url.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return null;
        }
        return url;
    }
}
