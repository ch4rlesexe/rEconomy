package net.ch4rlesexe.economy;

import net.ch4rlesexe.economy.auction.AuctionCommands;
import net.ch4rlesexe.economy.auction.AuctionService;
import net.ch4rlesexe.economy.claim.ClaimBypassService;
import net.ch4rlesexe.economy.claim.ClaimListener;
import net.ch4rlesexe.economy.claim.ClaimService;
import net.ch4rlesexe.economy.command.BalanceCommands;
import net.ch4rlesexe.economy.command.ChunkMapCommand;
import net.ch4rlesexe.economy.command.ClaimBypassCommand;
import net.ch4rlesexe.economy.command.CreateMapCommand;
import net.ch4rlesexe.economy.command.EcoCommands;
import net.ch4rlesexe.economy.command.GovernmentCommands;
import net.ch4rlesexe.economy.government.GovernmentService;
import net.ch4rlesexe.economy.livingshop.LivingShopCommands;
import net.ch4rlesexe.economy.livingshop.ShopEconomyService;
import net.ch4rlesexe.economy.livingshop.UltimateShopHook;
import net.ch4rlesexe.economy.command.GuideCommand;
import net.ch4rlesexe.economy.guide.GuideGui;
import net.ch4rlesexe.economy.imagemap.ImageMapService;
import net.ch4rlesexe.economy.command.NameColorCommand;
import net.ch4rlesexe.economy.command.HeadsCommand;
import net.ch4rlesexe.economy.command.HomeCommands;
import net.ch4rlesexe.economy.command.RtpCommand;
import net.ch4rlesexe.economy.command.StatsCommands;
import net.ch4rlesexe.economy.command.TeamCommands;
import net.ch4rlesexe.economy.command.TpaCommands;
import net.ch4rlesexe.economy.command.TopCommand;
import net.ch4rlesexe.economy.command.VanishCommand;
import net.ch4rlesexe.economy.command.WagerCommands;
import net.ch4rlesexe.economy.vanish.VanishListener;
import net.ch4rlesexe.economy.vanish.VanishService;
import net.ch4rlesexe.economy.coinflip.CoinflipGui;
import net.ch4rlesexe.economy.coinflip.CoinflipService;
import net.ch4rlesexe.economy.command.CoinflipCommands;
import net.ch4rlesexe.economy.leaderboard.ClaimWealthService;
import net.ch4rlesexe.economy.leaderboard.LeaderboardGui;
import net.ch4rlesexe.economy.leaderboard.LeaderboardService;
import net.ch4rlesexe.economy.combat.CombatListener;
import net.ch4rlesexe.economy.combat.CombatTagService;
import net.ch4rlesexe.economy.db.Database;
import net.ch4rlesexe.economy.economy.EconomyService;
import net.ch4rlesexe.economy.frames.FrameListener;
import net.ch4rlesexe.economy.heads.HeadsShop;
import net.ch4rlesexe.economy.homes.HomeService;
import net.ch4rlesexe.economy.listener.ColorFormatListener;
import net.ch4rlesexe.economy.namecolor.NameColorGui;
import net.ch4rlesexe.economy.namecolor.NameColorService;
import net.ch4rlesexe.economy.listener.JoinListener;
import net.ch4rlesexe.economy.listener.PaidCommandListener;
import net.ch4rlesexe.economy.listener.TeamChatListener;
import net.ch4rlesexe.economy.market.MarketApiServer;
import net.ch4rlesexe.economy.market.MarketCommands;
import net.ch4rlesexe.economy.market.MarketService;
import net.ch4rlesexe.economy.placeholder.REconomyPlaceholders;
import net.ch4rlesexe.economy.rtp.RtpGui;
import net.ch4rlesexe.economy.rtp.RtpService;
import net.ch4rlesexe.economy.spawn.SpawnService;
import net.ch4rlesexe.economy.stats.ActivityListener;
import net.ch4rlesexe.economy.stats.ActivityStatsService;
import net.ch4rlesexe.economy.stats.StatsService;
import net.ch4rlesexe.economy.team.TeamChatPrompt;
import net.ch4rlesexe.economy.team.TeamGui;
import net.ch4rlesexe.economy.team.TeamHomeService;
import net.ch4rlesexe.economy.team.TeamService;
import net.ch4rlesexe.economy.team.TeamUpgradeService;
import net.ch4rlesexe.economy.tpa.TpaService;
import net.ch4rlesexe.economy.util.CooldownService;
import net.ch4rlesexe.economy.util.TeleportWarmupService;
import net.ch4rlesexe.economy.vault.VaultHook;
import net.ch4rlesexe.economy.wager.WagerGui;
import net.ch4rlesexe.economy.wager.WagerService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class REconomyPlugin extends JavaPlugin {
    private Database database;
    private EconomyService economy;
    private HomeService homes;
    private TpaService tpa;
    private TeamService teams;
    private TeamHomeService teamHomes;
    private ClaimService claims;
    private WagerService wagers;
    private CoinflipService coinflips;
    private MarketService market;
    private MarketApiServer marketApi;
    private SpawnService spawn;
    private AuctionService auctions;
    private GovernmentService government;
    private ShopEconomyService livingShop;
    private CooldownService cooldowns;
    private TeleportWarmupService warmup;
    private CombatTagService combat;
    private StatsService stats;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(this);
        database.init();

        economy = new EconomyService(this, database);
        homes = new HomeService(this, database);
        tpa = new TpaService(this);
        teams = new TeamService(this, database);
        teamHomes = new TeamHomeService(this, database, teams);
        claims = new ClaimService(this, database, teams);
        TeamUpgradeService upgrades = new TeamUpgradeService(this, database, economy, teams);
        teamHomes.setUpgrades(upgrades);
        claims.setUpgrades(upgrades);
        wagers = new WagerService(this, database, economy);
        coinflips = new CoinflipService(this, database, economy);
        boolean marketEnabled = getConfig().getBoolean("market.enabled", false);
        market = marketEnabled ? new MarketService(this, database, economy) : null;
        spawn = new SpawnService(this, database);
        claims.setSpawn(spawn);
        auctions = new AuctionService(this, database, economy);
        government = new GovernmentService(this, database, economy);
        wagers.setGovernment(government);
        coinflips.setGovernment(government);
        auctions.setGovernment(government);
        cooldowns = new CooldownService(this);
        warmup = new TeleportWarmupService(this, cooldowns);
        combat = new CombatTagService(this);
        stats = new StatsService(this, database);
        ActivityStatsService activity = new ActivityStatsService(this, database);
        cooldowns.setCombat(combat);
        warmup.setCombat(combat);
        spawn.setCooldowns(cooldowns);
        spawn.setWarmup(warmup);
        combat.start();
        if (market != null) {
            market.seedDefaults();
            market.startEngine();
        } else {
            getLogger().info("Live market (/market) disabled.");
        }

        VaultHook.register(this, economy);

        BalanceCommands balanceCommands = new BalanceCommands(economy);
        getCommand("bal").setExecutor(balanceCommands);
        getCommand("pay").setExecutor(balanceCommands);
        getCommand("baltop").setExecutor(balanceCommands);

        EcoCommands ecoCommands = new EcoCommands(economy);
        getCommand("eco").setExecutor(ecoCommands);
        getCommand("eco").setTabCompleter(ecoCommands);
        getCommand("givemoney").setExecutor(ecoCommands);
        getCommand("givemoney").setTabCompleter(ecoCommands);

        GovernmentCommands govCommands = new GovernmentCommands(government, economy);
        getCommand("gov").setExecutor(govCommands);
        getCommand("gov").setTabCompleter(govCommands);

        if (getConfig().getBoolean("living-shop.enabled", true)) {
            livingShop = new ShopEconomyService(this, database);
            livingShop.load();
            LivingShopCommands livingCmds = new LivingShopCommands(livingShop);
            getCommand("livingshop").setExecutor(livingCmds);
            getCommand("livingshop").setTabCompleter(livingCmds);
            UltimateShopHook.register(this, livingShop);
        } else {
            getLogger().info("Living shop (CSV) disabled.");
        }

        GuideGui guideGui = new GuideGui(this);
        GuideCommand guideCommand = new GuideCommand(guideGui);
        getCommand("guide").setExecutor(guideCommand);
        getServer().getPluginManager().registerEvents(guideGui, this);

        StatsCommands statsCommands = new StatsCommands(stats, activity);
        getCommand("stats").setExecutor(statsCommands);
        getCommand("killtop").setExecutor(statsCommands);

        LeaderboardService leaderboards = new LeaderboardService(this, database, economy, stats, activity);
        LeaderboardGui leaderboardGui = new LeaderboardGui(this, leaderboards);
        TopCommand topCommand = new TopCommand(leaderboardGui, leaderboards);
        getCommand("top").setExecutor(topCommand);
        getCommand("top").setTabCompleter(topCommand);
        getServer().getPluginManager().registerEvents(leaderboardGui, this);
        getServer().getPluginManager().registerEvents(new ActivityListener(activity), this);

        Bukkit.getScheduler().runTaskTimer(this, activity::syncAllOnline, 20L * 60, 20L * 60);

        ClaimWealthService claimWealth = new ClaimWealthService(this, database, claims, market);
        leaderboards.setClaimWealth(claimWealth);
        claimWealth.start();

        HomeCommands homeCommands = new HomeCommands(homes, warmup);
        getCommand("sethome").setExecutor(homeCommands);
        getCommand("home").setExecutor(homeCommands);
        getCommand("delhome").setExecutor(homeCommands);
        getCommand("homes").setExecutor(homeCommands);

        TpaCommands tpaCommands = new TpaCommands(tpa, cooldowns, warmup);
        getCommand("tpa").setExecutor(tpaCommands);
        getCommand("tpahere").setExecutor(tpaCommands);
        getCommand("tpaccept").setExecutor(tpaCommands);
        getCommand("tpdeny").setExecutor(tpaCommands);
        getCommand("tpcancel").setExecutor(tpaCommands);

        TeamCommands teamCommands = new TeamCommands(teams, claims, teamHomes, cooldowns, upgrades, economy, warmup);
        TeamGui teamGui = new TeamGui(teams, claims, teamHomes, cooldowns, upgrades, economy, warmup);
        TeamChatPrompt teamPrompts = new TeamChatPrompt(this);
        teamGui.setPrompts(teamPrompts);
        teamPrompts.setHandlers(
                (player, nameTag) -> {
                    if (teams.create(player.getUniqueId(), nameTag[0], nameTag[1])) {
                        player.sendMessage("§crEconomy §8» §7Team §f" + nameTag[0] + " §7[" + nameTag[1] + "] §7created §8(§aOPEN§8).");
                        teamGui.openMain(player);
                    } else {
                        player.sendMessage("§cCould not create team (name taken or you're already on one).");
                    }
                },
                (player, targetName) -> {
                    Player online = Bukkit.getPlayerExact(targetName);
                    UUID tid = online != null ? online.getUniqueId()
                            : (Bukkit.getOfflinePlayer(targetName).hasPlayedBefore()
                            ? Bukkit.getOfflinePlayer(targetName).getUniqueId() : null);
                    if (tid == null) {
                        player.sendMessage("§cPlayer not found.");
                        return;
                    }
                    if (teams.invite(player.getUniqueId(), tid)) {
                        player.sendMessage("§crEconomy §8» §7Invited §f" + targetName);
                        if (online != null) {
                            var team = teams.getByPlayer(player.getUniqueId());
                            online.sendMessage("§crEconomy §8» §f" + player.getName() + " §7invited you to §f"
                                    + team.map(TeamService.Team::name).orElse("a team"));
                        }
                        teamGui.openSettings(player);
                    } else {
                        player.sendMessage("§cCould not invite.");
                    }
                },
                (player, teamName) -> {
                    if (teams.join(player.getUniqueId(), teamName)) {
                        player.sendMessage("§crEconomy §8» §7Joined §f" + teamName);
                        teamGui.openMain(player);
                    } else {
                        player.sendMessage("§cCould not join.");
                    }
                }
        );
        teamCommands.setGui(teamGui);
        getCommand("team").setExecutor(teamCommands);
        getCommand("claim").setExecutor(teamCommands);
        getCommand("unclaim").setExecutor(teamCommands);
        getCommand("unclaimall").setExecutor(teamCommands);
        getCommand("claims").setExecutor(teamCommands);
        getCommand("trust").setExecutor(teamCommands);
        getCommand("untrust").setExecutor(teamCommands);
        getCommand("trustlist").setExecutor(teamCommands);

        ClaimBypassService claimBypass = new ClaimBypassService();
        getCommand("claimbypass").setExecutor(new ClaimBypassCommand(claimBypass));
        getCommand("chunkmap").setExecutor(new ChunkMapCommand(claims, teams));

        VanishService vanish = new VanishService(this);
        VanishCommand vanishCommand = new VanishCommand(vanish);
        getCommand("vanish").setExecutor(vanishCommand);
        getCommand("vanish").setTabCompleter(vanishCommand);
        getServer().getPluginManager().registerEvents(new VanishListener(vanish), this);

        WagerGui wagerGui = new WagerGui(this, wagers, economy);
        WagerCommands wagerCommands = new WagerCommands(wagers, wagerGui);
        getCommand("wager").setExecutor(wagerCommands);
        getCommand("wager").setTabCompleter(wagerCommands);
        getServer().getPluginManager().registerEvents(wagerGui, this);

        CoinflipGui coinflipGui = new CoinflipGui(this, coinflips, economy);
        CoinflipCommands coinflipCommands = new CoinflipCommands(coinflips, coinflipGui);
        getCommand("coinflip").setExecutor(coinflipCommands);
        getCommand("coinflip").setTabCompleter(coinflipCommands);
        getServer().getPluginManager().registerEvents(coinflipGui, this);
        if (market != null) {
            getCommand("market").setExecutor(new MarketCommands(market));
        } else {
            getCommand("market").setExecutor((sender, command, label, args) -> {
                sender.sendMessage("§crEconomy §8» §7The live market is disabled. Use §e/shop §7or §e/ah§7.");
                return true;
            });
        }
        getCommand("ah").setExecutor(new AuctionCommands(auctions));
        getCommand("spawn").setExecutor((sender, command, label, args) -> {
            if (args.length > 0 && args[0].equalsIgnoreCase("rebuild")
                    && sender.hasPermission("reconomy.economy.admin")) {
                spawn.forceRebuild();
                sender.sendMessage("§crEconomy §8» §7Rebuilding spawn...");
                return true;
            }
            if (sender instanceof Player player) {
                spawn.teleport(player);
            }
            return true;
        });
        getCommand("setspawn").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!player.hasPermission("reconomy.economy.admin")) {
                player.sendMessage("§crEconomy §8» §7No permission.");
                return true;
            }
            spawn.setSpawn(player);
            return true;
        });

        RtpService rtp = new RtpService(this, economy);
        RtpGui rtpGui = new RtpGui(this, rtp, economy, cooldowns, warmup);
        getCommand("rtp").setExecutor(new RtpCommand(rtpGui));
        getServer().getPluginManager().registerEvents(rtpGui, this);

        HeadsShop headsShop = new HeadsShop(this, economy);
        HeadsCommand headsCommand = new HeadsCommand(headsShop);
        getCommand("heads").setExecutor(headsCommand);
        getCommand("heads").setTabCompleter(headsCommand);
        getServer().getPluginManager().registerEvents(headsShop, this);

        NameColorService nameColors = new NameColorService(this, database, economy);
        NameColorGui nameColorGui = new NameColorGui(this, nameColors, economy);
        getCommand("namecolor").setExecutor(new NameColorCommand(nameColorGui));
        getServer().getPluginManager().registerEvents(nameColorGui, this);

        ImageMapService imageMaps = new ImageMapService(this, database);
        imageMaps.load();
        getServer().getPluginManager().registerEvents(imageMaps, this);
        CreateMapCommand createMapCommand = new CreateMapCommand(this, imageMaps, economy);
        getCommand("createmap").setExecutor(createMapCommand);
        getCommand("createmap").setTabCompleter(createMapCommand);

        if (getConfig().getBoolean("frames.enabled", true)) {
            getServer().getPluginManager().registerEvents(new FrameListener(this), this);
        }

        getServer().getPluginManager().registerEvents(new ClaimListener(this, claims, claimBypass, teams), this);
        getServer().getPluginManager().registerEvents(new JoinListener(economy, nameColors), this);
        getServer().getPluginManager().registerEvents(new ColorFormatListener(), this);
        getServer().getPluginManager().registerEvents(new PaidCommandListener(), this);
        getServer().getPluginManager().registerEvents(new CombatListener(combat, stats), this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(teams), this);
        getServer().getPluginManager().registerEvents(warmup, this);
        if (market != null) {
            getServer().getPluginManager().registerEvents(market, this);
        }
        getServer().getPluginManager().registerEvents(auctions, this);
        getServer().getPluginManager().registerEvents(teamGui, this);
        getServer().getPluginManager().registerEvents(teamPrompts, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new REconomyPlaceholders(this, teams).register();
            getLogger().info("Registered PlaceholderAPI expansion: %reconomy_*%");
        }

        if (market != null && getConfig().getBoolean("market.api.enabled", false)) {
            marketApi = new MarketApiServer(this, market);
            marketApi.start();
        }

        spawn.ensureSpawn();
        getLogger().info("rEconomy " + getDescription().getVersion()
                + " enabled. Warmup=" + warmup.warmupSeconds() + "s. zHead=" + headsShop.available());
    }

    @Override
    public void onDisable() {
        if (combat != null) {
            combat.stop();
        }
        if (marketApi != null) {
            marketApi.stop();
        }
        if (market != null) {
            market.stopEngine();
        }
        if (database != null) {
            database.close();
        }
    }

    public EconomyService economy() {
        return economy;
    }

    public TeamService teams() {
        return teams;
    }

    public ClaimService claims() {
        return claims;
    }

    public MarketService market() {
        return market;
    }
}
