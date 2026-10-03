package net.ch4rlesexe.economy.coinflip;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CoinflipGui implements Listener {
    public static final String TITLE_MAIN = "rEconomy Coinflip";
    public static final String TITLE_AMOUNT = "Coinflip - Amount";
    public static final String TITLE_SIDE = "Coinflip - Side";
    public static final String TITLE_BROWSE = "Coinflip - Open Flips";
    public static final String TITLE_FLIP = "Coinflip!";

    private static final double[] PRESETS = {10, 25, 50, 100, 250, 500, 1000, 2500, 5000};

    private final REconomyPlugin plugin;
    private final CoinflipService flips;
    private final EconomyService economy;
    private final NamespacedKey keyAction;
    private final NamespacedKey keyValue;
    private final Map<UUID, Double> pendingAmount = new ConcurrentHashMap<>();
    private final Set<UUID> awaitingCustom = ConcurrentHashMap.newKeySet();
    private final Set<UUID> animating = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> animTasks = new ConcurrentHashMap<>();

    public CoinflipGui(REconomyPlugin plugin, CoinflipService flips, EconomyService economy) {
        this.plugin = plugin;
        this.flips = flips;
        this.economy = economy;
        this.keyAction = new NamespacedKey(plugin, "cf_action");
        this.keyValue = new NamespacedKey(plugin, "cf_value");
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_MAIN, NamedTextColor.GOLD, TextDecoration.BOLD));

        inv.setItem(4, tagged(Material.SUNFLOWER, "Coinflip", NamedTextColor.YELLOW, List.of(
                "Balance: " + economy.format(economy.getBalance(player.getUniqueId())),
                "Min: " + economy.format(flips.minAmount()),
                "50/50 — winner takes the pot",
                flips.hasOpen(player.getUniqueId()) ? "You have an open flip" : "No open flip"
        ), "noop", ""));

        inv.setItem(11, tagged(Material.GOLD_INGOT, "Create Flip", NamedTextColor.GREEN,
                List.of("Choose amount, then HEADS or TAILS"), "create", ""));
        inv.setItem(13, tagged(Material.COMPASS, "Browse Open", NamedTextColor.AQUA,
                List.of("Join someone else's flip"), "browse", ""));
        inv.setItem(15, tagged(Material.BARRIER, "Cancel Mine", NamedTextColor.RED,
                List.of("Refund your open coinflip"), "cancel", ""));
        inv.setItem(22, tagged(Material.ARROW, "Close", NamedTextColor.GRAY, List.of(), "close", ""));
        player.openInventory(inv);
    }

    public void openAmount(Player player) {
        Inventory inv = Bukkit.createInventory(player, 36,
                Component.text(TITLE_AMOUNT, NamedTextColor.GOLD, TextDecoration.BOLD));
        inv.setItem(4, tagged(Material.PAPER, "Pick Amount", NamedTextColor.YELLOW, List.of(
                "Balance: " + economy.format(economy.getBalance(player.getUniqueId())),
                "Or type a custom amount"
        ), "noop", ""));

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20};
        int i = 0;
        for (double amt : PRESETS) {
            if (i >= slots.length) break;
            if (amt < flips.minAmount()) continue;
            boolean afford = economy.getBalance(player.getUniqueId()) + 1e-9 >= amt;
            inv.setItem(slots[i++], tagged(afford ? Material.EMERALD : Material.COAL,
                    economy.format(amt),
                    afford ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY,
                    List.of(afford ? "Click, then pick a side" : "Not enough coins"),
                    "amount", Double.toString(amt)));
        }
        inv.setItem(22, tagged(Material.WRITABLE_BOOK, "Custom Amount", NamedTextColor.LIGHT_PURPLE,
                List.of("Type any amount in chat", "Must have the coins"), "custom", ""));
        inv.setItem(31, tagged(Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), "back_main", ""));
        player.openInventory(inv);
    }

    public void openSide(Player player, double amount) {
        pendingAmount.put(player.getUniqueId(), amount);
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(TITLE_SIDE, NamedTextColor.YELLOW, TextDecoration.BOLD));
        inv.setItem(4, tagged(Material.GOLD_NUGGET, "Amount: " + economy.format(amount), NamedTextColor.GOLD,
                List.of("Pick your side", "Opponent gets the other"), "noop", ""));
        inv.setItem(11, tagged(Material.YELLOW_CONCRETE, "HEADS", NamedTextColor.WHITE,
                List.of("Create flip on HEADS"), "side", "HEADS"));
        inv.setItem(15, tagged(Material.LIGHT_GRAY_CONCRETE, "TAILS", NamedTextColor.GRAY,
                List.of("Create flip on TAILS"), "side", "TAILS"));
        inv.setItem(22, tagged(Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), "create", ""));
        player.openInventory(inv);
    }

    public void openBrowse(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54,
                Component.text(TITLE_BROWSE, NamedTextColor.AQUA, TextDecoration.BOLD));
        inv.setItem(4, tagged(Material.COMPASS, "Open Coinflips", NamedTextColor.AQUA,
                List.of("Click to join (same amount)"), "noop", ""));

        List<CoinflipService.Flip> open = flips.listOpen(36);
        int slot = 9;
        for (CoinflipService.Flip flip : open) {
            if (slot >= 45) break;
            boolean own = flip.creator().equals(player.getUniqueId());
            boolean afford = economy.getBalance(player.getUniqueId()) + 1e-9 >= flip.amount();
            Material mat = own ? Material.BEDROCK : (afford ? Material.SUNFLOWER : Material.COAL);
            inv.setItem(slot++, tagged(mat,
                    "#" + flip.id() + " " + economy.format(flip.amount()),
                    own ? NamedTextColor.DARK_GRAY : NamedTextColor.YELLOW,
                    List.of(
                            "By: " + nameOf(flip.creator()),
                            "Side: " + flip.side(),
                            own ? "Your flip" : (afford ? "Click to join" : "Can't afford"),
                            "Pot: " + economy.format(flip.amount() * 2)
                    ),
                    own ? "noop" : "join", Long.toString(flip.id())));
        }
        if (open.isEmpty()) {
            inv.setItem(22, tagged(Material.PAPER, "No open flips", NamedTextColor.GRAY,
                    List.of("Create one!"), "noop", ""));
        }
        inv.setItem(49, tagged(Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), "back_main", ""));
        player.openInventory(inv);
    }


    public void joinAndAnimate(Player joiner, long id) {
        Optional<CoinflipService.ResolvedFlip> resolved = flips.join(joiner, id);
        if (resolved.isEmpty()) return;
        playAnimation(resolved.get());
    }

    public void playAnimation(CoinflipService.ResolvedFlip resolved) {
        List<Player> viewers = new ArrayList<>();
        Player creator = Bukkit.getPlayer(resolved.creator());
        Player joiner = Bukkit.getPlayer(resolved.joiner());
        if (joiner != null) viewers.add(joiner);
        if (creator != null && (joiner == null || !creator.getUniqueId().equals(joiner.getUniqueId()))) {
            viewers.add(creator);
        }
        if (viewers.isEmpty()) {
            flips.announce(resolved);
            return;
        }

        for (Player viewer : viewers) {
            startFlipAnim(viewer, resolved);
        }


        Bukkit.getScheduler().runTaskLater(plugin, () -> flips.announce(resolved), 70L);
    }

    private void startFlipAnim(Player viewer, CoinflipService.ResolvedFlip resolved) {
        cancelAnim(viewer.getUniqueId());
        animating.add(viewer.getUniqueId());

        Inventory inv = Bukkit.createInventory(viewer, 27,
                Component.text(TITLE_FLIP, NamedTextColor.GOLD, TextDecoration.BOLD));

        OfflinePlayer creatorOff = Bukkit.getOfflinePlayer(resolved.creator());
        OfflinePlayer joinerOff = Bukkit.getOfflinePlayer(resolved.joiner());
        String creatorName = nameOf(resolved.creator());
        String joinerName = nameOf(resolved.joiner());

        inv.setItem(11, skullOf(creatorOff, creatorName + " (" + resolved.creatorSide() + ")",
                List.of("Creator", economy.format(resolved.amount()))));
        inv.setItem(15, skullOf(joinerOff, joinerName + " ("
                        + opposite(resolved.creatorSide()) + ")",
                List.of("Challenger", economy.format(resolved.amount()))));
        inv.setItem(4, tagged(Material.SUNFLOWER, "Flipping...", NamedTextColor.YELLOW,
                List.of("Pot: " + economy.format(resolved.amount() * 2)), "noop", ""));
        inv.setItem(13, sideItem(true));

        viewer.openInventory(inv);


        int[] delays = {2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 8, 10};
        final int[] step = {0};
        final boolean[] showingHeads = {true};

        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!animating.contains(viewer.getUniqueId()) || !viewer.isOnline()) {
                    cancelAnim(viewer.getUniqueId());
                    return;
                }
                String title = PlainTextComponentSerializer.plainText()
                        .serialize(viewer.getOpenInventory().title());
                if (!title.equals(TITLE_FLIP)) {

                    viewer.openInventory(inv);
                }

                boolean last = step[0] >= delays.length - 1;
                if (last) {
                    boolean landHeads = "HEADS".equals(resolved.resultSide());
                    inv.setItem(13, sideItem(landHeads));
                    String winName = nameOf(resolved.winner());
                    inv.setItem(4, tagged(Material.NETHER_STAR, winName + " wins!", NamedTextColor.GREEN,
                            List.of(resolved.resultSide(),
                                    economy.format(resolved.payout())), "noop", ""));
                    OfflinePlayer winnerOff = Bukkit.getOfflinePlayer(resolved.winner());
                    inv.setItem(22, skullOf(winnerOff, winName, List.of("Winner!")));
                    viewer.playSound(viewer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                    animating.remove(viewer.getUniqueId());
                    animTasks.remove(viewer.getUniqueId());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (viewer.isOnline()) {
                            String t = PlainTextComponentSerializer.plainText()
                                    .serialize(viewer.getOpenInventory().title());
                            if (t.equals(TITLE_FLIP)) viewer.closeInventory();
                        }
                    }, 40L);
                    return;
                }

                showingHeads[0] = !showingHeads[0];
                inv.setItem(13, sideItem(showingHeads[0]));
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f,
                        showingHeads[0] ? 1.6f : 1.0f);
                step[0]++;
                BukkitTask next = Bukkit.getScheduler().runTaskLater(plugin, this, delays[step[0]]);
                animTasks.put(viewer.getUniqueId(), next);
            }
        };

        BukkitTask first = Bukkit.getScheduler().runTaskLater(plugin, tick, delays[0]);
        animTasks.put(viewer.getUniqueId(), first);
    }

    private ItemStack sideItem(boolean heads) {
        if (heads) {
            return tagged(Material.YELLOW_CONCRETE, "HEADS", NamedTextColor.WHITE,
                    List.of("..."), "noop", "");
        }
        return tagged(Material.LIGHT_GRAY_CONCRETE, "TAILS", NamedTextColor.GRAY,
                List.of("..."), "noop", "");
    }

    private static String opposite(String side) {
        return "HEADS".equals(side) ? "TAILS" : "HEADS";
    }

    private void cancelAnim(UUID uuid) {
        animating.remove(uuid);
        BukkitTask t = animTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    public void askCustomAmount(Player player) {
        awaitingCustom.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("§crEconomy §8» §7Type a §fcoinflip amount §7in chat.");
        player.sendMessage("§7Min §f" + economy.format(flips.minAmount())
                + "§7. Type §fcancel §7to abort.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!awaitingCustom.contains(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        String msg = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("cancel")) {
                awaitingCustom.remove(player.getUniqueId());
                player.sendMessage("§crEconomy §8» §7Cancelled.");
                openMain(player);
                return;
            }
            try {
                double amount = Double.parseDouble(msg.replace(",", "").replace("$", ""));
                awaitingCustom.remove(player.getUniqueId());
                if (amount < flips.minAmount()) {
                    player.sendMessage("§cMinimum is " + economy.format(flips.minAmount()));
                    openAmount(player);
                    return;
                }
                if (economy.getBalance(player.getUniqueId()) + 1e-9 < amount) {
                    player.sendMessage("§cYou don't have that much.");
                    openAmount(player);
                    return;
                }
                openSide(player, amount);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid number. Try again or type §fcancel§c.");
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingCustom.remove(event.getPlayer().getUniqueId());
        pendingAmount.remove(event.getPlayer().getUniqueId());
        cancelAnim(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.equals(TITLE_FLIP) && animating.contains(player.getUniqueId())) {

        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.equals(TITLE_MAIN) && !title.equals(TITLE_AMOUNT) && !title.equals(TITLE_SIDE)
                && !title.equals(TITLE_BROWSE) && !title.equals(TITLE_FLIP)) return;
        event.setCancelled(true);
        if (title.equals(TITLE_FLIP) || animating.contains(player.getUniqueId())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(keyValue, PersistentDataType.STRING);
        if (action == null || action.equals("noop")) return;

        switch (action) {
            case "close" -> player.closeInventory();
            case "back_main" -> openMain(player);
            case "create" -> openAmount(player);
            case "browse" -> openBrowse(player);
            case "cancel" -> {
                player.closeInventory();
                flips.cancel(player);
            }
            case "custom" -> askCustomAmount(player);
            case "amount" -> {
                try {
                    double amt = Double.parseDouble(value);
                    if (economy.getBalance(player.getUniqueId()) + 1e-9 < amt) {
                        player.sendMessage("§cNot enough coins.");
                        return;
                    }
                    openSide(player, amt);
                } catch (NumberFormatException ignored) {
                }
            }
            case "side" -> {
                Double amt = pendingAmount.remove(player.getUniqueId());
                if (amt == null) {
                    openAmount(player);
                    return;
                }
                player.closeInventory();
                flips.create(player, amt, value);
            }
            case "join" -> {
                try {
                    long id = Long.parseLong(value);
                    player.closeInventory();
                    joinAndAnimate(player, id);
                } catch (NumberFormatException ignored) {
                }
            }
            default -> {
            }
        }
    }

    private ItemStack skullOf(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack tagged(Material mat, String name, NamedTextColor color, List<String> lore,
                             String action, String value) {
        ItemStack item = new ItemStack(mat.isItem() ? mat : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        if (value != null) {
            meta.getPersistentDataContainer().set(keyValue, PersistentDataType.STRING, value);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        return off.getName() != null ? off.getName() : "Unknown";
    }
}
