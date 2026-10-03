package net.ch4rlesexe.economy.coinflip;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class CoinflipService {
    public record Flip(long id, UUID creator, double amount, String side, String status,
                       UUID joiner, UUID winner, String resultSide) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private GovernmentService government;

    private final Set<Long> settling = ConcurrentHashMap.newKeySet();
    private final Set<Long> announced = ConcurrentHashMap.newKeySet();

    public CoinflipService(REconomyPlugin plugin, Database database, EconomyService economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
    }

    public void setGovernment(GovernmentService government) {
        this.government = government;
    }

    public double minAmount() {
        return plugin.getConfig().getDouble("coinflip.min-amount", 10);
    }

    public double taxPercent() {
        return Math.max(0, Math.min(25, plugin.getConfig().getDouble("coinflip.tax-percent", 0)));
    }

    public boolean create(Player creator, double amount, String side) {
        side = normalizeSide(side);
        if (side == null) {
            creator.sendMessage("§cPick HEADS or TAILS.");
            return false;
        }
        if (amount < minAmount()) {
            creator.sendMessage("§cMinimum coinflip is " + economy.format(minAmount()));
            return false;
        }
        if (hasOpen(creator.getUniqueId())) {
            creator.sendMessage("§cYou already have an open coinflip. Cancel it first.");
            return false;
        }
        if (!economy.withdraw(creator.getUniqueId(), amount, "coinflip-escrow")) {
            creator.sendMessage("§cInsufficient funds.");
            return false;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO coinflips (creator, amount, side, status) VALUES (?,?,?,'open')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, creator.getUniqueId().toString());
            ps.setDouble(2, amount);
            ps.setString(3, side);
            ps.executeUpdate();
            long id = 0;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) id = keys.getLong(1);
            }
            creator.sendMessage("§crEconomy §8» §7Coinflip §f#" + id + " §7created: §f"
                    + economy.format(amount) + " §7on §f" + side);
            creator.sendMessage("§7Others can join via §f/coinflip");
            Bukkit.broadcastMessage("§crEconomy §8» §f" + creator.getName() + " §7opened a §f"
                    + economy.format(amount) + " §7coinflip (§f" + side + "§7). §8/coinflip");
            return true;
        } catch (SQLException e) {
            economy.deposit(creator.getUniqueId(), amount, "coinflip-refund");
            plugin.getLogger().severe("coinflip create: " + e.getMessage());
            return false;
        }
    }

    public record ResolvedFlip(long id, UUID creator, UUID joiner, UUID winner, String creatorSide,
                               String resultSide, double amount, double payout) {}


    public Optional<ResolvedFlip> join(Player joiner, long id) {
        if (!settling.add(id)) {
            joiner.sendMessage("§cThat coinflip is already resolving.");
            return Optional.empty();
        }
        try {
            Optional<Flip> opt = get(id);
            if (opt.isEmpty() || !"open".equals(opt.get().status())) {
                joiner.sendMessage("§cThat coinflip is gone.");
                return Optional.empty();
            }
            Flip flip = opt.get();
            if (flip.creator().equals(joiner.getUniqueId())) {
                joiner.sendMessage("§cYou can't join your own coinflip.");
                return Optional.empty();
            }
            if (!economy.withdraw(joiner.getUniqueId(), flip.amount(), "coinflip-escrow")) {
                joiner.sendMessage("§cNeed " + economy.format(flip.amount()) + " to join.");
                return Optional.empty();
            }

            boolean heads = ThreadLocalRandom.current().nextBoolean();
            String result = heads ? "HEADS" : "TAILS";
            UUID winner = result.equals(flip.side()) ? flip.creator() : joiner.getUniqueId();
            double pot = flip.amount() * 2;
            double tax = pot * (taxPercent() / 100.0);
            double payout = Math.floor((pot - tax) * 100.0) / 100.0;

            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement("""
                     UPDATE coinflips SET status='completed', joiner=?, winner=?, result_side=?
                     WHERE id=? AND status='open'
                     """)) {
                ps.setString(1, joiner.getUniqueId().toString());
                ps.setString(2, winner.toString());
                ps.setString(3, result);
                ps.setLong(4, id);
                if (ps.executeUpdate() == 0) {
                    economy.deposit(joiner.getUniqueId(), flip.amount(), "coinflip-refund");
                    joiner.sendMessage("§cSomeone else joined first.");
                    return Optional.empty();
                }
            } catch (SQLException e) {
                economy.deposit(joiner.getUniqueId(), flip.amount(), "coinflip-refund");
                return Optional.empty();
            }

            economy.deposit(winner, payout, "coinflip-win");
            if (tax > 1e-9 && government != null) {
                government.collect(tax, "coinflip-tax");
            }
            return Optional.of(new ResolvedFlip(id, flip.creator(), joiner.getUniqueId(), winner,
                    flip.side(), result, flip.amount(), payout));
        } finally {
            settling.remove(id);
        }
    }


    public void announce(ResolvedFlip resolved) {
        if (!announced.add(resolved.id())) return;
        String winName = nameOf(resolved.winner());
        Bukkit.broadcastMessage("§crEconomy §8» §7Coinflip §f#" + resolved.id()
                + " §7→ §f" + resolved.resultSide()
                + "§7! §a" + winName + " §7won §f" + economy.format(resolved.payout()));
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        var off = Bukkit.getOfflinePlayer(uuid);
        return off.getName() != null ? off.getName() : "Unknown";
    }

    public boolean cancel(Player player) {
        Optional<Flip> open = findOpenByCreator(player.getUniqueId());
        if (open.isEmpty()) {
            player.sendMessage("§cNo open coinflip to cancel.");
            return false;
        }
        Flip flip = open.get();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE coinflips SET status='cancelled' WHERE id=? AND status='open'")) {
            ps.setLong(1, flip.id());
            if (ps.executeUpdate() == 0) {
                player.sendMessage("§cAlready taken.");
                return false;
            }
        } catch (SQLException e) {
            return false;
        }
        economy.deposit(player.getUniqueId(), flip.amount(), "coinflip-cancel");
        player.sendMessage("§crEconomy §8» §7Cancelled coinflip. Refunded §f" + economy.format(flip.amount()));
        return true;
    }

    public List<Flip> listOpen(int limit) {
        List<Flip> list = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, creator, amount, side, status, joiner, winner, result_side "
                             + "FROM coinflips WHERE status='open' ORDER BY amount DESC, id DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, Math.min(limit, 45)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("coinflip list: " + e.getMessage());
        }
        return list;
    }

    public Optional<Flip> get(long id) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, creator, amount, side, status, joiner, winner, result_side FROM coinflips WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public boolean hasOpen(UUID uuid) {
        return findOpenByCreator(uuid).isPresent();
    }

    private Optional<Flip> findOpenByCreator(UUID creator) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, creator, amount, side, status, joiner, winner, result_side "
                             + "FROM coinflips WHERE creator=? AND status='open' LIMIT 1")) {
            ps.setString(1, creator.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private static String normalizeSide(String side) {
        if (side == null) return null;
        String s = side.trim().toUpperCase();
        if (s.equals("H") || s.equals("HEAD") || s.equals("HEADS")) return "HEADS";
        if (s.equals("T") || s.equals("TAIL") || s.equals("TAILS")) return "TAILS";
        return null;
    }

    private static Flip map(ResultSet rs) throws SQLException {
        String joiner = rs.getString(6);
        String winner = rs.getString(7);
        return new Flip(
                rs.getLong(1),
                UUID.fromString(rs.getString(2)),
                rs.getDouble(3),
                rs.getString(4),
                rs.getString(5),
                joiner == null ? null : UUID.fromString(joiner),
                winner == null ? null : UUID.fromString(winner),
                rs.getString(8)
        );
    }
}
