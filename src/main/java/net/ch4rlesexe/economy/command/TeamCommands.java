package net.ch4rlesexe.economy.command;

import net.ch4rlesexe.economy.claim.ClaimService;
import net.ch4rlesexe.economy.economy.EconomyService;
import net.ch4rlesexe.economy.team.TeamGui;
import net.ch4rlesexe.economy.team.TeamHomeService;
import net.ch4rlesexe.economy.team.TeamService;
import net.ch4rlesexe.economy.team.TeamUpgradeService;
import net.ch4rlesexe.economy.util.CooldownService;
import net.ch4rlesexe.economy.util.TeleportWarmupService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class TeamCommands implements CommandExecutor {
    private final TeamService teams;
    private final ClaimService claims;
    private final TeamHomeService teamHomes;
    private final CooldownService cooldowns;
    private final TeamUpgradeService upgrades;
    private final EconomyService economy;
    private final TeleportWarmupService warmup;
    private TeamGui gui;

    private final Map<UUID, PendingUnclaimAll> unclaimAllPending = new ConcurrentHashMap<>();

    private record PendingUnclaimAll(int step, long teamId, int count, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public TeamCommands(TeamService teams, ClaimService claims, TeamHomeService teamHomes,
                        CooldownService cooldowns, TeamUpgradeService upgrades, EconomyService economy,
                        TeleportWarmupService warmup) {
        this.teams = teams;
        this.claims = claims;
        this.teamHomes = teamHomes;
        this.cooldowns = cooldowns;
        this.upgrades = upgrades;
        this.economy = economy;
        this.warmup = warmup;
    }

    public void setGui(TeamGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("claim")) {
            int cx = player.getLocation().getChunk().getX();
            int cz = player.getLocation().getChunk().getZ();
            if (claims.isSpawnProtected(player.getWorld().getName(), cx, cz)) {
                player.sendMessage("§crEconomy §8» §7You can't claim land at spawn.");
                return true;
            }
            if (claims.claim(player.getUniqueId(), player.getWorld().getName(), cx, cz)) {
                player.sendMessage("§crEconomy §8» §7Chunk claimed.");
            } else {
                player.sendMessage("§cUnable to claim (need team + admin/owner role, free chunk, under limit).");
            }
            return true;
        }
        if (cmd.equals("unclaim")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
                return handleUnclaimAll(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            }
            if (claims.unclaim(player.getUniqueId(), player.getWorld().getName(),
                    player.getLocation().getChunk().getX(), player.getLocation().getChunk().getZ())) {
                player.sendMessage("§crEconomy §8» §7Chunk unclaimed.");
            } else {
                player.sendMessage("§cUnable to unclaim.");
            }
            return true;
        }
        if (cmd.equals("unclaimall")) {
            return handleUnclaimAll(player, args);
        }
        if (cmd.equals("claims")) {
            Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
            if (team.isEmpty()) {
                player.sendMessage("§cYou are not on a team.");
                return true;
            }
            player.sendMessage("§crEconomy §8» §7Claims: §f" + claims.count(team.get().id())
                    + "§7/§f" + claims.maxClaims(team.get().id()));
            for (ClaimService.Claim c : claims.list(team.get().id())) {
                player.sendMessage("§8- §7" + c.world() + " " + c.x() + "," + c.z());
            }
            return true;
        }
        if (cmd.equals("trust")) {
            if (args.length < 1) {
                player.sendMessage("§cUsage: /trust <player> [build|access|container]");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            Player online = Bukkit.getPlayerExact(args[0]);
            UUID tid = online != null ? online.getUniqueId() : target.getUniqueId();
            String level = args.length >= 2 ? args[1] : "build";
            if (claims.trust(player.getUniqueId(), tid, level)) {
                player.sendMessage("§crEconomy §8» §7Trusted §f" + args[0] + " §7(§f" + level + "§7).");
            } else {
                player.sendMessage("§cCould not trust (need team admin/owner).");
            }
            return true;
        }
        if (cmd.equals("untrust")) {
            if (args.length < 1) {
                player.sendMessage("§cUsage: /untrust <player>");
                return true;
            }
            Player online = Bukkit.getPlayerExact(args[0]);
            UUID tid = online != null ? online.getUniqueId() : Bukkit.getOfflinePlayer(args[0]).getUniqueId();
            if (claims.untrust(player.getUniqueId(), tid)) {
                player.sendMessage("§crEconomy §8» §7Untrusted §f" + args[0]);
            } else {
                player.sendMessage("§cCould not untrust.");
            }
            return true;
        }
        if (cmd.equals("trustlist") || cmd.equals("trusts")) {
            Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
            if (team.isEmpty()) {
                player.sendMessage("§cNot on a team.");
                return true;
            }
            player.sendMessage("§crEconomy §8» §7Trusted players:");
            for (ClaimService.Trust t : claims.trusts(team.get().id())) {
                String name = Bukkit.getOfflinePlayer(t.uuid()).getName();
                player.sendMessage("§8- §f" + (name == null ? t.uuid() : name) + " §7(" + t.level() + ")");
            }
            return true;
        }

        if (args.length == 0) {
            if (gui != null) {
                gui.openMain(player);
            } else {
                TeamGui.sendHelp(player);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
            TeamGui.sendHelp(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
            if (gui != null) gui.openMain(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /team create <name> <tag>");
                    return true;
                }
                if (teams.create(player.getUniqueId(), args[1], args[2])) {
                    player.sendMessage("§crEconomy §8» §7Team §f" + args[1] + " §7created §8(§aOPEN§8).");
                    player.sendMessage("§7Toggle: §f/team close §7or Settings in §f/team");
                } else {
                    player.sendMessage("§cCould not create team.");
                }
            }
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /team join <name>");
                    return true;
                }
                if (teams.join(player.getUniqueId(), args[1])) {
                    player.sendMessage("§crEconomy §8» §7Joined §f" + args[1]);
                } else {
                    player.sendMessage("§cCould not join (closed without invite, full, or already on a team).");
                }
            }
            case "open" -> {
                if (teams.setOpen(player.getUniqueId(), true)) {
                    player.sendMessage("§crEconomy §8» §7Team is now §aOPEN§7 — anyone can join.");
                } else {
                    player.sendMessage("§cNeed owner/admin.");
                }
            }
            case "close" -> {
                if (teams.setOpen(player.getUniqueId(), false)) {
                    player.sendMessage("§crEconomy §8» §7Team is now §cCLOSED§7 — invite only.");
                } else {
                    player.sendMessage("§cNeed owner/admin.");
                }
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /team invite <player>");
                    return true;
                }
                UUID tid = resolvePlayer(args[1]);
                if (tid == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                if (teams.invite(player.getUniqueId(), tid)) {
                    player.sendMessage("§crEconomy §8» §7Invited §f" + args[1]);
                    Player online = Bukkit.getPlayer(tid);
                    if (online != null) {
                        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
                        online.sendMessage("§crEconomy §8» §f" + player.getName() + " §7invited you to §f"
                                + team.map(TeamService.Team::name).orElse("a team"));
                        online.sendMessage("§7Use §f/team join " + team.map(TeamService.Team::name).orElse("<name>"));
                    }
                } else {
                    player.sendMessage("§cCould not invite.");
                }
            }
            case "leave" -> {
                if (teams.leave(player.getUniqueId())) {
                    player.sendMessage("§crEconomy §8» §7Left team.");
                } else {
                    player.sendMessage("§cNot on a team.");
                }
            }
            case "disband" -> {
                Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
                if (team.isEmpty() || !team.get().owner().equals(player.getUniqueId())) {
                    player.sendMessage("§cOnly the owner can disband.");
                    return true;
                }
                teams.disband(team.get().id());
                player.sendMessage("§crEconomy §8» §7Team disbanded.");
            }
            case "info" -> {
                Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
                if (team.isEmpty()) {
                    player.sendMessage("§cNot on a team.");
                    return true;
                }
                String role = teams.getRole(player.getUniqueId()).orElse("member");
                player.sendMessage("§crEconomy §8» §f" + team.get().name() + " §7[" + team.get().tag() + "] §8(§f" + role + "§8)");
                player.sendMessage("§7Join: §f" + (team.get().open() ? "OPEN" : "CLOSED (invite)"));
                player.sendMessage("§7Claims: §f" + claims.count(team.get().id()));
                for (String m : teams.members(team.get().id())) {
                    String[] parts = m.split(":", 2);
                    String name = Bukkit.getOfflinePlayer(UUID.fromString(parts[1])).getName();
                    player.sendMessage("§8- §f" + (name == null ? parts[1] : name) + " §7(" + parts[0] + ")");
                }
            }
            case "promote" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /team promote <player>");
                    return true;
                }
                UUID targetId = resolvePlayer(args[1]);
                if (targetId == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                if (teams.setRole(player.getUniqueId(), targetId, "admin")) {
                    player.sendMessage("§crEconomy §8» §7Promoted §f" + args[1] + " §7to admin.");
                } else {
                    player.sendMessage("§cCould not promote.");
                }
            }
            case "demote" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /team demote <player>");
                    return true;
                }
                UUID targetId = resolvePlayer(args[1]);
                if (targetId == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                if (teams.setRole(player.getUniqueId(), targetId, "member")) {
                    player.sendMessage("§crEconomy §8» §7Demoted §f" + args[1] + " §7to member.");
                } else {
                    player.sendMessage("§cCould not demote.");
                }
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /team kick <player>");
                    return true;
                }
                UUID targetId = resolvePlayer(args[1]);
                if (targetId == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                if (teams.kick(player.getUniqueId(), targetId)) {
                    player.sendMessage("§crEconomy §8» §7Kicked §f" + args[1]);
                    Player online = Bukkit.getPlayer(targetId);
                    if (online != null) {
                        online.sendMessage("§crEconomy §8» §7You were kicked from the team.");
                    }
                } else {
                    player.sendMessage("§cCould not kick.");
                }
            }
            case "enemy", "ally" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /team " + args[0] + " <team>");
                    return true;
                }
                Optional<TeamService.Team> mine = teams.getByPlayer(player.getUniqueId());
                Optional<TeamService.Team> other = teams.getByName(args[1]);
                if (mine.isEmpty() || other.isEmpty()) {
                    player.sendMessage("§cTeam not found.");
                    return true;
                }
                if (!mine.get().owner().equals(player.getUniqueId())) {
                    player.sendMessage("§cOwner only.");
                    return true;
                }
                String rel = args[0].equalsIgnoreCase("enemy") ? "enemy" : "ally";
                teams.setRelation(mine.get().id(), other.get().id(), rel);
                player.sendMessage("§crEconomy §8» §7Set §f" + other.get().name() + " §7as §f" + rel);
            }
            case "sethome" -> {
                String name = args.length >= 2 ? args[1] : "home";
                if (teamHomes.setHome(player, name)) {
                    player.sendMessage("§crEconomy §8» §7Team home §f" + name.toLowerCase() + " §7set. §8(default: all members)");
                    player.sendMessage("§7Manage access: §f/team perms " + name.toLowerCase());
                } else {
                    player.sendMessage("§cCould not set team home (need owner/admin, under limit of "
                            + teamHomes.limit(teams.getByPlayer(player.getUniqueId()).map(TeamService.Team::id).orElse(0L)) + ").");
                }
            }
            case "buyhome" -> {
                Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
                if (team.isEmpty()) {
                    player.sendMessage("§cNot on a team.");
                    return true;
                }
                double cost = upgrades.nextHomeCost(team.get().id());
                if (upgrades.buyHomeSlot(player.getUniqueId())) {
                    player.sendMessage("§crEconomy §8» §7Bought +1 team home slot for §a"
                            + economy.format(cost) + " §8(§7limit now §f"
                            + teamHomes.limit(team.get().id()) + "§8)");
                } else {
                    player.sendMessage("§cCould not buy home slot (need owner/admin, enough coins, or max upgrades).");
                    player.sendMessage("§7Next costs §a" + economy.format(cost));
                }
            }
            case "buyclaims", "buyclaim" -> {
                Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
                if (team.isEmpty()) {
                    player.sendMessage("§cNot on a team.");
                    return true;
                }
                int amount = 5;
                if (args.length >= 2) {
                    try { amount = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
                }
                double cost = upgrades.costForClaims(team.get().id(), amount);
                if (upgrades.buyClaimSlots(player.getUniqueId(), amount)) {
                    player.sendMessage("§crEconomy §8» §7Bought §f+" + amount + " §7claim chunks for §a"
                            + economy.format(cost) + " §8(§7limit now §f"
                            + claims.maxClaims(team.get().id()) + "§8)");
                } else {
                    player.sendMessage("§cCould not buy claims (need owner/admin, enough coins, or max upgrades).");
                    player.sendMessage("§7" + amount + " chunks cost §a" + economy.format(cost)
                            + " §8(§7next chunk §a" + economy.format(upgrades.nextClaimChunkCost(team.get().id())) + "§8)");
                }
            }
            case "home" -> {
                String name = args.length >= 2 ? args[1] : "home";
                Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
                if (team.isEmpty()) {
                    player.sendMessage("§cNot on a team.");
                    return true;
                }
                Location loc = teamHomes.getLocation(team.get().id(), name);
                if (loc == null) {
                    player.sendMessage("§cTeam home not found.");
                    return true;
                }
                if (!teamHomes.canUse(player.getUniqueId(), team.get().id(), name)) {
                    player.sendMessage("§cNo permission for team home §f" + name.toLowerCase());
                    return true;
                }
                Location dest = loc.clone();
                String homeName = name.toLowerCase();
                warmup.begin(player, "team-home", p -> {
                    p.teleportAsync(dest);
                    p.sendMessage("§crEconomy §8» §7Teleported to team home §f" + homeName);
                });
            }
            case "delhome" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /team delhome <name>");
                    return true;
                }
                if (teamHomes.delete(player, args[1])) {
                    player.sendMessage("§crEconomy §8» §7Deleted team home §f" + args[1].toLowerCase());
                } else {
                    player.sendMessage("§cCould not delete (need owner/admin, or home missing).");
                }
            }
            case "homes" -> {
                Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
                if (team.isEmpty()) {
                    player.sendMessage("§cNot on a team.");
                    return true;
                }
                List<TeamHomeService.TeamHome> homes = teamHomes.list(team.get().id());
                if (homes.isEmpty()) {
                    player.sendMessage("§crEconomy §8» §7No team homes.");
                } else {
                    player.sendMessage("§crEconomy §8» §7Team homes §8(§f" + homes.size() + "§7/§f"
                            + teamHomes.limit(team.get().id()) + "§8):");
                    for (TeamHomeService.TeamHome h : homes) {
                        boolean allowed = teamHomes.canUse(player.getUniqueId(), team.get().id(), h.name());
                        player.sendMessage("§8- §f" + h.name() + " §7[" + h.accessMode() + "]"
                                + (allowed ? "" : " §c(locked)"));
                    }
                }
            }
            case "perms" -> handlePerms(player, args);
            default -> player.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }

    private void handlePerms(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c/team perms <home>");
            player.sendMessage("§c/team perms <home> all");
            player.sendMessage("§c/team perms <home> list");
            player.sendMessage("§c/team perms <home> add <player>");
            player.sendMessage("§c/team perms <home> remove <player>");
            player.sendMessage("§c/team perms <home> clear");
            return;
        }
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) {
            player.sendMessage("§cNot on a team.");
            return;
        }
        String homeName = args[1].toLowerCase();
        Optional<String> mode = teamHomes.accessMode(team.get().id(), homeName);
        if (mode.isEmpty()) {
            player.sendMessage("§cTeam home §f" + homeName + " §cnot found.");
            return;
        }

        if (args.length == 2) {
            player.sendMessage("§crEconomy §8» §7Perms for §f" + homeName + " §8(§f" + mode.get() + "§8)");
            if (mode.get().equals("all")) {
                player.sendMessage("§7All team members can use this home.");
            } else {
                List<UUID> allowed = teamHomes.allowedPlayers(team.get().id(), homeName);
                if (allowed.isEmpty()) {
                    player.sendMessage("§7Whitelist empty — only owner/admins can use it.");
                } else {
                    String names = allowed.stream().map(TeamHomeService::nameOf).collect(Collectors.joining("§7, §f"));
                    player.sendMessage("§7Allowed: §f" + names);
                }
            }
            player.sendMessage("§7Owner/admins always have access.");
            return;
        }

        String action = args[2].toLowerCase();
        switch (action) {
            case "all" -> {
                if (teamHomes.setAccessMode(player, homeName, "all")) {
                    player.sendMessage("§crEconomy §8» §7§f" + homeName + " §7is now open to §fall §7members.");
                } else {
                    player.sendMessage("§cNeed owner/admin.");
                }
            }
            case "list" -> {
                if (teamHomes.setAccessMode(player, homeName, "list")) {
                    player.sendMessage("§crEconomy §8» §7§f" + homeName + " §7is now whitelist-only.");
                    player.sendMessage("§7Add players: §f/team perms " + homeName + " add <player>");
                } else {
                    player.sendMessage("§cNeed owner/admin.");
                }
            }
            case "add" -> {
                if (args.length < 4) {
                    player.sendMessage("§cUsage: /team perms <home> add <player>");
                    return;
                }
                UUID tid = resolvePlayer(args[3]);
                if (tid == null) {
                    player.sendMessage("§cPlayer not found.");
                    return;
                }
                if (teamHomes.addPerm(player, homeName, tid)) {
                    player.sendMessage("§crEconomy §8» §7Allowed §f" + args[3] + " §7on §f" + homeName);
                } else {
                    player.sendMessage("§cCould not add (need owner/admin; player must be on your team).");
                }
            }
            case "remove" -> {
                if (args.length < 4) {
                    player.sendMessage("§cUsage: /team perms <home> remove <player>");
                    return;
                }
                UUID tid = resolvePlayer(args[3]);
                if (tid == null) {
                    player.sendMessage("§cPlayer not found.");
                    return;
                }
                if (teamHomes.removePerm(player, homeName, tid)) {
                    player.sendMessage("§crEconomy §8» §7Removed §f" + args[3] + " §7from §f" + homeName);
                } else {
                    player.sendMessage("§cCould not remove.");
                }
            }
            case "clear" -> {
                if (teamHomes.clearPerms(player, homeName)) {
                    player.sendMessage("§crEconomy §8» §7Cleared whitelist for §f" + homeName);
                } else {
                    player.sendMessage("§cNeed owner/admin.");
                }
            }
            default -> player.sendMessage("§cUnknown perms action. Use all|list|add|remove|clear");
        }
    }


    private boolean handleUnclaimAll(Player player, String[] args) {
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) {
            unclaimAllPending.remove(player.getUniqueId());
            player.sendMessage("§cYou are not on a team.");
            return true;
        }
        String role = teams.getRole(player.getUniqueId()).orElse("member");
        if (!TeamService.canManageClaims(role)) {
            unclaimAllPending.remove(player.getUniqueId());
            player.sendMessage("§cNeed team owner/admin to unclaim all.");
            return true;
        }
        long teamId = team.get().id();
        int count = claims.count(teamId);
        if (count <= 0) {
            unclaimAllPending.remove(player.getUniqueId());
            player.sendMessage("§crEconomy §8» §7Your team has no claims.");
            return true;
        }

        String a0 = args.length >= 1 ? args[0].toLowerCase() : "";
        String a1 = args.length >= 2 ? args[1].toLowerCase() : "";
        UUID id = player.getUniqueId();
        PendingUnclaimAll pending = unclaimAllPending.get(id);
        if (pending != null && (pending.expired() || pending.teamId() != teamId)) {
            unclaimAllPending.remove(id);
            pending = null;
        }


        if (a0.equals("confirm") && (a1.equals("yes") || a1.equals("confirm"))) {
            if (pending == null || pending.step() < 2) {
                unclaimAllPending.remove(id);
                player.sendMessage("§crEconomy §8» §7Start over: §f/unclaimall");
                return true;
            }
            unclaimAllPending.remove(id);
            int removed = claims.unclaimAll(id);
            if (removed < 0) {
                player.sendMessage("§cCould not unclaim all.");
            } else {
                player.sendMessage("§crEconomy §8» §cUnclaimed §f" + removed + " §cchunk(s). Land is unprotected.");
            }
            return true;
        }


        if (a0.equals("confirm")) {
            if (pending == null || pending.step() < 1) {

                unclaimAllPending.put(id, new PendingUnclaimAll(1, teamId, count,
                        System.currentTimeMillis() + 30_000L));
                player.sendMessage("§crEconomy §8» §eConfirmation §f1/3§e.");
                player.sendMessage("§7This will remove §cALL §f" + count + " §7claim(s) for §f" + team.get().name() + "§7.");
                player.sendMessage("§eRun §f/unclaimall confirm §eagain within §f30s§e.");
                return true;
            }
            unclaimAllPending.put(id, new PendingUnclaimAll(2, teamId, count,
                    System.currentTimeMillis() + 30_000L));
            player.sendMessage("§crEconomy §8» §eConfirmation §f2/3§e.");
            player.sendMessage("§c§lWARNING: §7Every claimed chunk becomes wilderness.");
            player.sendMessage("§7Bought claim slots are §fkept§7 — only land is cleared.");
            player.sendMessage("§eFinal step: §f/unclaimall confirm yes §ewithin §f30s§e.");
            return true;
        }


        unclaimAllPending.put(id, new PendingUnclaimAll(1, teamId, count,
                System.currentTimeMillis() + 30_000L));
        player.sendMessage("§crEconomy §8» §eUnclaim ALL — confirmation §f1/3§e.");
        player.sendMessage("§7Your team §f" + team.get().name() + " §7has §f" + count + " §7claim(s).");
        player.sendMessage("§cThis cannot be undone.");
        player.sendMessage("§eNext: §f/unclaimall confirm §ewithin §f30s§e.");
        return true;
    }

    private UUID resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        if (off.hasPlayedBefore() || off.isOnline()) return off.getUniqueId();
        return null;
    }
}
