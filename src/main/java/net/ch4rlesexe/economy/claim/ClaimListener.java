package net.ch4rlesexe.economy.claim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.ch4rlesexe.economy.team.TeamService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimListener implements Listener {
    private static final String DENY = "§crEconomy §8» §7This chunk is claimed.";
    private static final String META_TNT_SOURCE = "reconomy.tnt_source";
    private static final long TNT_PLACE_TTL_MS = 5 * 60 * 1000L;
    private static final long WILDERNESS = Long.MIN_VALUE;

    private final JavaPlugin plugin;
    private final ClaimService claims;
    private final ClaimBypassService bypass;
    private final TeamService teams;

    private final Map<String, PlacedTnt> placedTnt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClaimTeam = new ConcurrentHashMap<>();

    private record PlacedTnt(UUID placer, long at) {}

    public ClaimListener(JavaPlugin plugin, ClaimService claims, ClaimBypassService bypass, TeamService teams) {
        this.plugin = plugin;
        this.claims = claims;
        this.bypass = bypass;
        this.teams = teams;
    }

    private boolean isBypassing(Player player) {
        return bypass.isEnabled(player);
    }

    private void denyMessage(Player player) {
        player.sendMessage(DENY);
    }

    private boolean denyBuild(Player player, Location loc) {
        if (player == null || loc == null) return true;
        if (isBypassing(player)) return false;
        if (claims.canBuild(player.getUniqueId(), loc)) return false;
        denyMessage(player);
        return true;
    }

    private boolean denyContainer(Player player, Location loc) {
        if (player == null || loc == null) return true;
        if (isBypassing(player)) return false;
        if (claims.canContainer(player.getUniqueId(), loc)) return false;
        denyMessage(player);
        return true;
    }

    private boolean denyAccess(Player player, Location loc) {
        if (player == null || loc == null) return true;
        if (isBypassing(player)) return false;
        if (claims.canAccess(player.getUniqueId(), loc)) return false;
        denyMessage(player);
        return true;
    }

    private Player attackerAsPlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }



    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || to.getWorld() == null) return;
        if (from.getWorld() == to.getWorld()
                && from.getBlockX() >> 4 == to.getBlockX() >> 4
                && from.getBlockZ() >> 4 == to.getBlockZ() >> 4) {
            return;
        }
        notifyClaimChange(event.getPlayer(), to);
    }

    private void notifyClaimChange(Player player, Location loc) {
        Optional<ClaimService.Claim> claim = claims.get(
                loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        long teamId = claim.map(ClaimService.Claim::teamId).orElse(WILDERNESS);
        Long prev = lastClaimTeam.put(player.getUniqueId(), teamId);
        if (Objects.equals(prev, teamId)) return;

        if (teamId == WILDERNESS) {
            if (prev != null && prev != WILDERNESS) {
                player.sendActionBar(Component.text("Wilderness", NamedTextColor.GRAY));
            }
            return;
        }

        String label = teams.getById(teamId)
                .map(t -> t.name() + " [" + t.tag() + "]")
                .orElse("Team #" + teamId);
        boolean mine = teams.getByPlayer(player.getUniqueId())
                .map(t -> t.id() == teamId)
                .orElse(false);
        NamedTextColor color = mine ? NamedTextColor.GREEN : NamedTextColor.GOLD;
        player.sendActionBar(Component.text("Claim · ", NamedTextColor.DARK_GRAY)
                .append(Component.text(label, color)));
    }



    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (denyBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (denyBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (event.getBlock().getType() == Material.TNT) {
            Block b = event.getBlock();
            placedTnt.put(blockKey(b), new PlacedTnt(event.getPlayer().getUniqueId(), System.currentTimeMillis()));
            prunePlacedTnt();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        bypass.clear(event.getPlayer().getUniqueId());
        lastClaimTeam.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (denyBuild(event.getPlayer(), event.getBlockClicked().getRelative(event.getBlockFace()).getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (denyBuild(event.getPlayer(), event.getBlockClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (denyBuild(event.getPlayer(), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(org.bukkit.event.block.SignChangeEvent event) {
        if (denyBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }



    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material type = block.getType();
        Location loc = block.getLocation();

        if (action == Action.PHYSICAL) {
            if (isAccessBlock(type) || type == Material.FARMLAND || type.name().contains("PRESSURE_PLATE")
                    || type == Material.TRIPWIRE || type == Material.TRIPWIRE_HOOK) {
                if (denyAccess(player, loc)) event.setCancelled(true);
            }
            return;
        }

        if (type != Material.ENDER_CHEST && isContainerBlock(block)) {
            if (denyContainer(player, loc)) event.setCancelled(true);
            return;
        }

        if (isAccessBlock(type)) {
            if (denyAccess(player, loc)) event.setCancelled(true);
            return;
        }

        Material hand = event.getItem() == null ? Material.AIR : event.getItem().getType();
        if (isBuildTool(hand) || isBuildItem(hand)) {
            Block against = block.getRelative(event.getBlockFace());
            if (denyBuild(player, against.getLocation())) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder == null || holder instanceof Player) return;

        Location loc = null;
        if (holder instanceof BlockState state) {
            if (state.getType() == Material.ENDER_CHEST) return;
            loc = state.getLocation();
        } else if (holder instanceof Entity entity) {
            loc = entity.getLocation();
        }
        if (loc == null) return;
        if (denyContainer(player, loc)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLectern(PlayerTakeLecternBookEvent event) {
        if (denyContainer(event.getPlayer(), event.getLectern().getLocation())) {
            event.setCancelled(true);
        }
    }



    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if (target instanceof Player) return;
        if (target instanceof Enemy) return;
        Player player = event.getPlayer();
        if (isBypassing(player)) return;
        if (claims.canHurtEntities(player.getUniqueId(), target.getLocation())) return;
        denyMessage(player);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (denyBuild(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (victim instanceof Player) return;
        if (victim instanceof Enemy) return;

        Player attacker = attackerAsPlayer(event.getDamager());
        if (attacker != null) {
            if (isBypassing(attacker)) return;
            if (!isProtectedEntity(victim)) return;
            if (claims.canHurtEntities(attacker.getUniqueId(), victim.getLocation())) return;
            denyMessage(attacker);
            event.setCancelled(true);
            return;
        }

        UUID tntActor = resolveExplosionActor(event.getDamager());
        if (tntActor != null) {
            if (!isProtectedEntity(victim)) return;
            if (claims.canHurtEntities(tntActor, victim.getLocation())) return;
            if (claims.isClaimed(victim.getLocation())) event.setCancelled(true);
            return;
        }

        if (claims.isClaimed(victim.getLocation()) && isProtectedEntity(victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = attackerAsPlayer(event.getRemover());
        if (player != null) {
            if (denyBuild(player, event.getEntity().getLocation())) event.setCancelled(true);
            return;
        }
        UUID tntActor = resolveExplosionActor(event.getRemover());
        if (tntActor != null) {
            if (claims.canBuild(tntActor, event.getEntity().getLocation())) return;
            if (claims.isClaimed(event.getEntity().getLocation())) event.setCancelled(true);
            return;
        }
        if (claims.isClaimed(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (denyBuild(player, event.getEntity().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (denyBuild(event.getPlayer(), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player player = attackerAsPlayer(event.getAttacker());
        if (player != null) {
            if (denyBuild(player, event.getVehicle().getLocation())) event.setCancelled(true);
            return;
        }
        UUID tntActor = resolveExplosionActor(event.getAttacker());
        if (tntActor != null) {
            if (claims.canBuild(tntActor, event.getVehicle().getLocation())) return;
            if (claims.isClaimed(event.getVehicle().getLocation())) event.setCancelled(true);
            return;
        }
        if (claims.isClaimed(event.getVehicle().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player player = attackerAsPlayer(event.getAttacker());
        if (player != null) {
            if (denyBuild(player, event.getVehicle().getLocation())) event.setCancelled(true);
            return;
        }
        UUID tntActor = resolveExplosionActor(event.getAttacker());
        if (tntActor != null) {
            if (claims.canBuild(tntActor, event.getVehicle().getLocation())) return;
            if (claims.isClaimed(event.getVehicle().getLocation())) event.setCancelled(true);
            return;
        }
        if (claims.isClaimed(event.getVehicle().getLocation())) {
            event.setCancelled(true);
        }
    }



    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntSpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof TNTPrimed tnt)) return;

        UUID fromSource = actorFromEntity(tnt.getSource());
        if (fromSource != null) {
            tagTnt(tnt, fromSource);
            return;
        }

        Location loc = tnt.getLocation();
        UUID placer = takePlacedTntNear(loc);
        if (placer != null) {
            tagTnt(tnt, placer);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        UUID actor = resolveExplosionActor(event.getEntity());
        stripClaimedBlocks(event.blockList().iterator(), actor);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        stripClaimedBlocks(event.blockList().iterator(), null);
    }

    private void stripClaimedBlocks(Iterator<Block> it, UUID actor) {
        while (it.hasNext()) {
            Block block = it.next();
            Location loc = block.getLocation();
            if (!claims.isClaimed(loc)) continue;
            if (actor != null && claims.canBuild(actor, loc)) continue;
            it.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!claims.isClaimed(loc)) return;
        Player player = event.getPlayer();
        if (player != null) {
            if (denyBuild(player, loc)) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (claims.isClaimed(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!claims.isClaimed(event.getBlock().getLocation())) return;
        Player player = event.getEntity() instanceof Player p ? p : null;
        if (player != null) {
            if (denyBuild(player, event.getBlock().getLocation())) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesForeignClaim(event.getBlock(), event.getBlocks(), event.getDirection().getModX(),
                event.getDirection().getModZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesForeignClaim(event.getBlock(), event.getBlocks(), event.getDirection().getModX(),
                event.getDirection().getModZ())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonTouchesForeignClaim(Block piston, Iterable<Block> moved, int dx, int dz) {
        String world = piston.getWorld().getName();
        int px = piston.getX() >> 4;
        int pz = piston.getZ() >> 4;
        for (Block block : moved) {
            int bx = block.getX() >> 4;
            int bz = block.getZ() >> 4;
            if (!claims.sameOwner(world, px, pz, bx, bz)) return true;
            int tx = (block.getX() + dx) >> 4;
            int tz = (block.getZ() + dz) >> 4;
            if (!claims.sameOwner(world, px, pz, tx, tz)) return true;
        }
        int hx = (piston.getX() + dx) >> 4;
        int hz = (piston.getZ() + dz) >> 4;
        return !claims.sameOwner(world, px, pz, hx, hz);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();
        String world = from.getWorld().getName();
        if (!claims.sameOwner(world, from.getX() >> 4, from.getZ() >> 4,
                to.getX() >> 4, to.getZ() >> 4)) {
            if (claims.isClaimed(to.getLocation())) {
                event.setCancelled(true);
            }
        }
    }



    private static String blockKey(Block b) {
        return b.getWorld().getName() + ':' + b.getX() + ':' + b.getY() + ':' + b.getZ();
    }

    private void prunePlacedTnt() {
        long now = System.currentTimeMillis();
        placedTnt.entrySet().removeIf(e -> now - e.getValue().at() > TNT_PLACE_TTL_MS);
    }

    private UUID takePlacedTntNear(Location loc) {
        prunePlacedTnt();
        Block base = loc.getBlock();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = base.getRelative(dx, dy, dz);
                    PlacedTnt placed = placedTnt.remove(blockKey(b));
                    if (placed != null) return placed.placer();
                }
            }
        }
        return null;
    }

    private void tagTnt(Entity entity, UUID source) {
        entity.setMetadata(META_TNT_SOURCE, new FixedMetadataValue(plugin, source.toString()));
    }

    private UUID readTntTag(Entity entity) {
        if (entity == null || !entity.hasMetadata(META_TNT_SOURCE)) return null;
        for (MetadataValue meta : entity.getMetadata(META_TNT_SOURCE)) {
            if (meta.getOwningPlugin() != plugin) continue;
            try {
                return UUID.fromString(meta.asString());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private UUID actorFromEntity(Entity entity) {
        if (entity == null) return null;
        if (entity instanceof Player p) return p.getUniqueId();
        UUID tagged = readTntTag(entity);
        if (tagged != null) return tagged;
        if (entity instanceof TNTPrimed tnt) {
            return actorFromEntity(tnt.getSource());
        }
        if (entity instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p.getUniqueId();
        }
        return null;
    }

    private UUID resolveExplosionActor(Entity entity) {
        return actorFromEntity(entity);
    }



    private static boolean isProtectedEntity(Entity entity) {
        if (entity instanceof Player) return false;
        if (entity instanceof Enemy) return false;
        if (entity instanceof Hanging) return true;
        if (entity instanceof ArmorStand) return true;
        if (entity instanceof Vehicle) return true;
        if (entity instanceof Tameable) return true;
        if (entity instanceof org.bukkit.entity.Animals) return true;
        if (entity instanceof org.bukkit.entity.NPC) return true;
        if (entity instanceof org.bukkit.entity.Golem) return true;
        EntityType type = entity.getType();
        String name = type.name();
        return name.contains("VILLAGER")
                || name.contains("ALLAY")
                || name.contains("FRAME")
                || name.contains("PAINTING")
                || name.contains("BOAT")
                || name.contains("MINECART");
    }

    private static boolean isContainerBlock(Block block) {
        BlockState state = block.getState();
        if (state instanceof Container) return true;
        Material type = block.getType();
        return type == Material.LECTERN
                || type == Material.CHISELED_BOOKSHELF
                || type == Material.DECORATED_POT
                || type == Material.JUKEBOX
                || type == Material.BEACON
                || type == Material.RESPAWN_ANCHOR
                || type.name().contains("SHULKER_BOX")
                || type.name().endsWith("_CHEST")
                || type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.BARREL
                || type == Material.HOPPER
                || type == Material.DROPPER
                || type == Material.DISPENSER
                || type.name().contains("FURNACE")
                || type == Material.SMOKER
                || type == Material.BLAST_FURNACE
                || type == Material.BREWING_STAND
                || type == Material.CRAFTER;
    }

    private static boolean isAccessBlock(Material type) {
        if (Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type)
                || Tag.BUTTONS.isTagged(type) || Tag.BEDS.isTagged(type)) {
            return true;
        }
        String name = type.name();
        return type == Material.LEVER
                || type == Material.REPEATER
                || type == Material.COMPARATOR
                || type == Material.DAYLIGHT_DETECTOR
                || type == Material.NOTE_BLOCK
                || type == Material.BELL
                || type == Material.CAKE
                || type == Material.SWEET_BERRY_BUSH
                || type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT
                || name.contains("PRESSURE_PLATE")
                || name.contains("CANDLE")
                || type == Material.DRAGON_EGG;
    }

    private static boolean isBuildTool(Material hand) {
        return hand == Material.FLINT_AND_STEEL
                || hand == Material.FIRE_CHARGE
                || hand == Material.END_CRYSTAL
                || hand == Material.TNT
                || hand == Material.TNT_MINECART
                || hand == Material.ARMOR_STAND
                || hand == Material.ITEM_FRAME
                || hand == Material.GLOW_ITEM_FRAME
                || hand == Material.PAINTING
                || hand == Material.MINECART
                || hand == Material.CHEST_MINECART
                || hand == Material.HOPPER_MINECART
                || hand == Material.FURNACE_MINECART
                || hand == Material.COMMAND_BLOCK_MINECART
                || hand.name().contains("BOAT")
                || hand == Material.BUCKET
                || hand == Material.WATER_BUCKET
                || hand == Material.LAVA_BUCKET
                || hand == Material.POWDER_SNOW_BUCKET
                || hand == Material.AXOLOTL_BUCKET
                || hand == Material.TADPOLE_BUCKET
                || hand == Material.COD_BUCKET
                || hand == Material.SALMON_BUCKET
                || hand == Material.PUFFERFISH_BUCKET
                || hand == Material.TROPICAL_FISH_BUCKET
                || hand.name().endsWith("_SPAWN_EGG")
                || hand == Material.BONE_MEAL;
    }

    private static boolean isBuildItem(Material hand) {
        return isBuildTool(hand);
    }
}
