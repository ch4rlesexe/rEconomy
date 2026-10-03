package net.ch4rlesexe.economy.guide;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class GuideGui implements Listener {
    public static final String TITLE = "Server Guide";
    public static final String TITLE_DETAIL_PREFIX = "Guide · ";

    private final NamespacedKey topicKey;

    public GuideGui(net.ch4rlesexe.economy.REconomyPlugin plugin) {
        this.topicKey = new NamespacedKey(plugin, "guide_topic");
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE, NamedTextColor.RED, TextDecoration.BOLD));

        inv.setItem(4, item(Material.BOOK, "Getting Started", NamedTextColor.GOLD, List.of(
                "Welcome to the Economy SMP.",
                "Click a category for details.",
                "Tip: type /guide anytime."
        ), "start"));

        inv.setItem(19, topic(Material.GOLD_INGOT, "Money & Paying", "money", List.of(
                "/bal · /pay · /baltop",
                "How to send and check coins"
        )));
        inv.setItem(20, topic(Material.GOLDEN_APPLE, "Leaderboards", "leaderboards", List.of(
                "/top · /baltop · /killtop",
                "Money, playtime, PvP, teams…"
        )));
        inv.setItem(21, topic(Material.EMERALD, "Shops & Trading", "shops", List.of(
                "/shop · /ah · /wager · /coinflip",
                "Buy, sell, auction, gamble"
        )));
        inv.setItem(22, topic(Material.WHITE_BANNER, "Teams", "teams", List.of(
                "/team create · invite · homes",
                "Invite-only by default"
        )));
        inv.setItem(23, topic(Material.SHIELD, "Claims (land)", "claims", List.of(
                "/claim · /unclaim · /chunkmap",
                "How protection & trust work"
        )));
        inv.setItem(24, topic(Material.ENDER_PEARL, "Travel", "travel", List.of(
                "/home · /tpa · /spawn · /rtp",
                "Warmups + combat lock"
        )));
        inv.setItem(25, topic(Material.IRON_SWORD, "Combat & Stats", "combat", List.of(
                "/stats · /killtop · /top",
                "20s tag · logout = death"
        )));
        inv.setItem(29, topic(Material.NAME_TAG, "Looks & Maps", "cosmetics", List.of(
                "/namecolor · /createmap · /heads",
                "Colors, image maps, heads"
        )));
        inv.setItem(30, topic(Material.OAK_SIGN, "FAQ & Rules", "faq", List.of(
                "How to make money fast?",
                "Are griefing and raiding allowed?",
                "Do I lose my items on death?"
        )));
        inv.setItem(31, topic(Material.ITEM_FRAME, "Building Tips", "building", List.of(
                "Invisible frames · graves · villagers",
                "QoL tools around the world"
        )));
        inv.setItem(33, topic(Material.NETHER_STAR, "Quick Commands", "commands", List.of(
                "Cheat-sheet of every command",
                "Open for the full list"
        )));

        if (player.hasPermission("reconomy.economy.admin")) {
            inv.setItem(40, topic(Material.COMMAND_BLOCK, "Admin", "admin", List.of(
                    "/eco · /givemoney · /setspawn",
                    "Staff-only tools"
            )));
        }

        inv.setItem(49, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of(), "close"));
        player.openInventory(inv);
    }

    private void openTopic(Player player, String id) {
        if (id.equals("claims")) {
            openClaimsGuide(player);
            return;
        }
        if (id.equals("money")) {
            openMoneyGuide(player);
            return;
        }
        if (id.equals("leaderboards")) {
            openLeaderboardsGuide(player);
            return;
        }
        if (id.equals("faq")) {
            openFaqGuide(player);
            return;
        }
        Topic topic = topicOf(id, player);
        if (topic == null) {
            open(player);
            return;
        }
        Inventory inv = Bukkit.createInventory(player, 45,
                Component.text(TITLE_DETAIL_PREFIX + topic.title(), NamedTextColor.YELLOW, TextDecoration.BOLD));

        inv.setItem(13, item(topic.icon(), topic.title(), NamedTextColor.GOLD, topic.lines(), "noop"));
        inv.setItem(36, item(Material.ARROW, "Back", NamedTextColor.GRAY, List.of("Return to guide"), "home"));
        inv.setItem(44, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of(), "close"));
        player.openInventory(inv);
    }

    private void openMoneyGuide(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_DETAIL_PREFIX + "Money", NamedTextColor.GOLD, TextDecoration.BOLD));

        inv.setItem(10, item(Material.GOLD_INGOT, "/bal — balance", NamedTextColor.YELLOW,
                player.hasPermission("reconomy.economy.admin")
                        ? List.of(
                        "/bal — see your coin balance",
                        "Aliases: /balance · /money",
                        "",
                        "Staff can check others:",
                        "  /bal <player>",
                        "  /eco bal <player>",
                        "",
                        "Currency symbol is $.",
                        "Starter coins are granted on first join."
                )
                        : List.of(
                        "/bal — see your coin balance",
                        "Aliases: /balance · /money",
                        "",
                        "Currency symbol is $.",
                        "Starter coins are granted on first join."
                ), "noop"));

        inv.setItem(12, item(Material.PAPER, "/pay — send coins", NamedTextColor.GREEN, List.of(
                "/pay <player> <amount>",
                "",
                "Examples:",
                "  /pay Steve 100",
                "  /pay Alex 25.50",
                "",
                "Rules:",
                " • Player must be online",
                " • Amount must be positive",
                " • You can't pay yourself",
                " • Needs enough balance",
                "",
                "Both of you get a chat receipt.",
                "This is the main way to tip / trade cash."
        ), "noop"));

        inv.setItem(14, item(Material.GOLD_BLOCK, "/baltop — richest", NamedTextColor.GOLD, List.of(
                "/baltop — top coin balances",
                "Aliases: /balancetop · /moneytop",
                "",
                "Shows the richest players in chat.",
                "For more boards, use /top (GUI).",
                "",
                "Also: /top money"
        ), "noop"));

        inv.setItem(16, item(Material.EMERALD, "How to earn", NamedTextColor.DARK_GREEN, List.of(
                "Ways to get coins:",
                " • Sell / buy at /shop",
                " • List & sell on /ah",
                " • Win /wager or /coinflip",
                " • Get paid by other players (/pay)",
                " • Play the economy loop",
                "",
                "Spend on: RTP, heads, name colors,",
                "createmap ($10k), claim upgrades,",
                "team homes, shops…"
        ), "noop"));

        if (player.hasPermission("reconomy.economy.admin")) {
            inv.setItem(28, item(Material.COMMAND_BLOCK, "Staff money tools", NamedTextColor.RED, List.of(
                    "Requires reconomy.economy.admin",
                    "",
                    "/eco give <player> <amount>",
                    "/eco take <player> <amount>",
                    "/eco set <player> <amount>",
                    "/eco bal <player>",
                    "/givemoney <player> <amount>",
                    "",
                    "Players normally use /pay only."
            ), "noop"));
        }

        inv.setItem(30, item(Material.GOLDEN_APPLE, "See also: Leaderboards", NamedTextColor.AQUA, List.of(
                "Open /guide → Leaderboards",
                "or type /top for the GUI.",
                "",
                "Click here to jump →"
        ), "leaderboards"));

        inv.setItem(45, item(Material.ARROW, "Back", NamedTextColor.GRAY, List.of("Return to guide"), "home"));
        inv.setItem(49, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of(), "close"));
        player.openInventory(inv);
    }

    private void openFaqGuide(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_DETAIL_PREFIX + "FAQ & Rules", NamedTextColor.GREEN, TextDecoration.BOLD));

        inv.setItem(10, item(Material.DIAMOND, "How do I make money fast?", NamedTextColor.AQUA, List.of(
                "1. Build a massive farm (Cactus, Sugarcane, etc.)",
                "   and sell the drops at /shop.",
                "2. Hunt rare mobs and sell their drops.",
                "3. Find rare items (like spawners or custom items)",
                "   and sell them on the Auction House (/ah)."
        ), "noop"));

        inv.setItem(12, item(Material.CHEST, "How do I protect my chests?", NamedTextColor.YELLOW, List.of(
                "When you claim land with /claim,",
                "ALL containers (chests, barrels, furnaces)",
                "are automatically locked to your team.",
                "",
                "No need for signs. Only people you /trust",
                "can open them."
        ), "noop"));

        inv.setItem(14, item(Material.SKELETON_SKULL, "Do I lose items on death?", NamedTextColor.RED, List.of(
                "Yes! If you die, your items drop.",
                "A 'Death Grave' will spawn where you died.",
                "Anyone can loot your grave, so hurry back!",
                "",
                "Keep inventory is OFF."
        ), "noop"));

        inv.setItem(16, item(Material.FLINT_AND_STEEL, "Is griefing allowed?", NamedTextColor.GOLD, List.of(
                "In unclaimed land (the wild), griefing",
                "and raiding are technically allowed.",
                "",
                "However, malicious server griefing",
                "(lag machines, massive lava casts) is NOT.",
                "",
                "Protect your stuff by using /claim."
        ), "noop"));

        inv.setItem(28, item(Material.GOLDEN_APPLE, "Can I get scammed?", NamedTextColor.GREEN, List.of(
                "Trading items using /ah is 100% safe.",
                "",
                "However, doing manual trades (dropping items)",
                "or gambling (/coinflip, /wager) always",
                "carries risk. Trade carefully!"
        ), "noop"));

        inv.setItem(30, item(Material.WHITE_BANNER, "How do I make a team?", NamedTextColor.LIGHT_PURPLE, List.of(
                "Use /team create <name> <tag>.",
                "",
                "Teams are invite-only by default.",
                "You need a team to /claim land",
                "and protect your chests!"
        ), "noop"));

        inv.setItem(32, item(Material.ENDER_PEARL, "How do I get to the End/Nether?", NamedTextColor.DARK_PURPLE, List.of(
                "The Nether can be accessed normally",
                "via Nether Portals.",
                "",
                "The End requires finding a Stronghold",
                "in the wild."
        ), "noop"));

        inv.setItem(34, item(Material.BEACON, "What are the rules?", NamedTextColor.WHITE, List.of(
                "1. No hacking or unfair clients.",
                "2. Respect other players in chat.",
                "3. No lagging the server intentionally.",
                "4. Staff decisions are final.",
                "",
                "Use common sense and have fun!"
        ), "noop"));

        inv.setItem(45, item(Material.ARROW, "Back", NamedTextColor.GRAY, List.of("Return to guide"), "home"));
        inv.setItem(49, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of(), "close"));
        player.openInventory(inv);
    }

    private void openLeaderboardsGuide(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_DETAIL_PREFIX + "Leaderboards", NamedTextColor.GOLD, TextDecoration.BOLD));

        inv.setItem(4, item(Material.GOLDEN_APPLE, "/top — main boards GUI", NamedTextColor.GOLD, List.of(
                "/top  (aliases: /leaderboard /lb /tops)",
                "",
                "Opens a GUI of every ranking.",
                "Click a board → see the top players.",
                "Mobs & crops have per-type drilldowns.",
                "",
                "Chat shortcut examples:",
                "  /top money",
                "  /top playtime",
                "  /top kills",
                "  /top wealth"
        ), "noop"));

        inv.setItem(19, item(Material.GOLD_INGOT, "Money boards", NamedTextColor.YELLOW, List.of(
                "Money — richest coin balances",
                "  Also: /baltop · /top money",
                "",
                "Team Wealth — member coins",
                "  + valuables in claim chests/",
                "    barrels / shulkers / hoppers",
                "  Also: /top wealth",
                "",
                "Store loot in claimed land to climb."
        ), "noop"));

        inv.setItem(21, item(Material.CLOCK, "Playtime", NamedTextColor.AQUA, List.of(
                "Playtime — longest time online",
                "  Synced from vanilla stats",
                "  Also: /top playtime",
                "",
                "Shown on /stats too."
        ), "noop"));

        inv.setItem(23, item(Material.IRON_SWORD, "PvP boards", NamedTextColor.RED, List.of(
                "Kills — most player kills",
                "Deaths — most deaths",
                "KDR — kill/death ratio",
                "Streak — best killstreak",
                "",
                "Also: /killtop · /top kills",
                "/stats [player] for personal stats",
                "",
                "Combat-log deaths count as a kill",
                "for the opponent."
        ), "noop"));

        inv.setItem(25, item(Material.ZOMBIE_HEAD, "Mobs & crops", NamedTextColor.GREEN, List.of(
                "Mob Kills — total PvE kills",
                "  Click for per-mob boards",
                "  (zombie, creeper, etc.)",
                "",
                "Crops — farmed / harvested",
                "  Click for per-crop boards",
                "  (wheat, carrots, cane…)",
                "",
                "Also: /top mobs · /top crops"
        ), "noop"));

        inv.setItem(29, item(Material.WHITE_BANNER, "Team boards", NamedTextColor.WHITE, List.of(
                "Team Size — most members",
                "Team Claims — most claimed chunks",
                "Team Wealth — coins + claim chests",
                "",
                "Also: /top teams · /top claims · /top wealth",
                "",
                "Claim more land & store valuables",
                "to climb Team Wealth."
        ), "noop"));

        inv.setItem(33, item(Material.COMPASS, "Quick opens", NamedTextColor.LIGHT_PURPLE, List.of(
                "In chat / GUI:",
                "  /top",
                "  /baltop",
                "  /killtop",
                "  /stats",
                "",
                "Money tips: /guide → Money"
        ), "money"));

        inv.setItem(45, item(Material.ARROW, "Back", NamedTextColor.GRAY, List.of("Return to guide"), "home"));
        inv.setItem(49, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of(), "close"));
        player.openInventory(inv);
    }


    private void openClaimsGuide(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_DETAIL_PREFIX + "Claims", NamedTextColor.GREEN, TextDecoration.BOLD));

        inv.setItem(10, item(Material.GOLDEN_SHOVEL, "1. Claiming land", NamedTextColor.GREEN, List.of(
                "You must be on a team.",
                "Only owner/admin can claim.",
                "",
                "/claim — claim the chunk you stand in",
                "/chunkmap — see who owns nearby chunks",
                "/team → Claims → Claim Here",
                "",
                "A chunk is 16×16 blocks.",
                "Stand inside a chunk to claim it.",
                "Already-claimed land cannot be taken."
        ), "noop"));

        inv.setItem(12, item(Material.MAP, "2. Claim limit", NamedTextColor.AQUA, List.of(
                "Limit = (team members × 10)",
                "        + purchased bonus slots",
                "",
                "Example: 3 members → 30 claims base.",
                "",
                "Buy more in /team → Claims:",
                "  +1 / +5 / +10 / +25 chunks",
                "Price rises exponentially as you buy.",
                "Also: /team buyclaims <amount>",
                "",
                "/claims — list all your chunks"
        ), "noop"));

        inv.setItem(14, item(Material.IRON_SHOVEL, "3. Unclaiming", NamedTextColor.YELLOW, List.of(
                "/unclaim — remove the chunk you stand in",
                "/team → Claims → Unclaim Here",
                "",
                "/unclaimall  (or /unclaim all)",
                "  Wipes EVERY team claim.",
                "  Requires THREE confirmations:",
                "  1) /unclaimall",
                "  2) /unclaimall confirm",
                "  3) /unclaimall confirm yes",
                "  (or use the GUI Unclaim ALL button)",
                "",
                "Bonus slots you bought are kept.",
                "Only the land protection is cleared."
        ), "noop"));

        inv.setItem(16, item(Material.NAME_TAG, "4. Trust levels", NamedTextColor.GOLD, List.of(
                "Let non-members use your land safely.",
                "Owner/admin: /trust <player> [level]",
                "",
                "access — doors, buttons, levers, beds",
                "container — access + chests/barrels/",
                "             hoppers/furnaces/etc.",
                "build — container + place/break/buckets",
                "        (default if you omit the level)",
                "",
                "/untrust <player> — remove trust",
                "/trustlist — see who is trusted",
                "",
                "Team members always have full build."
        ), "noop"));

        List<String> protectLore = new ArrayList<>(List.of(
                "Outsiders without trust cannot:",
                " • Place / break blocks",
                " • Use buckets / flint & steel",
                " • Open containers (unless trusted)",
                " • Use doors/buttons (unless access+)",
                " • Hurt animals / villagers",
                " • Break frames / armor stands",
                " • Steal from item frames",
                " • Ride / break vehicles",
                "",
                "Also blocked in claims:",
                " • Enemy explosions · fire spread",
                " • Pistons pushing across borders",
                " • Liquids flowing into the claim",
                "",
                "Your team's TNT still breaks your land.",
                "Hostiles can still be fought.",
                "See nearby owners: /chunkmap"
        ));
        inv.setItem(28, item(Material.SHIELD, "5. What claims protect", NamedTextColor.RED, protectLore, "noop"));

        inv.setItem(30, item(Material.EMERALD, "6. Team wealth tip", NamedTextColor.DARK_GREEN, List.of(
                "/top → Team Wealth ranks teams by:",
                "  member coin balances",
                "  + valuables in claim chests/",
                "    barrels / shulkers / hoppers",
                "",
                "Store loot in claimed land to count it."
        ), "noop"));

        inv.setItem(32, item(Material.BOOK, "7. Quick recipe", NamedTextColor.WHITE, List.of(
                "1. /team create <name> <tag>",
                "2. Invite friends (/team → Invite)",
                "3. Stand on your base → /claim",
                "4. Expand with more /claim chunks",
                "5. /trust friends container or build",
                "6. Buy extra slots when you hit the limit",
                "",
                "Need a recap later? /guide → Claims"
        ), "noop"));

        inv.setItem(45, item(Material.ARROW, "Back", NamedTextColor.GRAY, List.of("Return to guide"), "home"));
        inv.setItem(49, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of(), "close"));
        player.openInventory(inv);
    }

    private Topic topicOf(String id, Player player) {
        boolean staff = player.hasPermission("reconomy.economy.admin");
        return switch (id) {
            case "start" -> new Topic("Getting Started", Material.BOOK, List.of(
                    "1. /bal — check your coins",
                    "2. /pay <player> <amount> — tip friends",
                    "3. /shop — buy & sell",
                    "4. /team + /claim — protect land",
                    "5. /top — climb the leaderboards",
                    "6. /home · /tpa · /spawn · /rtp",
                    "",
                    "Full details in each category.",
                    "Grief is logged — builds & breaks."
            ));
            case "shops" -> new Topic("Shops & Trading", Material.EMERALD, List.of(
                    "/shop — server market (UltimateShop)",
                    "  Search + favorites supported",
                    "/ah — player auction house",
                    "  List, browse, buy, cancel your listings",
                    "/heads — decorative heads ($75 each)",
                    "/wager — coin wagers vs a player",
                    "/coinflip — flips for any amount",
                    "",
                    "Old /market bazaar is disabled."
            ));
            case "teams" -> new Topic("Teams", Material.WHITE_BANNER, List.of(
                    "/team — main GUI (recommended)",
                    "/team create <name> <tag>",
                    "  Starts invite-only (closed).",
                    "/team open · /team close",
                    "/team invite <player> · /team join <name>",
                    "/team leave · /team disband (owner)",
                    "",
                    "Team homes: /team → Homes",
                    "  Default 2 · buy more in the GUI",
                    "  /team home [name] · /team sethome",
                    "  /team delhome · /team buyhome",
                    "",
                    "Tag shows in chat via LPC.",
                    "Claims are covered in Claims."
            ));
            case "travel" -> new Topic("Travel", Material.ENDER_PEARL, List.of(
                    "/spawn — return to spawn",
                    "/home /sethome /delhome /homes",
                    "  Default 2 personal homes",
                    "/tpa /tpahere /tpaccept /tpdeny /tpcancel",
                    "/rtp — paid wild TP (1k / 3k / 5k)",
                    "",
                    "Stand still during teleport warmup.",
                    "Combat tag (20s) blocks teleports.",
                    "Team homes: /team → Homes"
            ));
            case "combat" -> new Topic("Combat & Stats", Material.IRON_SWORD, List.of(
                    "Combat tag = 20 seconds after PvP.",
                    "Logout while tagged = death + kill credit.",
                    "",
                    "/stats [player] — kills, deaths, KDR,",
                    "  playtime, mobs, crops",
                    "/killtop — PvP kill leaderboard",
                    "/top — full boards GUI (see Leaderboards)",
                    "",
                    "Grief is logged (CoreProtect)."
            ));
            case "cosmetics" -> new Topic("Looks & Maps", Material.NAME_TAG, List.of(
                    "/namecolor — permanent name colors",
                    "  Username stays the same (no /nick)",
                    "  Buy once → keep forever",
                    "  Confirm with Yes / No",
                    "",
                    "/createmap <url> <size>",
                    "  Size required: 1–4 / 1x1–4x4 / 128–512",
                    "  Costs $10,000 — run twice in 15s",
                    "  Place maps in item frames to assemble",
                    "",
                    "/heads — buy decorative heads"
            ));
            case "building" -> new Topic("Building Tips", Material.ITEM_FRAME, List.of(
                    "Invisible item frames:",
                    "  Shears → hide frame (item floats)",
                    "  Leather → restore",
                    "  Honeycomb → wax/lock · Axe → unwax",
                    "",
                    "Death graves: public loot for anyone",
                    "",
                    "Villagers: scroll trades → Reroll Trades",
                    "  (emerald shown, not consumed)"
            ));
            case "commands" -> new Topic("Quick Commands", Material.NETHER_STAR, List.of(
                    "Money: /bal /pay /baltop /top",
                    "Shops: /shop /ah /heads /wager /coinflip",
                    "Teams: /team /claim /unclaim /unclaimall",
                    "Trust: /trust /untrust /trustlist /claims",
                    "Map: /chunkmap — who owns nearby land",
                    "Travel: /home /sethome /tpa /spawn /rtp",
                    "Stats: /stats /killtop /top",
                    "Looks: /namecolor /createmap /heads",
                    "Help: /guide /help"
            ));
            case "admin" -> staff ? new Topic("Admin", Material.COMMAND_BLOCK, List.of(
                    "/eco give|take|set|bal <player> [amt]",
                    "/givemoney <player> <amount>",
                    "/setspawn · /spawn rebuild",
                    "/claimbypass — toggle claim ignore",
                    "/vanish — hide from players (/v)",
                    "",
                    "Permission: reconomy.economy.admin",
                    "Claim bypass: reconomy.claims.bypass",
                    "Vanish: reconomy.vanish (+ .see)",
                    "Admins do NOT auto-bypass claims."
            )) : null;
            default -> null;
        };
    }

    private ItemStack topic(Material mat, String name, String id, List<String> preview) {
        List<String> lore = new ArrayList<>(preview);
        lore.add("");
        lore.add("Click to open");
        return item(mat, name, NamedTextColor.YELLOW, lore, id);
    }

    private ItemStack item(Material mat, String name, NamedTextColor color, List<String> lore, String id) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            if (line.isEmpty()) {
                lines.add(Component.empty());
            } else {
                lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lines);
        meta.getPersistentDataContainer().set(topicKey, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.equals(TITLE) && !title.startsWith(TITLE_DETAIL_PREFIX)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer().get(topicKey, PersistentDataType.STRING);
        if (id == null || id.equals("noop")) return;
        switch (id) {
            case "close" -> player.closeInventory();
            case "home" -> open(player);
            default -> openTopic(player, id);
        }
    }

    private record Topic(String title, Material icon, List<String> lines) {}
}
