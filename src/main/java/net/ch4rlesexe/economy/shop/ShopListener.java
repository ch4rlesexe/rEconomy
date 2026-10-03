package net.ch4rlesexe.economy.shop;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

public final class ShopListener implements Listener {
    private final ShopService shops;

    public ShopListener(ShopService shops) {
        this.shops = shops;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        String line0 = event.getLine(0) == null ? "" : event.getLine(0).trim();
        if (!line0.equalsIgnoreCase("[Shop]") && !line0.equalsIgnoreCase("[shop]")) return;
        Player player = event.getPlayer();
        String modeLine = event.getLine(1) == null ? "" : event.getLine(1).trim();
        char mode;
        if (modeLine.equalsIgnoreCase("B") || modeLine.equalsIgnoreCase("Buy")) mode = 'B';
        else if (modeLine.equalsIgnoreCase("S") || modeLine.equalsIgnoreCase("Sell")) mode = 'S';
        else {
            player.sendMessage("§cLine 2 must be B or S");
            event.setCancelled(true);
            return;
        }
        double price;
        int amount;
        try {
            price = Double.parseDouble(event.getLine(2) == null ? "" : event.getLine(2).trim());
            amount = Integer.parseInt(event.getLine(3) == null ? "1" : event.getLine(3).trim().split(" ")[0]);
        } catch (Exception e) {
            player.sendMessage("§cLine 3 = price, line 4 = amount");
            event.setCancelled(true);
            return;
        }
        Material mat = player.getInventory().getItemInMainHand().getType();
        if (mat.isAir()) {
            player.sendMessage("§cHold the item to sell/buy in your hand when creating the shop.");
            event.setCancelled(true);
            return;
        }
        if (shops.findChest(event.getBlock()) == null) {
            player.sendMessage("§cPlace the sign on/adjacent to a chest.");
            event.setCancelled(true);
            return;
        }
        if (!shops.create(player, event.getBlock(), mode, price, amount, mat)) {
            player.sendMessage("§cFailed to create shop.");
            event.setCancelled(true);
            return;
        }
        event.setLine(0, "§a[Shop]");
        event.setLine(1, mode == 'B' ? "§eBuy" : "§bSell");
        event.setLine(2, "§f$" + String.format("%.2f", price));
        event.setLine(3, "§7" + amount + " " + mat.name());
        player.sendMessage("§crEconomy §8» §7Shop created for §f" + mat.name());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) return;
        Optional<ShopService.Shop> shop = shops.getAt(block);
        if (shop.isEmpty()) return;
        event.setCancelled(true);
        shops.trade(event.getPlayer(), shop.get(), block);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Sign)) return;
        Optional<ShopService.Shop> shop = shops.getAt(event.getBlock());
        if (shop.isEmpty()) return;
        if (!shop.get().owner().equals(event.getPlayer().getUniqueId())
                && !event.getPlayer().hasPermission("reconomy.economy.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNot your shop.");
            return;
        }
        shops.delete(event.getBlock());
        event.getPlayer().sendMessage("§crEconomy §8» §7Shop removed.");
    }
}
