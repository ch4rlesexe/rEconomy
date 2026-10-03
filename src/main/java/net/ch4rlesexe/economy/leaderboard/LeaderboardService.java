package net.ch4rlesexe.economy.leaderboard;

import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.economy.EconomyService;
import net.ch4rlesexe.economy.stats.ActivityStatsService;
import net.ch4rlesexe.economy.stats.StatsService;
import org.bukkit.entity.EntityType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LeaderboardService {
    public enum Category {
        MONEY("Money", "Richest players"),
        KILLS("PvP Kills", "Most player kills"),
        DEATHS("Deaths", "Most deaths"),
        KDR("K/D", "Best kill/death ratio (min 5 kills)"),
        STREAK("Streak", "Best killstreak"),
        PLAYTIME("Playtime", "Most time played"),
        MOB_KILLS("Mob Kills", "Total mobs killed"),
        CROPS("Crops", "Crops farmed / harvested"),
        TEAM_MEMBERS("Team Size", "Largest teams"),
        TEAM_CLAIMS("Team Claims", "Most claimed chunks"),
        TEAM_WEALTH("Team Wealth", "Coins + valuables in claim chests");

        private final String title;
        private final String lore;

        Category(String title, String lore) {
            this.title = title;
            this.lore = lore;
        }

        public String title() {
            return title;
        }

        public String lore() {
            return lore;
        }

        public static Category fromArg(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String s = raw.toLowerCase(Locale.ROOT);
            return switch (s) {
                case "money", "bal", "balance", "rich", "coins", "baltop" -> MONEY;
                case "kills", "kill", "killtop", "pk", "pvp" -> KILLS;
                case "deaths", "death", "deathtop" -> DEATHS;
                case "kdr", "kd", "ratio" -> KDR;
                case "streak", "killstreak", "ks" -> STREAK;
                case "playtime", "time", "played", "pt" -> PLAYTIME;
                case "mobs", "mob", "mobkills", "pve" -> MOB_KILLS;
                case "crops", "farm", "farming", "harvest" -> CROPS;
                case "team", "teams", "members", "size", "teammembers" -> TEAM_MEMBERS;
                case "claims", "claim", "land", "teamclaims" -> TEAM_CLAIMS;
                case "wealth", "teamwealth", "teammoney", "teambal" -> TEAM_WEALTH;
                default -> null;
            };
        }
    }

    public record Entry(String name, String detail, double sortValue) {}

    private final REconomyPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final StatsService stats;
    private final ActivityStatsService activity;
    private ClaimWealthService claimWealth;

    public LeaderboardService(REconomyPlugin plugin, Database database,
                              EconomyService economy, StatsService stats,
                              ActivityStatsService activity) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.stats = stats;
        this.activity = activity;
    }

    public void setClaimWealth(ClaimWealthService claimWealth) {
        this.claimWealth = claimWealth;
    }

    public ActivityStatsService activity() {
        return activity;
    }

    public List<Entry> top(Category category, int limit) {
        int lim = Math.max(1, Math.min(limit, 45));
        return switch (category) {
            case MONEY -> money(lim);
            case KILLS -> kills(lim);
            case DEATHS -> deaths(lim);
            case KDR -> kdr(lim);
            case STREAK -> streak(lim);
            case PLAYTIME -> mapNamed(activity.topPlaytime(lim));
            case MOB_KILLS -> mapNamed(activity.topMobKills(lim));
            case CROPS -> mapNamed(activity.topCrops(lim));
            case TEAM_MEMBERS -> teamMembers(lim);
            case TEAM_CLAIMS -> teamClaims(lim);
            case TEAM_WEALTH -> teamWealth(lim);
        };
    }

    public List<Entry> topMob(EntityType type, int limit) {
        return mapNamed(activity.topMobType(type.name(), limit));
    }

    public List<Entry> topCrop(String cropKey, int limit) {
        return mapNamed(activity.topCropType(cropKey, limit));
    }

    private static List<Entry> mapNamed(List<ActivityStatsService.NamedCount> list) {
        List<Entry> out = new ArrayList<>(list.size());
        for (ActivityStatsService.NamedCount n : list) {
            out.add(new Entry(n.name(), n.detail(), n.value()));
        }
        return out;
    }

    private List<Entry> money(int limit) {
        List<Entry> out = new ArrayList<>();
        for (EconomyService.TopEntry e : economy.top(limit)) {
            out.add(new Entry(e.name(), economy.format(e.balance()), e.balance()));
        }
        return out;
    }

    private List<Entry> kills(int limit) {
        List<Entry> out = new ArrayList<>();
        for (StatsService.Stats s : stats.topKills(limit)) {
            out.add(new Entry(s.name(), s.kills() + "K / " + s.deaths() + "D (" + s.kdr() + " KDR)", s.kills()));
        }
        return out;
    }

    private List<Entry> deaths(int limit) {
        List<Entry> out = new ArrayList<>();
        for (StatsService.Stats s : stats.topDeaths(limit)) {
            out.add(new Entry(s.name(), s.deaths() + " deaths / " + s.kills() + " kills", s.deaths()));
        }
        return out;
    }

    private List<Entry> kdr(int limit) {
        List<Entry> out = new ArrayList<>();
        for (StatsService.Stats s : stats.topKdr(limit, 5)) {
            out.add(new Entry(s.name(), s.kdr() + " KDR / " + s.kills() + "K/" + s.deaths() + "D", s.kdr()));
        }
        return out;
    }

    private List<Entry> streak(int limit) {
        List<Entry> out = new ArrayList<>();
        for (StatsService.Stats s : stats.topStreak(limit)) {
            out.add(new Entry(s.name(), "Best " + s.bestKillstreak() + " (now " + s.killstreak() + ")",
                    s.bestKillstreak()));
        }
        return out;
    }

    private List<Entry> teamMembers(int limit) {
        List<Entry> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT t.name, t.tag, COUNT(tm.uuid) AS members
                 FROM teams t
                 JOIN team_members tm ON tm.team_id = t.id
                 GROUP BY t.id, t.name, t.tag
                 ORDER BY members DESC, t.name ASC
                 LIMIT ?
                 """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int members = rs.getInt(3);
                    out.add(new Entry(rs.getString(1) + " [" + rs.getString(2) + "]",
                            members + " members", members));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("top team members: " + e.getMessage());
        }
        return out;
    }

    private List<Entry> teamClaims(int limit) {
        List<Entry> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT t.name, t.tag, COUNT(c.team_id) AS claims
                 FROM teams t
                 LEFT JOIN claims c ON c.team_id = t.id
                 GROUP BY t.id, t.name, t.tag
                 ORDER BY claims DESC, t.name ASC
                 LIMIT ?
                 """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int claims = rs.getInt(3);
                    out.add(new Entry(rs.getString(1) + " [" + rs.getString(2) + "]",
                            claims + " claims", claims));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("top team claims: " + e.getMessage());
        }
        return out;
    }

    private List<Entry> teamWealth(int limit) {
        if (claimWealth != null) {
            List<Entry> out = new ArrayList<>();
            for (ClaimWealthService.TeamWealth w : claimWealth.top(limit)) {
                out.add(new Entry(w.name() + " [" + w.tag() + "]",
                        economy.format(w.total()) + " (cash " + economy.format(w.cash())
                                + " + stored " + economy.format(w.stored()) + ")",
                        w.total()));
            }
            return out;
        }
        List<Entry> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT t.name, t.tag, COALESCE(SUM(b.balance), 0) AS wealth, COUNT(DISTINCT tm.uuid) AS members
                 FROM teams t
                 JOIN team_members tm ON tm.team_id = t.id
                 LEFT JOIN balances b ON b.uuid = tm.uuid
                 GROUP BY t.id, t.name, t.tag
                 ORDER BY wealth DESC, t.name ASC
                 LIMIT ?
                 """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double wealth = rs.getDouble(3);
                    int members = rs.getInt(4);
                    out.add(new Entry(rs.getString(1) + " [" + rs.getString(2) + "]",
                            economy.format(wealth) + " / " + members + " members", wealth));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("top team wealth: " + e.getMessage());
        }
        return out;
    }
}
