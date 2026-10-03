package net.ch4rlesexe.economy.wager;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.economy.EconomyService;
import net.ch4rlesexe.economy.government.GovernmentService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WagerService {
    public record Wager(long id, UUID challenger, UUID target, double amount, String status) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private GovernmentService government;
    private final Map<UUID, Long> activeByPlayer = new ConcurrentHashMap<>();

    public WagerService(REconomyPlugin plugin, Database database, EconomyService economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
    }

    public void setGovernment(GovernmentService government) {
        this.government = government;
    }

    public double minAmount() {
        return plugin.getConfig().getDouble("wager.min-amount", 10);
    }

    public double taxPercent() {
        return Math.max(0, Math.min(25, plugin.getConfig().getDouble("wager.tax-percent", 2)));
    }

    public Optional<Wager> getActive(UUID uuid) {
        Long id = activeByPlayer.get(uuid);
        if (id == null) return Optional.empty();
        return get(id);
    }

    public Optional<Wager> pendingFor(UUID target) {
        return findPendingFor(target);
    }

    public boolean challenge(Player from, Player to, double amount) {
        double min = plugin.getConfig().getDouble("wager.min-amount", 10);
        if (amount < min) {
            from.sendMessage("§cMinimum wager is " + economy.format(min));
            return false;
        }
        if (activeByPlayer.containsKey(from.getUniqueId()) || activeByPlayer.containsKey(to.getUniqueId())) {
            from.sendMessage("§cOne of you already has an active wager.");
            return false;
        }
        if (!economy.withdraw(from.getUniqueId(), amount, "wager-escrow")) {
            from.sendMessage("§cInsufficient funds.");
            return false;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO wagers (challenger, target, amount, status) VALUES (?,?,?,'pending')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, from.getUniqueId().toString());
            ps.setString(2, to.getUniqueId().toString());
            ps.setDouble(3, amount);
            ps.executeUpdate();
            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
            activeByPlayer.put(from.getUniqueId(), id);
            activeByPlayer.put(to.getUniqueId(), id);
            to.sendMessage("§crEconomy §8» §f" + from.getName() + " §7wagered §f" + economy.format(amount));
            to.sendMessage("§7/wager accept §8| §7/wager deny");
            from.sendMessage("§crEconomy §8» §7Wager sent.");
            return true;
        } catch (SQLException e) {
            economy.deposit(from.getUniqueId(), amount, "wager-refund");
            return false;
        }
    }

    public boolean accept(Player target) {
        Optional<Wager> wager = findPendingFor(target.getUniqueId());
        if (wager.isEmpty()) {
            target.sendMessage("§cNo pending wager.");
            return false;
        }
        Wager w = wager.get();
        if (!economy.withdraw(target.getUniqueId(), w.amount(), "wager-escrow")) {
            target.sendMessage("§cInsufficient funds to match.");
            return false;
        }
        setStatus(w.id(), "active");
        Player challenger = Bukkit.getPlayer(w.challenger());
        target.sendMessage("§crEconomy §8» §7Wager active. Winner: §f/wager win §7or staff §f/wager resolve");
        if (challenger != null) challenger.sendMessage("§crEconomy §8» §7Wager accepted by §f" + target.getName());
        return true;
    }

    public boolean deny(Player target) {
        Optional<Wager> wager = findPendingFor(target.getUniqueId());
        if (wager.isEmpty()) return false;
        Wager w = wager.get();
        economy.deposit(w.challenger(), w.amount(), "wager-refund");
        setStatus(w.id(), "denied");
        clearActive(w);
        target.sendMessage("§crEconomy §8» §7Wager denied.");
        return true;
    }

    public boolean declareWinner(Player actor, UUID winnerUuid, boolean admin) {
        Long id = activeByPlayer.get(actor.getUniqueId());
        if (id == null && admin) {

            id = activeByPlayer.get(winnerUuid);
        }
        if (id == null) {
            actor.sendMessage("§cNo active wager.");
            return false;
        }
        Optional<Wager> wager = get(id);
        if (wager.isEmpty() || !wager.get().status().equals("active")) {
            actor.sendMessage("§cWager not active.");
            return false;
        }
        Wager w = wager.get();
        if (!admin && !w.challenger().equals(actor.getUniqueId()) && !w.target().equals(actor.getUniqueId())) {
            return false;
        }
        if (!winnerUuid.equals(w.challenger()) && !winnerUuid.equals(w.target())) {
            actor.sendMessage("§cWinner must be a wager participant.");
            return false;
        }
        double pot = w.amount() * 2;
        double tax = pot * (taxPercent() / 100.0);
        double payout = Math.floor((pot - tax) * 100.0) / 100.0;
        economy.deposit(winnerUuid, payout, "wager-win");
        if (tax > 1e-9 && government != null) {
            government.collect(tax, "wager-tax");
        }
        setWinner(w.id(), winnerUuid);
        clearActive(w);
        Player winner = Bukkit.getPlayer(winnerUuid);
        if (winner != null) winner.sendMessage("§crEconomy §8» §7You won §f" + economy.format(payout)
                + (tax > 1e-9 ? " §8(§7tax §f" + economy.format(tax) + "§8)" : ""));
        return true;
    }

    public boolean cancel(Player admin) {

        Long id = activeByPlayer.get(admin.getUniqueId());
        if (id == null) return false;
        Optional<Wager> wager = get(id);
        if (wager.isEmpty()) return false;
        Wager w = wager.get();
        if (w.status().equals("pending")) {
            economy.deposit(w.challenger(), w.amount(), "wager-cancel");
        } else if (w.status().equals("active")) {
            economy.deposit(w.challenger(), w.amount(), "wager-cancel");
            economy.deposit(w.target(), w.amount(), "wager-cancel");
        }
        setStatus(w.id(), "cancelled");
        clearActive(w);
        return true;
    }

    private void clearActive(Wager w) {
        activeByPlayer.remove(w.challenger());
        activeByPlayer.remove(w.target());
    }

    private Optional<Wager> findPendingFor(UUID target) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, challenger, target, amount, status FROM wagers WHERE target=? AND status='pending' ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private Optional<Wager> get(long id) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, challenger, target, amount, status FROM wagers WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private Wager map(ResultSet rs) throws SQLException {
        return new Wager(rs.getLong(1), UUID.fromString(rs.getString(2)), UUID.fromString(rs.getString(3)),
                rs.getDouble(4), rs.getString(5));
    }

    private void setStatus(long id, String status) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE wagers SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void setWinner(long id, UUID winner) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE wagers SET status='completed', winner=? WHERE id=?")) {
            ps.setString(1, winner.toString());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }
}
