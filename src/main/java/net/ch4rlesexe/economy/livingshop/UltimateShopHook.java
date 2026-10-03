package net.ch4rlesexe.economy.livingshop;

import net.ch4rlesexe.economy.REconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

public final class UltimateShopHook {
    private UltimateShopHook() {}

    public static void register(REconomyPlugin plugin, ShopEconomyService economy) {
        if (Bukkit.getPluginManager().getPlugin("UltimateShop") == null) {
            plugin.getLogger().warning("[LivingShop] UltimateShop not found — CSV apply/export still work, live tracking disabled.");
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> preClass = (Class<? extends Event>) Class.forName(
                    "cn.superiormc.ultimateshop.api.ItemPreTransactionEvent");
            @SuppressWarnings("unchecked")
            Class<? extends Event> finishClass = (Class<? extends Event>) Class.forName(
                    "cn.superiormc.ultimateshop.api.ItemFinishTransactionEvent");

            Listener listener = new Listener() {};
            EventExecutor preExec = (l, event) -> handlePre(economy, event);
            EventExecutor finishExec = (l, event) -> handleFinish(economy, event);

            Bukkit.getPluginManager().registerEvent(
                    preClass, listener, EventPriority.HIGH, preExec, plugin, true);
            Bukkit.getPluginManager().registerEvent(
                    finishClass, listener, EventPriority.MONITOR, finishExec, plugin, true);
            plugin.getLogger().info("[LivingShop] Hooked UltimateShop transaction events.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[LivingShop] UltimateShop API missing: " + e.getMessage());
        }
    }

    private static void handlePre(ShopEconomyService economy, Event event) {
        if (!economy.isEnabled()) return;
        try {
            boolean buy = (boolean) event.getClass().getMethod("isBuyOrSell").invoke(event);
            if (!buy) return;
            Object item = event.getClass().getMethod("getItem").invoke(event);
            int amount = (int) event.getClass().getMethod("getAmount").invoke(event);
            String shop = (String) item.getClass().getMethod("getShop").invoke(item);
            String product = (String) item.getClass().getMethod("getProduct").invoke(item);
            if (!economy.canBuy(shop, product, amount)) {
                event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                if (player != null) {
                    var st = economy.get(shop, product);
                    int left = st.map(ShopEconomyService.ItemState::stockRemaining).orElse(0);
                    player.sendMessage("§crEconomy §8» §7Shop stock depleted for this item. §8(§f" + left + "§8 left)");
                }
            }
        } catch (ReflectiveOperationException e) {

        }
    }

    private static void handleFinish(ShopEconomyService economy, Event event) {
        if (!economy.isEnabled()) return;
        try {
            boolean buy = (boolean) event.getClass().getMethod("isBuyOrSell").invoke(event);
            Object item = event.getClass().getMethod("getItem").invoke(event);
            int amount = (int) event.getClass().getMethod("getAmount").invoke(event);
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            String shop = (String) item.getClass().getMethod("getShop").invoke(item);
            String product = (String) item.getClass().getMethod("getProduct").invoke(item);
            economy.onTransaction(shop, product, buy, amount, player);
        } catch (ReflectiveOperationException e) {

        }
    }
}
