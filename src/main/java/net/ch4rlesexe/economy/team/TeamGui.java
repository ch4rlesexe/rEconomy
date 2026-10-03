package net.ch4rlesexe.economy.team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.ch4rlesexe.economy.claim.ClaimService;
import net.ch4rlesexe.economy.economy.EconomyService;
import net.ch4rlesexe.economy.util.CooldownService;
import net.ch4rlesexe.economy.util.TeleportWarmupService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TeamGui implements Listener {
    public static final String TITLE_MAIN = "rEconomy Teams";
    public static final String TITLE_MEMBERS = "Team Members";
    public static final String TITLE_HOMES = "Team Homes";
    public static final String TITLE_JOIN = "Join a Team";
    public static final String TITLE_SETTINGS = "Team Settings";
    public static final String TITLE_INVITE = "Invite Players";
    public static final String TITLE_CLAIMS = "Team Claims";
    public static final String TITLE_UNCLAIM_ALL = "Unclaim ALL · Confirm";

    private final TeamService teams;
    private final ClaimService claims;
    private final TeamHomeService teamHomes;
    private final CooldownService cooldowns;
    private final TeamUpgradeService upgrades;
    private final EconomyService economy;
    private final TeleportWarmupService warmup;
    private TeamChatPrompt prompts;

    public TeamGui(TeamService teams, ClaimService claims, TeamHomeService teamHomes,
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

    public void setPrompts(TeamChatPrompt prompts) {
        this.prompts = prompts;
    }

    public void openMain(Player player) {
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) openNoTeam(player);
        else openTeamHub(player, team.get());
    }

    private void openNoTeam(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_MAIN, NamedTextColor.RED, TextDecoration.BOLD));
        inv.setItem(11, button(Material.WRITABLE_BOOK, "Create a Team", NamedTextColor.GREEN,
                List.of("Opens create flow", "You'll type name + tag in chat")));
        inv.setItem(13, button(Material.COMPASS, "Browse Open Teams", NamedTextColor.AQUA,
                List.of("Join teams that are Open", "Click a team to join")));
        inv.setItem(15, button(Material.NAME_TAG, "Join by Name", NamedTextColor.YELLOW,
                List.of("Type a team name in chat", "Works for invites too")));
        List<TeamService.Team> invites = teams.listInvites(player.getUniqueId());
        if (!invites.isEmpty()) {
            inv.setItem(4, button(Material.PAPER, "Pending Invites (" + invites.size() + ")", NamedTextColor.GOLD,
                    List.of("You have invites!", "Use Browse / Join by Name")));
        }
        inv.setItem(22, button(Material.BARRIER, "Close", NamedTextColor.GRAY, List.of()));
        player.openInventory(inv);
    }

    private void openTeamHub(Player player, TeamService.Team team) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_MAIN, NamedTextColor.RED, TextDecoration.BOLD));
        String role = teams.getRole(player.getUniqueId()).orElse("member");
        int claimCount = claims.count(team.id());
        int homeCount = teamHomes.list(team.id()).size();
        int memberCount = teams.members(team.id()).size();

        inv.setItem(4, button(Material.RED_BANNER, team.name() + " [" + team.tag() + "]", NamedTextColor.RED,
                List.of("Your role: " + role,
                        "Join mode: " + (team.open() ? "OPEN" : "CLOSED (invite)"),
                        "Members: " + memberCount,
                        "Claims: " + claimCount + "/" + claims.maxClaims(team.id()),
                        "Homes: " + homeCount + "/" + teamHomes.limit(team.id()))));

        inv.setItem(19, button(Material.PLAYER_HEAD, "Members", NamedTextColor.YELLOW,
                List.of("View roster", "Promote / demote / kick")));
        inv.setItem(21, button(Material.RED_BED, "Team Homes", NamedTextColor.GOLD,
                List.of("Teleport to team homes", "Set home at your location")));
        inv.setItem(23, button(Material.GOLDEN_SHOVEL, "Claims", NamedTextColor.GREEN,
                List.of("View claimed chunks", "Claim/unclaim nearby")));
        inv.setItem(25, button(Material.COMPARATOR, "Settings", NamedTextColor.AQUA,
                List.of("Open / Closed join mode", "Invite players")));

        inv.setItem(38, button(Material.OAK_DOOR, "Leave Team", NamedTextColor.GRAY,
                List.of(team.owner().equals(player.getUniqueId())
                        ? "Owner leaving disbands the team!"
                        : "Leave this team")));
        if (team.owner().equals(player.getUniqueId())) {
            inv.setItem(42, button(Material.TNT, "Disband Team", NamedTextColor.DARK_RED,
                    List.of("Permanently delete the team")));
        }
        inv.setItem(49, button(Material.BARRIER, "Close", NamedTextColor.GRAY, List.of()));
        player.openInventory(inv);
    }

    public void openJoinBrowser(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_JOIN, NamedTextColor.AQUA, TextDecoration.BOLD));
        int slot = 0;

        for (TeamService.Team t : teams.listInvites(player.getUniqueId())) {
            if (slot >= 45) break;
            inv.setItem(slot++, teamIcon(t, true));
        }
        for (TeamService.Team t : teams.listOpen()) {
            if (slot >= 45) break;

            inv.setItem(slot++, teamIcon(t, false));
        }
        if (slot == 0) {
            inv.setItem(22, button(Material.BARRIER, "No open teams", NamedTextColor.GRAY,
                    List.of("Try Join by Name", "or wait for an invite")));
        }
        inv.setItem(45, button(Material.NAME_TAG, "Join by Name", NamedTextColor.YELLOW,
                List.of("Type a team name in chat")));
        inv.setItem(49, button(Material.ARROW, "Back", NamedTextColor.GRAY, List.of()));
        player.openInventory(inv);
    }

    private ItemStack teamIcon(TeamService.Team t, boolean invited) {
        ItemStack item = new ItemStack(invited ? Material.GOLDEN_APPLE : Material.WHITE_BANNER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(t.name() + " [" + t.tag() + "]", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("tid:" + t.id(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text(t.open() ? "Open" : "Invite only", NamedTextColor.GRAY));
        if (invited) lore.add(Component.text("You are invited!", NamedTextColor.GOLD));
        lore.add(Component.text("Click to join", NamedTextColor.GREEN));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void openSettings(Player player) {
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) {
            openMain(player);
            return;
        }
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_SETTINGS, NamedTextColor.AQUA, TextDecoration.BOLD));
        boolean open = team.get().open();
        inv.setItem(11, button(open ? Material.LIME_DYE : Material.GRAY_DYE,
                open ? "Join: OPEN" : "Join: CLOSED",
                open ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of(open ? "Anyone can /team join" : "Invite only",
                        "Click to toggle")));
        inv.setItem(15, button(Material.WRITABLE_BOOK, "Invite Player", NamedTextColor.YELLOW,
                List.of("Type a player name in chat", "They can then join")));
        inv.setItem(13, button(Material.PLAYER_HEAD, "Online Players", NamedTextColor.GOLD,
                List.of("Pick someone online to invite")));
        inv.setItem(22, button(Material.ARROW, "Back", NamedTextColor.GRAY, List.of()));
        player.openInventory(inv);
    }

    public void openInviteOnline(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_INVITE, NamedTextColor.GOLD, TextDecoration.BOLD));
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            if (teams.getByPlayer(online.getUniqueId()).isPresent()) continue;
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(online);
            meta.displayName(Component.text(online.getName(), NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("id:" + online.getUniqueId(), NamedTextColor.DARK_GRAY),
                    Component.text("Click to invite", NamedTextColor.GREEN)));
            skull.setItemMeta(meta);
            inv.setItem(slot++, skull);
        }
        inv.setItem(49, button(Material.ARROW, "Back", NamedTextColor.GRAY, List.of()));
        player.openInventory(inv);
    }

    public void openMembers(Player player) {
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) {
            openMain(player);
            return;
        }
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_MEMBERS, NamedTextColor.YELLOW, TextDecoration.BOLD));
        String myRole = teams.getRole(player.getUniqueId()).orElse("member");
        boolean manage = TeamService.canManageClaims(myRole);
        int slot = 0;
        for (String entry : teams.members(team.get().id())) {
            if (slot >= 45) break;
            String[] parts = entry.split(":", 2);
            String role = parts[0];
            UUID uuid = UUID.fromString(parts[1]);
            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(off);
            String name = off.getName() != null ? off.getName() : uuid.toString().substring(0, 8);
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Role: " + role, NamedTextColor.GRAY));
            lore.add(Component.text("id:" + uuid, NamedTextColor.DARK_GRAY));
            if (manage && !uuid.equals(team.get().owner())) {
                lore.add(Component.empty());
                lore.add(Component.text("Left-click: promote", NamedTextColor.GREEN));
                lore.add(Component.text("Right-click: demote", NamedTextColor.YELLOW));
                lore.add(Component.text("Shift-click: kick", NamedTextColor.RED));
            }
            meta.lore(lore);
            skull.setItemMeta(meta);
            inv.setItem(slot++, skull);
        }
        inv.setItem(49, button(Material.ARROW, "Back", NamedTextColor.GRAY, List.of()));
        player.openInventory(inv);
    }

    public void openHomes(Player player) {
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) {
            openMain(player);
            return;
        }
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_HOMES, NamedTextColor.GOLD, TextDecoration.BOLD));
        int slot = 0;
        for (TeamHomeService.TeamHome home : teamHomes.list(team.get().id())) {
            if (slot >= 45) break;
            boolean allowed = teamHomes.canUse(player.getUniqueId(), team.get().id(), home.name());
            ItemStack bed = new ItemStack(allowed ? Material.RED_BED : Material.GRAY_BED);
            ItemMeta meta = bed.getItemMeta();
            meta.displayName(Component.text(home.name(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Access: " + home.accessMode(), NamedTextColor.GRAY));
            lore.add(Component.text("home:" + home.name(), NamedTextColor.DARK_GRAY));
            lore.add(Component.text(allowed ? "Left-click to teleport" : "No permission",
                    allowed ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Component.text("Shift-click to delete", NamedTextColor.RED));
            meta.lore(lore);
            bed.setItemMeta(meta);
            inv.setItem(slot++, bed);
        }
        inv.setItem(45, button(Material.LIME_BED, "Set Home Here", NamedTextColor.GREEN,
                List.of("Sets team home 'home' here")));
        inv.setItem(49, button(Material.ARROW, "Back", NamedTextColor.GRAY, List.of()));
        player.openInventory(inv);
    }

    public void openClaims(Player player) {
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) {
            openMain(player);
            return;
        }
        long id = team.get().id();
        int used = claims.count(id);
        int max = claims.maxClaims(id);
        double next1 = upgrades.nextClaimChunkCost(id);
        double next5 = upgrades.costForClaims(id, 5);
        double next10 = upgrades.costForClaims(id, 10);
        double next25 = upgrades.costForClaims(id, 25);

        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_CLAIMS, NamedTextColor.GREEN, TextDecoration.BOLD));
        inv.setItem(4, button(Material.MAP, "Claim Limit", NamedTextColor.AQUA, List.of(
                "Used: " + used + " / " + max,
                "Base: members × " + claims.perMember(),
                "Bonus bought: +" + upgrades.bonusClaims(id),
                "Next chunk: " + economy.format(next1),
                "Price rises exponentially as you buy more"
        )));
        inv.setItem(11, button(Material.GOLDEN_SHOVEL, "Claim Here", NamedTextColor.GREEN,
                List.of("Claim the chunk you stand in", "Limit: " + used + "/" + max)));
        inv.setItem(13, button(Material.TNT, "Unclaim ALL", NamedTextColor.DARK_RED, List.of(
                "Remove EVERY team claim",
                "Requires 3 confirmations",
                "Owner/admin only",
                used + " chunk(s) currently claimed"
        )));
        inv.setItem(15, button(Material.IRON_SHOVEL, "Unclaim Here", NamedTextColor.RED,
                List.of("Unclaim the chunk you stand in")));

        inv.setItem(29, button(Material.EMERALD, "Buy +1 Claim", NamedTextColor.GREEN, List.of(
                "Cost: " + economy.format(next1),
                "Owner/admin only")));
        inv.setItem(31, button(Material.EMERALD_BLOCK, "Buy +5 Claims", NamedTextColor.GREEN, List.of(
                "Cost: " + economy.format(next5),
                "Owner/admin only")));
        inv.setItem(33, button(Material.DIAMOND, "Buy +10 Claims", NamedTextColor.AQUA, List.of(
                "Cost: " + economy.format(next10),
                "Owner/admin only")));
        inv.setItem(35, button(Material.NETHER_STAR, "Buy +25 Claims", NamedTextColor.LIGHT_PURPLE, List.of(
                "Cost: " + economy.format(next25),
                "Owner/admin only")));

        int slot = 45;
        for (ClaimService.Claim c : claims.list(id)) {
            if (slot >= 53) break;
            inv.setItem(slot++, button(Material.FILLED_MAP, c.world() + " " + c.x() + "," + c.z(),
                    NamedTextColor.WHITE, List.of("Claimed chunk")));
        }
        inv.setItem(49, button(Material.ARROW, "Back", NamedTextColor.GRAY, List.of()));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!isOurs(title)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        String name = PlainTextComponentSerializer.plainText().serialize(
                clicked.getItemMeta().displayName() == null ? Component.empty() : clicked.getItemMeta().displayName());

        if (title.contains(TITLE_MEMBERS)) {
            handleMembersClick(player, clicked, event.getClick(), name);
            return;
        }
        if (title.contains(TITLE_HOMES)) {
            handleHomesClick(player, clicked, event.getClick(), name);
            return;
        }
        if (title.contains(TITLE_JOIN)) {
            handleJoinClick(player, clicked, name);
            return;
        }
        if (title.contains(TITLE_SETTINGS)) {
            handleSettingsClick(player, name);
            return;
        }
        if (title.contains(TITLE_INVITE)) {
            handleInviteClick(player, clicked, name);
            return;
        }
        if (title.contains(TITLE_CLAIMS)) {
            handleClaimsClick(player, name);
            return;
        }
        if (title.startsWith(TITLE_UNCLAIM_ALL)) {
            handleUnclaimAllClick(player, title, name);
            return;
        }

        switch (name) {
            case "Close" -> player.closeInventory();
            case "Create a Team" -> {
                if (prompts != null) prompts.askCreate(player);
            }
            case "Browse Open Teams" -> openJoinBrowser(player);
            case "Join by Name" -> {
                if (prompts != null) prompts.askJoinName(player);
            }
            case "Members" -> openMembers(player);
            case "Team Homes" -> openHomes(player);
            case "Claims" -> openClaims(player);
            case "Settings" -> openSettings(player);
            case "Leave Team" -> {
                player.closeInventory();
                if (teams.leave(player.getUniqueId())) {
                    player.sendMessage("§crEconomy §8» §7Left team.");
                } else player.sendMessage("§cNot on a team.");
            }
            case "Disband Team" -> {
                player.closeInventory();
                Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
                if (team.isEmpty() || !team.get().owner().equals(player.getUniqueId())) {
                    player.sendMessage("§cOnly the owner can disband.");
                    return;
                }
                teams.disband(team.get().id());
                player.sendMessage("§crEconomy §8» §7Team disbanded.");
            }
            default -> {
                if (name.startsWith("Pending Invites")) openJoinBrowser(player);
            }
        }
    }

    private void handleJoinClick(Player player, ItemStack clicked, String name) {
        if (name.equals("Back")) {
            openMain(player);
            return;
        }
        if (name.equals("Join by Name")) {
            if (prompts != null) prompts.askJoinName(player);
            return;
        }
        Long tid = extractTeamId(clicked);
        if (tid == null) return;
        if (teams.joinById(player.getUniqueId(), tid)) {
            player.sendMessage("§crEconomy §8» §7Joined team!");
            openMain(player);
        } else {
            player.sendMessage("§cCould not join (closed without invite, or already on a team).");
        }
    }

    private void handleSettingsClick(Player player, String name) {
        if (name.equals("Back")) {
            openMain(player);
            return;
        }
        if (name.startsWith("Join:")) {
            Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
            if (team.isEmpty()) return;
            boolean next = !team.get().open();
            if (teams.setOpen(player.getUniqueId(), next)) {
                player.sendMessage("§crEconomy §8» §7Team is now §f" + (next ? "OPEN" : "CLOSED (invite only)"));
                openSettings(player);
            } else {
                player.sendMessage("§cNeed owner/admin.");
            }
            return;
        }
        if (name.equals("Invite Player")) {
            if (prompts != null) prompts.askInvite(player);
            return;
        }
        if (name.equals("Online Players")) {
            openInviteOnline(player);
        }
    }

    private void handleInviteClick(Player player, ItemStack clicked, String name) {
        if (name.equals("Back")) {
            openSettings(player);
            return;
        }
        UUID id = extractUuid(clicked);
        if (id == null) return;
        if (teams.invite(player.getUniqueId(), id)) {
            player.sendMessage("§crEconomy §8» §7Invited §f" + name);
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
                String tname = team.map(TeamService.Team::name).orElse("a team");
                online.sendMessage("§crEconomy §8» §f" + player.getName() + " §7invited you to §f" + tname);
                online.sendMessage("§7Open §f/team §7→ Browse, or §f/team join " + tname);
            }
            openInviteOnline(player);
        } else {
            player.sendMessage("§cCould not invite (need owner/admin; they must be teamless).");
        }
    }

    private void handleClaimsClick(Player player, String name) {
        if (name.equals("Back")) {
            openMain(player);
            return;
        }
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) {
            openMain(player);
            return;
        }
        if (name.startsWith("Buy +")) {
            int amount = switch (name) {
                case "Buy +1 Claim" -> 1;
                case "Buy +5 Claims" -> 5;
                case "Buy +10 Claims" -> 10;
                case "Buy +25 Claims" -> 25;
                default -> 0;
            };
            if (amount <= 0) return;
            double cost = upgrades.costForClaims(team.get().id(), amount);
            if (upgrades.buyClaimSlots(player.getUniqueId(), amount)) {
                player.sendMessage("§crEconomy §8» §7Bought §f+" + amount + " §7claims for §a"
                        + economy.format(cost) + " §8(§7limit §f" + claims.maxClaims(team.get().id()) + "§8)");
            } else {
                player.sendMessage("§cCould not buy — need owner/admin + " + economy.format(cost));
            }
            openClaims(player);
            return;
        }
        if (name.equals("Claim Here")) {
            int cx = player.getLocation().getChunk().getX();
            int cz = player.getLocation().getChunk().getZ();
            if (claims.isSpawnProtected(player.getWorld().getName(), cx, cz)) {
                player.sendMessage("§crEconomy §8» §7You can't claim land at spawn.");
            } else if (claims.claim(player.getUniqueId(), player.getWorld().getName(), cx, cz)) {
                player.sendMessage("§crEconomy §8» §7Chunk claimed.");
            } else {
                player.sendMessage("§cUnable to claim (limit " + claims.count(team.get().id())
                        + "/" + claims.maxClaims(team.get().id()) + "). Buy more below.");
            }
            openClaims(player);
            return;
        }
        if (name.equals("Unclaim Here")) {
            if (claims.unclaim(player.getUniqueId(), player.getWorld().getName(),
                    player.getLocation().getChunk().getX(), player.getLocation().getChunk().getZ())) {
                player.sendMessage("§crEconomy §8» §7Chunk unclaimed.");
            } else {
                player.sendMessage("§cUnable to unclaim.");
            }
            openClaims(player);
            return;
        }
        if (name.equals("Unclaim ALL")) {
            openUnclaimAllConfirm(player, 1);
        }
    }

    private void openUnclaimAllConfirm(Player player, int step) {
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) {
            openMain(player);
            return;
        }
        String role = teams.getRole(player.getUniqueId()).orElse("member");
        if (!TeamService.canManageClaims(role)) {
            player.sendMessage("§cNeed team owner/admin.");
            openClaims(player);
            return;
        }
        int count = claims.count(team.get().id());
        if (count <= 0) {
            player.sendMessage("§crEconomy §8» §7No claims to remove.");
            openClaims(player);
            return;
        }
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_UNCLAIM_ALL + " " + step + "/3", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        List<String> warn = switch (step) {
            case 1 -> List.of(
                    "Step 1 of 3",
                    "Remove ALL " + count + " claim(s) for " + team.get().name(),
                    "This cannot be undone.",
                    "Click Confirm to continue"
            );
            case 2 -> List.of(
                    "Step 2 of 3",
                    "Every claimed chunk becomes wilderness.",
                    "Bought claim slots stay — land is cleared.",
                    "Click Confirm again"
            );
            default -> List.of(
                    "FINAL confirmation (3/3)",
                    "Unclaim " + count + " chunk(s) NOW?",
                    "There is no undo.",
                    "Click Confirm to wipe all claims"
            );
        };
        inv.setItem(11, button(Material.LIME_CONCRETE, "Confirm", NamedTextColor.GREEN, warn));
        inv.setItem(13, button(Material.TNT, "Unclaim ALL", NamedTextColor.DARK_RED, List.of(
                "Team: " + team.get().name(),
                "Chunks: " + count,
                "Step " + step + " / 3"
        )));
        inv.setItem(15, button(Material.RED_CONCRETE, "Cancel", NamedTextColor.RED, List.of("Back to claims menu")));
        player.openInventory(inv);
    }

    private void handleUnclaimAllClick(Player player, String title, String name) {
        if (name.equals("Cancel") || name.equals("Back")) {
            openClaims(player);
            return;
        }
        if (!name.equals("Confirm")) return;
        int step = 1;
        try {

            int slash = title.lastIndexOf('/');
            int sp = title.lastIndexOf(' ');
            if (sp > 0 && slash > sp) {
                step = Integer.parseInt(title.substring(sp + 1, slash).trim());
            }
        } catch (NumberFormatException ignored) {
        }
        if (step < 3) {
            openUnclaimAllConfirm(player, step + 1);
            return;
        }
        int removed = claims.unclaimAll(player.getUniqueId());
        player.closeInventory();
        if (removed < 0) {
            player.sendMessage("§cCould not unclaim all (need owner/admin + team).");
        } else {
            player.sendMessage("§crEconomy §8» §cUnclaimed §f" + removed + " §cchunk(s). Land is unprotected.");
        }
    }

    private void handleMembersClick(Player player, ItemStack clicked, ClickType click, String displayName) {
        if (displayName.equals("Back")) {
            openMain(player);
            return;
        }
        UUID target = extractUuid(clicked);
        if (target == null) return;
        if (click.isShiftClick()) {
            if (teams.kick(player.getUniqueId(), target)) {
                player.sendMessage("§crEconomy §8» §7Kicked §f" + displayName);
                Player online = Bukkit.getPlayer(target);
                if (online != null) online.sendMessage("§crEconomy §8» §7You were kicked from the team.");
            } else player.sendMessage("§cCould not kick.");
            openMembers(player);
            return;
        }
        if (click.isLeftClick()) {
            if (teams.setRole(player.getUniqueId(), target, "admin")) {
                player.sendMessage("§crEconomy §8» §7Promoted §f" + displayName);
            } else player.sendMessage("§cCould not promote.");
            openMembers(player);
            return;
        }
        if (click.isRightClick()) {
            if (teams.setRole(player.getUniqueId(), target, "member")) {
                player.sendMessage("§crEconomy §8» §7Demoted §f" + displayName);
            } else player.sendMessage("§cCould not demote.");
            openMembers(player);
        }
    }

    private void handleHomesClick(Player player, ItemStack clicked, ClickType click, String displayName) {
        if (displayName.equals("Back")) {
            openMain(player);
            return;
        }
        if (displayName.equals("Set Home Here")) {
            if (teamHomes.setHome(player, "home")) {
                player.sendMessage("§crEconomy §8» §7Team home §fhome §7set.");
            } else player.sendMessage("§cCould not set team home.");
            openHomes(player);
            return;
        }
        String homeName = extractHome(clicked);
        if (homeName == null) return;
        if (click.isShiftClick()) {
            if (teamHomes.delete(player, homeName)) {
                player.sendMessage("§crEconomy §8» §7Deleted team home §f" + homeName);
            } else {
                player.sendMessage("§cCould not delete (need owner/admin, or home missing).");
            }
            openHomes(player);
            return;
        }
        Optional<TeamService.Team> team = teams.getByPlayer(player.getUniqueId());
        if (team.isEmpty()) return;
        var loc = teamHomes.getLocation(team.get().id(), homeName);
        if (loc == null) {
            player.sendMessage("§cTeam home not found.");
            return;
        }
        if (!teamHomes.canUse(player.getUniqueId(), team.get().id(), homeName)) {
            player.sendMessage("§cNo permission for team home §f" + homeName);
            return;
        }
        Location dest = loc.clone();
        player.closeInventory();
        warmup.begin(player, "team-home", p -> {
            p.teleportAsync(dest);
            p.sendMessage("§crEconomy §8» §7Teleported to team home §f" + homeName);
        });
    }

    private static boolean isOurs(String title) {
        return title.contains(TITLE_MAIN) || title.contains(TITLE_MEMBERS) || title.contains(TITLE_HOMES)
                || title.contains(TITLE_JOIN) || title.contains(TITLE_SETTINGS)
                || title.contains(TITLE_INVITE) || title.contains(TITLE_CLAIMS)
                || title.startsWith(TITLE_UNCLAIM_ALL);
    }

    private static UUID extractUuid(ItemStack item) {
        if (item.getItemMeta() == null || item.getItemMeta().lore() == null) return null;
        for (Component line : item.getItemMeta().lore()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.startsWith("id:")) {
                try {
                    return UUID.fromString(plain.substring(3).trim());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static Long extractTeamId(ItemStack item) {
        if (item.getItemMeta() == null || item.getItemMeta().lore() == null) return null;
        for (Component line : item.getItemMeta().lore()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.startsWith("tid:")) {
                try {
                    return Long.parseLong(plain.substring(4).trim());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static String extractHome(ItemStack item) {
        if (item.getItemMeta() == null || item.getItemMeta().lore() == null) return null;
        for (Component line : item.getItemMeta().lore()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.startsWith("home:")) return plain.substring(5).trim();
        }
        return null;
    }

    private static ItemStack button(Material mat, String name, NamedTextColor color, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static void sendHelp(Player player) {
        player.sendMessage("§crEconomy §8» §fTeams & Claims");
        player.sendMessage("§8 §7GUI: §e/team");
        player.sendMessage("§8 §7Guide: §e/guide");
        player.sendMessage("");
        player.sendMessage("§eBasics");
        player.sendMessage("§8 • §f/team create <name> <tag>");
        player.sendMessage("§8 • §f/team join <name> §7(open or invited)");
        player.sendMessage("§8 • §f/team open §8| §f/team close");
        player.sendMessage("§8 • §f/team invite <player>");
        player.sendMessage("§8 • §f/team leave §8| §f/team disband");
        player.sendMessage("");
        player.sendMessage("§eClaims");
        player.sendMessage("§8 • §f/claim §7· §f/unclaim §7· §f/claims");
        player.sendMessage("§8 • §f/unclaimall §7— wipe all land (§c3 confirms§7)");
        player.sendMessage("§8 • §f/trust <player> [access|container|build]");
        player.sendMessage("§8 • §f/untrust <player> §7· §f/trustlist");
        player.sendMessage("§8 • Buy more slots in §f/team §7→ Claims");
    }
}
