package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.economy.EconomyService;
import net.ch4rlesexe.economy.imagemap.ImageMapService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CreateMapCommand implements CommandExecutor, TabCompleter {
    private final net.ch4rlesexe.economy.REconomyPlugin plugin;
    private final ImageMapService maps;
    private final EconomyService economy;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private record Pending(String url, int tiles, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public CreateMapCommand(net.ch4rlesexe.economy.REconomyPlugin plugin,
                            ImageMapService maps, EconomyService economy) {
        this.plugin = plugin;
        this.maps = maps;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Parsed parsed = parseArgs(args);
        if (parsed == null) {
            player.sendMessage("§crEconomy §8» §7Usage: §f/createmap <url> <size>");
            player.sendMessage("§crEconomy §8» §7Size is required: §f1§7–§f" + maps.maxTiles()
                    + " §7or §f1x1§7…§f" + maps.maxTiles() + "x" + maps.maxTiles()
                    + " §7(or §f128§7/§f256§7/§f384§7/§f512§7 px)");
            return true;
        }

        String url = parsed.url();
        int tiles = parsed.tiles();
        double cost = maps.cost();
        boolean free = player.hasPermission("reconomy.economy.admin")
                || player.hasPermission("reconomy.paid.bypass");
        UUID id = player.getUniqueId();

        if (!free) {
            if (economy.getBalance(id) + 1e-9 < cost) {
                pending.remove(id);
                player.sendMessage("§crEconomy §8» §7Need §f" + economy.format(cost)
                        + "§7. Balance: §f" + economy.format(economy.getBalance(id)));
                return true;
            }
            Pending p = pending.get(id);
            if (p == null || p.expired() || !p.url().equalsIgnoreCase(url) || p.tiles() != tiles) {
                pending.put(id, new Pending(url, tiles,
                        System.currentTimeMillis() + maps.confirmSeconds() * 1000L));
                player.sendMessage("§crEconomy §8» §7Custom map costs §f" + economy.format(cost) + "§7.");
                player.sendMessage("§crEconomy §8» §7Size: §f" + tiles + "×" + tiles
                        + " §7tiles (§f" + (tiles * 128) + "×" + (tiles * 128) + " §7px, §f"
                        + (tiles * tiles) + " §7maps) + dithering.");
                player.sendMessage("§crEconomy §8» §eRun §f/createmap "
                        + rebuildArgs(url, tiles)
                        + " §eagain within §f" + maps.confirmSeconds() + "s §eto confirm.");
                return true;
            }
            pending.remove(id);
            if (!economy.withdraw(id, cost, "createmap")) {
                player.sendMessage("§cCould not charge " + economy.format(cost) + ".");
                return true;
            }
            player.sendMessage("§crEconomy §8» §7Paid §f" + economy.format(cost)
                    + "§7. Creating §f" + tiles + "×" + tiles + " §7map…");
        }

        final String finalUrl = url;
        final int finalTiles = tiles;
        final boolean charged = !free;
        final double chargedAmount = cost;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<ItemStack> items = maps.create(player, finalUrl, finalTiles);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    for (ItemStack item : items) {
                        var leftover = player.getInventory().addItem(item);
                        leftover.values().forEach(stack ->
                                player.getWorld().dropItemNaturally(player.getLocation(), stack));
                    }
                    player.sendMessage("§crEconomy §8» §aMap created! §7Got §f" + items.size()
                            + " §7map item(s). Place them in item frames to assemble.");
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (charged && player.isOnline()) {
                        economy.deposit(player.getUniqueId(), chargedAmount, "createmap-refund");
                        player.sendMessage("§crEconomy §8» §cMap failed — refunded §f"
                                + economy.format(chargedAmount) + "§c.");
                    } else if (player.isOnline()) {
                        player.sendMessage("§crEconomy §8» §cCould not load image: §7" + e.getMessage());
                    }
                    plugin.getLogger().warning("createmap failed for " + player.getName() + ": " + e.getMessage());
                });
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("https://");
            for (int i = 1; i <= maps.maxTiles(); i++) {
                out.add(i + "x" + i);
            }
        } else if (args.length >= 2) {
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (int i = 1; i <= maps.maxTiles(); i++) {
                String opt = i + "x" + i;
                if (opt.startsWith(prefix) || String.valueOf(i).startsWith(prefix)) {
                    out.add(opt);
                }
            }
        }
        return out;
    }

    private record Parsed(String url, int tiles) {}

    private Parsed parseArgs(String[] args) {
        if (args == null || args.length < 2) {
            return null;
        }

        Integer sizeFromFirst = parseSizeToken(args[0]);
        Integer sizeFromLast = parseSizeToken(args[args.length - 1]);

        String url;
        Integer tiles;
        if (sizeFromFirst != null && looksLikeUrl(args[1])) {

            tiles = sizeFromFirst;
            url = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();

            Integer trailing = parseSizeToken(args[args.length - 1]);
            if (trailing != null && args.length > 2) {
                url = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length - 1)).trim();
            }
        } else if (sizeFromLast != null && looksLikeUrl(args[0])) {

            tiles = sizeFromLast;
            url = String.join(" ", java.util.Arrays.copyOfRange(args, 0, args.length - 1)).trim();
        } else {
            return null;
        }

        if (!looksLikeUrl(url) || tiles == null) return null;
        if (tiles < 1 || tiles > maps.maxTiles()) {
            return null;
        }
        return new Parsed(url, tiles);
    }

    private static boolean looksLikeUrl(String s) {
        if (s == null || s.isBlank()) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }


    private Integer parseSizeToken(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.matches("\\d+x\\d+")) {
            String[] parts = s.split("x");
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a != b) return null;
            if (a >= 1 && a <= maps.maxTiles()) return a;
            if (a % 128 == 0) {
                int t = a / 128;
                if (t >= 1 && t <= maps.maxTiles()) return t;
            }
            return null;
        }
        if (s.matches("\\d+")) {
            int n = Integer.parseInt(s);
            if (n >= 1 && n <= maps.maxTiles()) return n;
            if (n % 128 == 0) {
                int t = n / 128;
                if (t >= 1 && t <= maps.maxTiles()) return t;
            }
        }
        return null;
    }

    private static String rebuildArgs(String url, int tiles) {
        return url + " " + tiles + "x" + tiles;
    }
}
