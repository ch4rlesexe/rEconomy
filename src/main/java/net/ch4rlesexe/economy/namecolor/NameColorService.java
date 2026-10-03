package net.ch4rlesexe.economy.namecolor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NameColorService {
    public record ColorDef(String id, String name, double price, String type, String value, Material icon) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, ColorDef> catalog = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> ownedCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeCache = new ConcurrentHashMap<>();

    public NameColorService(REconomyPlugin plugin, Database database, EconomyService economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        reloadCatalog();
    }

    public void reloadCatalog() {
        catalog.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("name-colors.colors");
        if (section == null) {
            putBuiltinDefaults();
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(id);
            if (c == null) continue;
            String matName = c.getString("material", "WHITE_DYE");
            Material mat;
            try {
                mat = Material.valueOf(matName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                mat = Material.WHITE_DYE;
            }
            catalog.put(id.toLowerCase(Locale.ROOT), new ColorDef(
                    id.toLowerCase(Locale.ROOT),
                    c.getString("name", id),
                    Math.max(0, c.getDouble("price", 0)),
                    c.getString("type", "solid").toLowerCase(Locale.ROOT),
                    c.getString("value", "#FFFFFF"),
                    mat
            ));
        }
        if (catalog.isEmpty()) putBuiltinDefaults();
        if (!catalog.containsKey("default")) {
            catalog.put("default", new ColorDef("default", "Default", 0, "solid", "#FFFFFF", Material.PAPER));
        }
    }

    private void putBuiltinDefaults() {
        catalog.put("default", new ColorDef("default", "Default", 0, "solid", "#FFFFFF", Material.PAPER));
        catalog.put("red", new ColorDef("red", "Red", 1500, "solid", "#FF5555", Material.RED_DYE));
        catalog.put("blue", new ColorDef("blue", "Blue", 1500, "solid", "#55FFFF", Material.LIGHT_BLUE_DYE));
        catalog.put("green", new ColorDef("green", "Green", 1500, "solid", "#55FF55", Material.LIME_DYE));
        catalog.put("gold", new ColorDef("gold", "Gold", 2500, "solid", "#FFAA00", Material.ORANGE_DYE));
        catalog.put("purple", new ColorDef("purple", "Purple", 2500, "solid", "#AA00AA", Material.PURPLE_DYE));
        catalog.put("pink", new ColorDef("pink", "Pink", 2500, "solid", "#FF55FF", Material.PINK_DYE));
        catalog.put("aqua", new ColorDef("aqua", "Aqua", 2500, "solid", "#00AAAA", Material.CYAN_DYE));
        catalog.put("crimson", new ColorDef("crimson", "Crimson", 5000, "solid", "#FF2D55", Material.REDSTONE));
        catalog.put("neon", new ColorDef("neon", "Neon", 5000, "solid", "#39FF14", Material.SLIME_BALL));
        catalog.put("electric", new ColorDef("electric", "Electric", 5000, "solid", "#00D4FF", Material.DIAMOND));
        catalog.put("rose", new ColorDef("rose", "Rose Gold", 7500, "solid", "#B76E79", Material.COPPER_INGOT));
        catalog.put("ice", new ColorDef("ice", "Ice", 7500, "solid", "#A8E6FF", Material.PACKED_ICE));
        catalog.put("ember", new ColorDef("ember", "Ember", 7500, "solid", "#FF4500", Material.BLAZE_POWDER));
        catalog.put("sunset", new ColorDef("sunset", "Sunset", 10000, "gradient", "#FF6B35;#F7C948", Material.MAGMA_CREAM));
        catalog.put("ocean", new ColorDef("ocean", "Ocean", 10000, "gradient", "#00C9FF;#92FE9D", Material.PRISMARINE_SHARD));
        catalog.put("aurora", new ColorDef("aurora", "Aurora", 15000, "gradient", "#00FF87;#60EFFF;#B967FF", Material.NETHER_STAR));
        catalog.put("shadow", new ColorDef("shadow", "Shadow", 15000, "gradient", "#232526;#414345", Material.COAL));
        catalog.put("candy", new ColorDef("candy", "Candy", 12500, "gradient", "#FF9A9E;#FECFEF;#FECFEF", Material.CHERRY_SAPLING));
    }

    public List<ColorDef> colors() {
        return Collections.unmodifiableList(new ArrayList<>(catalog.values()));
    }

    public Optional<ColorDef> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(catalog.get(id.toLowerCase(Locale.ROOT)));
    }

    public boolean owns(UUID uuid, String colorId) {
        ColorDef def = catalog.get(colorId.toLowerCase(Locale.ROOT));
        if (def != null && def.price() <= 0) return true;
        return ownedCache.computeIfAbsent(uuid, this::loadOwned).contains(colorId.toLowerCase(Locale.ROOT));
    }

    public String activeId(UUID uuid) {
        return activeCache.computeIfAbsent(uuid, this::loadActive);
    }

    public boolean buy(Player player, String colorId) {
        ColorDef def = catalog.get(colorId.toLowerCase(Locale.ROOT));
        if (def == null) return false;
        if (owns(player.getUniqueId(), def.id())) return false;
        if (def.price() > 0 && !economy.withdraw(player.getUniqueId(), def.price(), "name-color-" + def.id())) {
            return false;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO name_colors_owned (uuid, color_id) VALUES (?,?)")) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, def.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (def.price() > 0) {
                economy.deposit(player.getUniqueId(), def.price(), "name-color-refund");
            }
            plugin.getLogger().warning("name color buy: " + e.getMessage());
            return false;
        }
        ownedCache.computeIfAbsent(player.getUniqueId(), u -> ConcurrentHashMap.newKeySet()).add(def.id());
        return true;
    }

    public boolean equip(Player player, String colorId) {
        ColorDef def = catalog.get(colorId.toLowerCase(Locale.ROOT));
        if (def == null) return false;
        if (!owns(player.getUniqueId(), def.id())) return false;
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO name_colors_active (uuid, color_id) VALUES (?,?)
                 ON DUPLICATE KEY UPDATE color_id=VALUES(color_id)
                 """)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, def.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("name color equip: " + e.getMessage());
            return false;
        }
        activeCache.put(player.getUniqueId(), def.id());
        apply(player);
        return true;
    }

    public void apply(Player player) {
        ColorDef def = get(activeId(player.getUniqueId())).orElse(catalog.get("default"));
        Component name = render(player.getName(), def);
        player.displayName(name);
        player.customName(name);

        if (tabList != null) {
            tabList.refresh(player);
        } else {
            player.playerListName(name);
        }
    }

    private net.ch4rlesexe.economy.tab.TabListService tabList;

    public void setTabList(net.ch4rlesexe.economy.tab.TabListService tabList) {
        this.tabList = tabList;
    }

    public void applyLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) apply(player);
        });
    }

    public Component render(String username, ColorDef def) {
        if (def == null) {
            return Component.text(username, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
        }
        if ("gradient".equals(def.type())) {
            String[] parts = def.value().split(";");
            StringBuilder tags = new StringBuilder("<gradient");
            for (String p : parts) {
                String hex = normalizeHex(p.trim());
                if (hex != null) tags.append(':').append(hex);
            }
            tags.append('>').append(escape(username)).append("</gradient>");
            return mini.deserialize(tags.toString()).decoration(TextDecoration.ITALIC, false);
        }
        TextColor color = parseSolid(def.value());
        return Component.text(username, color == null ? NamedTextColor.WHITE : color)
                .decoration(TextDecoration.ITALIC, false);
    }

    public Component preview(ColorDef def) {
        return render(def.name(), def);
    }

    private static String escape(String s) {
        return s.replace("<", "").replace(">", "").replace(":", "");
    }

    private static TextColor parseSolid(String value) {
        String hex = normalizeHex(value);
        return hex == null ? null : TextColor.fromHexString(hex);
    }

    private static String normalizeHex(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        if (!v.startsWith("#")) v = "#" + v;
        if (v.length() != 7) return null;
        try {
            TextColor.fromHexString(v);
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private Set<String> loadOwned(UUID uuid) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        out.add("default");
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT color_id FROM name_colors_owned WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1).toLowerCase(Locale.ROOT));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("load owned colors: " + e.getMessage());
        }

        for (ColorDef def : catalog.values()) {
            if (def.price() <= 0) out.add(def.id());
        }
        return out;
    }

    private String loadActive(UUID uuid) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT color_id FROM name_colors_active WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString(1).toLowerCase(Locale.ROOT);
                    if (catalog.containsKey(id)) return id;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("load active color: " + e.getMessage());
        }
        return "default";
    }

    public void invalidate(UUID uuid) {
        ownedCache.remove(uuid);
        activeCache.remove(uuid);
    }
}
