package net.ch4rlesexe.economy.rtp;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpService {
    private final REconomyPlugin plugin;
    private final EconomyService economy;

    public RtpService(REconomyPlugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void execute(Player player, int maxRange, double cost) {
        World world = resolveWorld(player);
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            player.sendMessage("§crEconomy §8» §7RTP only works in the overworld.");
            return;
        }
        if (cost > 0 && !economy.has(player.getUniqueId(), cost)) {
            player.sendMessage("§crEconomy §8» §7Need " + economy.format(cost) + " for that RTP.");
            return;
        }

        int minRange = Math.max(100, (int) (maxRange * 0.35));
        player.sendMessage("§crEconomy §8» §7Searching within §f" + String.format("%,d", maxRange) + " §7blocks...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location dest = findSafe(world, minRange, maxRange);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (dest == null) {
                    player.sendMessage("§crEconomy §8» §7Could not find a safe spot. Try again.");
                    return;
                }
                if (cost > 0 && !economy.withdraw(player.getUniqueId(), cost, "rtp")) {
                    player.sendMessage("§crEconomy §8» §7Need " + economy.format(cost) + " for that RTP.");
                    return;
                }
                player.teleportAsync(dest).thenAccept(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        player.sendMessage("§crEconomy §8» §7Teleported"
                                + (cost > 0 ? " §8(§7" + economy.format(cost) + "§8)" : "")
                                + " §8» §f" + dest.getBlockX() + " " + dest.getBlockY() + " " + dest.getBlockZ());
                    } else {
                        if (cost > 0) economy.deposit(player.getUniqueId(), cost, "rtp-refund");
                        player.sendMessage("§crEconomy §8» §7Teleport failed — refunded.");
                    }
                });
            });
        });
    }

    private World resolveWorld(Player player) {
        List<String> worlds = plugin.getConfig().getStringList("rtp.worlds");
        if (worlds != null && !worlds.isEmpty()) {
            World configured = Bukkit.getWorld(worlds.get(0));
            if (configured != null) return configured;
        }
        return player.getWorld();
    }

    private Location findSafe(World world, int min, int max) {
        int attempts = Math.max(1, plugin.getConfig().getInt("rtp.max-attempts", 32));
        Random rng = ThreadLocalRandom.current();
        Location center = world.getWorldBorder().getCenter();
        double border = world.getWorldBorder().getSize() / 2.0 - 16;
        int effectiveMax = (int) Math.min(max, Math.max(min + 1, border));
        if (effectiveMax <= min) effectiveMax = min + 1;

        for (int i = 0; i < attempts; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            int dist = min + rng.nextInt(Math.max(1, effectiveMax - min + 1));
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            if (Math.abs(x - center.getX()) > border || Math.abs(z - center.getZ()) > border) continue;
            Location surface = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES).getLocation();
            Block ground = surface.getBlock();
            Block feet = ground.getRelative(0, 1, 0);
            Block head = ground.getRelative(0, 2, 0);
            if (!isSafeGround(ground.getType())) continue;
            if (!feet.getType().isAir() || !head.getType().isAir()) continue;
            Location dest = feet.getLocation().add(0.5, 0, 0.5);
            dest.setYaw(rng.nextFloat() * 360f);
            dest.setPitch(0f);
            return dest;
        }
        return null;
    }

    private static boolean isSafeGround(Material type) {
        if (type.isAir() || !type.isSolid()) return false;
        if (type == Material.LAVA || type == Material.WATER || type == Material.MAGMA_BLOCK
                || type == Material.CACTUS || type == Material.SWEET_BERRY_BUSH
                || type == Material.FIRE || type == Material.SOUL_FIRE) return false;
        if (Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) return false;
        String name = type.name();
        return !name.contains("LEAVES") && !name.equals("POWDER_SNOW");
    }
}
