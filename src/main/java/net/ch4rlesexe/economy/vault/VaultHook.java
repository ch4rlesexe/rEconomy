package net.ch4rlesexe.economy.vault;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.economy.EconomyService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class VaultHook {
    private VaultHook() {}

    public static boolean register(REconomyPlugin plugin, EconomyService economy) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null
                && Bukkit.getPluginManager().getPlugin("VaultUnlocked") == null) {
            plugin.getLogger().warning("Vault not found — shop/AH need it.");
            return false;
        }
        Economy provider = new Provider(plugin, economy);
        Bukkit.getServicesManager().register(Economy.class, provider, plugin, ServicePriority.Highest);


        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.getServicesManager().register(Economy.class, provider, plugin, ServicePriority.Highest);
            var rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            String name = rsp == null ? "none" : rsp.getProvider().getName() + " via " + rsp.getPlugin().getName();
            plugin.getLogger().info("Vault economy active: " + name);
            if (Bukkit.getPluginManager().isPluginEnabled("UltimateShop")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "shop reload");
                plugin.getLogger().info("Reloaded UltimateShop so it re-hooks Vault.");
            }
        }, 60L);
        plugin.getLogger().info("Registered Vault economy provider (rEconomy Coins).");
        return true;
    }

    private static final class Provider implements Economy {
        private final REconomyPlugin plugin;
        private final EconomyService economy;

        private Provider(REconomyPlugin plugin, EconomyService economy) {
            this.plugin = plugin;
            this.economy = economy;
        }

        @Override
        public boolean isEnabled() {
            return plugin.isEnabled();
        }

        @Override
        public String getName() {
            return "rEconomy";
        }

        @Override
        public boolean hasBankSupport() {
            return false;
        }

        @Override
        public int fractionalDigits() {
            return 2;
        }

        @Override
        public String format(double amount) {
            return economy.format(amount);
        }

        @Override
        public String currencyNamePlural() {
            return plugin.getConfig().getString("currency-name", "Coins");
        }

        @Override
        public String currencyNameSingular() {
            return plugin.getConfig().getString("currency-name", "Coins");
        }

        @Override
        public boolean hasAccount(String playerName) {
            return true;
        }

        @Override
        public boolean hasAccount(OfflinePlayer player) {
            return true;
        }

        @Override
        public boolean hasAccount(String playerName, String worldName) {
            return true;
        }

        @Override
        public boolean hasAccount(OfflinePlayer player, String worldName) {
            return true;
        }

        @Override
        public double getBalance(String playerName) {
            OfflinePlayer off = resolve(playerName);
            return off == null ? 0 : getBalance(off);
        }

        @Override
        public double getBalance(OfflinePlayer player) {
            economy.ensureAccount(player.getUniqueId(), player.getName());
            return economy.getBalance(player.getUniqueId());
        }

        @Override
        public double getBalance(String playerName, String world) {
            return getBalance(playerName);
        }

        @Override
        public double getBalance(OfflinePlayer player, String world) {
            return getBalance(player);
        }

        @Override
        public boolean has(String playerName, double amount) {
            return getBalance(playerName) + 1e-9 >= amount;
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return economy.has(player.getUniqueId(), amount);
        }

        @Override
        public boolean has(String playerName, String worldName, double amount) {
            return has(playerName, amount);
        }

        @Override
        public boolean has(OfflinePlayer player, String worldName, double amount) {
            return has(player, amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, double amount) {
            OfflinePlayer off = resolve(playerName);
            if (off == null) {
                return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Unknown player");
            }
            return withdrawPlayer(off, amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            UUID id = player.getUniqueId();
            economy.ensureAccount(id, player.getName());
            if (amount < 0) {
                return new EconomyResponse(0, economy.getBalance(id), EconomyResponse.ResponseType.FAILURE, "Negative");
            }
            if (amount == 0) {
                return new EconomyResponse(0, economy.getBalance(id), EconomyResponse.ResponseType.SUCCESS, null);
            }
            if (!economy.withdraw(id, amount, "vault-withdraw")) {
                plugin.getLogger().log(Level.WARNING, "Vault withdraw FAILED name={0} uuid={1} amount={2} bal={3}",
                        new Object[]{player.getName(), id, amount, economy.getBalance(id)});
                return new EconomyResponse(0, economy.getBalance(id), EconomyResponse.ResponseType.FAILURE, "Insufficient");
            }
            plugin.getLogger().info("Vault withdraw OK " + player.getName() + " -" + amount
                    + " -> " + economy.getBalance(id));
            return new EconomyResponse(amount, economy.getBalance(id), EconomyResponse.ResponseType.SUCCESS, null);
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
            return withdrawPlayer(playerName, amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
            return withdrawPlayer(player, amount);
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, double amount) {
            OfflinePlayer off = resolve(playerName);
            if (off == null) {
                return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Unknown player");
            }
            return depositPlayer(off, amount);
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            UUID id = player.getUniqueId();
            if (amount < 0) {
                return new EconomyResponse(0, economy.getBalance(id), EconomyResponse.ResponseType.FAILURE, "Negative");
            }
            economy.ensureAccount(id, player.getName());
            if (amount == 0) {
                return new EconomyResponse(0, economy.getBalance(id), EconomyResponse.ResponseType.SUCCESS, null);
            }
            if (!economy.deposit(id, amount, "vault-deposit")) {
                return new EconomyResponse(0, economy.getBalance(id), EconomyResponse.ResponseType.FAILURE, "Failed");
            }
            plugin.getLogger().info("Vault deposit OK " + player.getName() + " +" + amount
                    + " -> " + economy.getBalance(id));
            return new EconomyResponse(amount, economy.getBalance(id), EconomyResponse.ResponseType.SUCCESS, null);
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
            return depositPlayer(playerName, amount);
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
            return depositPlayer(player, amount);
        }

        @Override
        public EconomyResponse createBank(String name, String player) {
            return notSupported();
        }

        @Override
        public EconomyResponse createBank(String name, OfflinePlayer player) {
            return notSupported();
        }

        @Override
        public EconomyResponse deleteBank(String name) {
            return notSupported();
        }

        @Override
        public EconomyResponse bankBalance(String name) {
            return notSupported();
        }

        @Override
        public EconomyResponse bankHas(String name, double amount) {
            return notSupported();
        }

        @Override
        public EconomyResponse bankWithdraw(String name, double amount) {
            return notSupported();
        }

        @Override
        public EconomyResponse bankDeposit(String name, double amount) {
            return notSupported();
        }

        @Override
        public EconomyResponse isBankOwner(String name, String playerName) {
            return notSupported();
        }

        @Override
        public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
            return notSupported();
        }

        @Override
        public EconomyResponse isBankMember(String name, String playerName) {
            return notSupported();
        }

        @Override
        public EconomyResponse isBankMember(String name, OfflinePlayer player) {
            return notSupported();
        }

        @Override
        public List<String> getBanks() {
            return Collections.emptyList();
        }

        @Override
        public boolean createPlayerAccount(String playerName) {
            OfflinePlayer off = resolve(playerName);
            if (off == null) return false;
            return createPlayerAccount(off);
        }

        @Override
        public boolean createPlayerAccount(OfflinePlayer player) {
            economy.ensureAccount(player.getUniqueId(), player.getName());
            return true;
        }

        @Override
        public boolean createPlayerAccount(String playerName, String worldName) {
            return createPlayerAccount(playerName);
        }

        @Override
        public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
            return createPlayerAccount(player);
        }

        private OfflinePlayer resolve(String playerName) {
            Player online = Bukkit.getPlayerExact(playerName);
            if (online != null) return online;


            @SuppressWarnings("deprecation")
            OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
            return off;
        }

        private EconomyResponse notSupported() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks");
        }
    }
}
