package net.ch4rlesexe.economy.livingshop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public final class ShopCatalog {
    private final File shopsDir;
    private final Logger log;

    public ShopCatalog(File ultimateShopDataFolder, Logger log) {
        this.shopsDir = new File(ultimateShopDataFolder, "shops");
        this.log = log;
    }

    public List<ShopProduct> loadAll() {
        List<ShopProduct> out = new ArrayList<>();
        if (!shopsDir.isDirectory()) {
            log.warning("[LivingShop] UltimateShop shops folder missing: " + shopsDir);
            return out;
        }
        File[] files = shopsDir.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        if (files == null) return out;
        for (File file : files) {
            String shopId = stripExt(file.getName());
            if ("example".equalsIgnoreCase(shopId) || "daily".equalsIgnoreCase(shopId)) continue;
            out.addAll(loadShop(file, shopId));
        }
        out.sort((a, b) -> {
            int c = a.shop.compareToIgnoreCase(b.shop);
            return c != 0 ? c : a.product.compareToIgnoreCase(b.product);
        });
        return out;
    }

    public List<ShopProduct> loadShop(File file, String shopId) {
        List<ShopProduct> out = new ArrayList<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items == null) return out;
        for (String productId : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(productId);
            if (item == null) continue;
            String material = firstMaterial(item);
            if (material == null || material.isBlank()) continue;
            Double buy = firstVaultAmount(item.getConfigurationSection("buy-prices"));
            Double sell = firstVaultAmount(item.getConfigurationSection("sell-prices"));
            out.add(new ShopProduct(
                    shopId,
                    productId,
                    material.toUpperCase(Locale.ROOT),
                    materialId(material),
                    buy != null ? buy : 0,
                    sell != null ? sell : 0,
                    buy != null,
                    sell != null
            ));
        }
        return out;
    }

    public boolean updatePrices(String shopId, String productId, Double buy, Double sell) {
        File file = new File(shopsDir, shopId + ".yml");
        if (!file.isFile()) {
            file = new File(shopsDir, shopId + ".yaml");
        }
        if (!file.isFile()) {
            log.warning("[LivingShop] shop file not found for " + shopId);
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection item = yaml.getConfigurationSection("items." + productId);
        if (item == null) {
            log.warning("[LivingShop] product missing: " + shopId + "/" + productId);
            return false;
        }
        if (buy != null) {
            setFirstVaultAmount(item.getConfigurationSection("buy-prices"), buy);
        }
        if (sell != null) {
            setFirstVaultAmount(item.getConfigurationSection("sell-prices"), sell);
        }
        try {
            yaml.save(file);
            return true;
        } catch (Exception e) {
            log.severe("[LivingShop] failed saving " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private static String firstMaterial(ConfigurationSection item) {
        ConfigurationSection products = item.getConfigurationSection("products");
        if (products == null) return null;
        for (String key : products.getKeys(false)) {
            ConfigurationSection p = products.getConfigurationSection(key);
            if (p == null) continue;
            String mat = p.getString("material");
            if (mat != null && !mat.isBlank()) return mat;
        }
        return null;
    }

    private static Double firstVaultAmount(ConfigurationSection prices) {
        if (prices == null) return null;
        for (String key : prices.getKeys(false)) {
            ConfigurationSection p = prices.getConfigurationSection(key);
            if (p == null) continue;
            Object raw = p.get("amount");
            if (raw == null) continue;
            Double parsed = ManualEconomyCsv.parseMoney(String.valueOf(raw));
            if (parsed != null) return parsed;
        }
        return null;
    }

    private static void setFirstVaultAmount(ConfigurationSection prices, double amount) {
        if (prices == null) return;
        for (String key : prices.getKeys(false)) {
            ConfigurationSection p = prices.getConfigurationSection(key);
            if (p == null) continue;
            p.set("amount", ManualEconomyCsv.formatPriceAmount(amount));
            return;
        }
    }


    public static String materialId(String material) {
        if (material == null) return "";
        String m = material.trim();

        int colon = m.indexOf(':');
        if (colon >= 0) m = m.substring(colon + 1);
        return m.toLowerCase(Locale.ROOT);
    }

    public static String prettyMaterial(String material) {
        String id = materialId(material);
        if (id.isEmpty()) return "";
        String name = id.replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String part : name.split(" ")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
