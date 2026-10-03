package net.ch4rlesexe.economy.frames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.ch4rlesexe.economy.REconomyPlugin;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;
import java.util.function.Consumer;

public final class FrameListener implements Listener {
    private final REconomyPlugin plugin;
    private final NamespacedKey invisibleKey;
    private final NamespacedKey waxedKey;
    private final Random random = new Random();

    public FrameListener(REconomyPlugin plugin) {
        this.plugin = plugin;
        this.invisibleKey = new NamespacedKey(plugin, "invisibleframe");
        this.waxedKey = new NamespacedKey(plugin, "waxedframe");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("frames.enabled", true);
    }

    private boolean doShearsBreak() {
        return plugin.getConfig().getBoolean("frames.doShearsBreak", true);
    }

    private boolean fixWithLeather() {
        return plugin.getConfig().getBoolean("frames.fixWithLeather", true);
    }

    private boolean enableWax() {
        return plugin.getConfig().getBoolean("frames.enableWax", true);
    }

    private boolean waxFullLock() {
        return plugin.getConfig().getBoolean("frames.waxFullLock", false);
    }

    private boolean doAxeBreak() {
        return plugin.getConfig().getBoolean("frames.doAxeBreak", true);
    }

    private boolean doLeatherConsume() {
        return plugin.getConfig().getBoolean("frames.doLeatherConsume", true);
    }

    private boolean doHoneycombConsume() {
        return plugin.getConfig().getBoolean("frames.doHoneycombConsume", true);
    }

    private FrameClickMode shearsButton() {
        return FrameClickMode.parse(plugin.getConfig().getString("frames.shearsButton"), FrameClickMode.LEFT);
    }

    private FrameClickMode leatherButton() {
        return FrameClickMode.parse(plugin.getConfig().getString("frames.leatherButton"), FrameClickMode.LEFT);
    }

    private FrameClickMode honeycombButton() {
        return FrameClickMode.parse(plugin.getConfig().getString("frames.honeycombButton"), FrameClickMode.BOTH);
    }

    private FrameClickMode axeButton() {
        return FrameClickMode.parse(plugin.getConfig().getString("frames.axeButton"), FrameClickMode.BOTH);
    }

    private String invisibleFrameName() {
        return plugin.getConfig().getString("frames.invisibleFrameName", "Invisible Item Frame");
    }

    private String invisibleGlowFrameName() {
        return plugin.getConfig().getString("frames.invisibleGlowFrameName", "Invisible Glow Item Frame");
    }

    private boolean isInvisible(ItemFrame frame) {
        return frame.getPersistentDataContainer().has(invisibleKey, PersistentDataType.BYTE);
    }

    private void tag(ItemFrame frame) {
        frame.getPersistentDataContainer().set(invisibleKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void untag(ItemFrame frame) {
        frame.getPersistentDataContainer().remove(invisibleKey);
    }

    private void syncVisibility(ItemFrame frame) {
        frame.setVisible(frame.getItem().getType() == Material.AIR);
    }

    private boolean isWaxed(ItemFrame frame) {
        return frame.getPersistentDataContainer().has(waxedKey, PersistentDataType.BYTE);
    }

    private void setWaxed(ItemFrame frame) {
        frame.getPersistentDataContainer().set(waxedKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void unsetWaxed(ItemFrame frame) {
        frame.getPersistentDataContainer().remove(waxedKey);
    }

    private static boolean isAxe(Material material) {
        return material.name().endsWith("_AXE");
    }

    private boolean consumesDurability(int unbreakingLevel) {
        return unbreakingLevel <= 0 || random.nextInt(unbreakingLevel + 1) == 0;
    }

    private void damageTool(ItemStack item) {
        Enchantment unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        int level = unbreaking != null ? item.getEnchantmentLevel(unbreaking) : 0;
        if (!consumesDurability(level)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int next = damageable.getDamage() + 1;
            if (next >= item.getType().getMaxDurability()) {
                item.setAmount(item.getAmount() - 1);
            } else {
                damageable.setDamage(next);
                item.setItemMeta(meta);
            }
        }
    }

    private void restore(ItemFrame frame) {
        untag(frame);
        frame.setVisible(true);
        effects(frame, Sound.ENTITY_ITEM_FRAME_PLACE, Particle.CRIT, 10, 0.3, SoundCategory.MASTER);
    }

    private void doShear(ItemFrame frame, Player player, ItemStack hand) {
        if (player.getGameMode() != GameMode.CREATIVE && doShearsBreak()) {
            damageTool(hand);
        }
        tag(frame);
        syncVisibility(frame);
        effects(frame, Sound.ENTITY_SNOW_GOLEM_SHEAR, Particle.CLOUD, 3, 0.0, SoundCategory.MASTER);
    }

    private void doRestore(ItemFrame frame, Player player, ItemStack hand) {
        if (player.getGameMode() != GameMode.CREATIVE && doLeatherConsume()) {
            hand.setAmount(hand.getAmount() - 1);
        }
        restore(frame);
    }

    private void doWax(ItemFrame frame, Player player, ItemStack hand) {
        if (player.getGameMode() != GameMode.CREATIVE && doHoneycombConsume()) {
            hand.setAmount(hand.getAmount() - 1);
        }
        setWaxed(frame);
        effects(frame, Sound.ITEM_HONEYCOMB_WAX_ON, Particle.WAX_ON, 7, 0.2, SoundCategory.BLOCKS);
    }

    private void doUnwax(ItemFrame frame, Player player, ItemStack hand) {
        if (player.getGameMode() != GameMode.CREATIVE && doAxeBreak()) {
            damageTool(hand);
        }
        unsetWaxed(frame);
        effects(frame, Sound.ITEM_AXE_WAX_OFF, Particle.WAX_OFF, 7, 0.2, SoundCategory.BLOCKS);
    }

    private boolean tryTool(ItemFrame frame, Player player, Consumer<Void> cancel) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.SHEARS && !isInvisible(frame) && !isWaxed(frame)
                && shearsButton().allowsLeft() && player.hasPermission("reconomy.frames.shear")) {
            cancel.accept(null);
            doShear(frame, player, hand);
            return true;
        }
        if (hand.getType() == Material.LEATHER && isInvisible(frame) && !isWaxed(frame)
                && fixWithLeather() && leatherButton().allowsLeft()
                && player.hasPermission("reconomy.frames.restore")) {
            cancel.accept(null);
            doRestore(frame, player, hand);
            return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        Player player = event.getDamager() instanceof Player p ? p : null;
        if (player != null) {
            if (tryTool(frame, player, v -> event.setCancelled(true))) {
                return;
            }
            ItemStack lmbHand = player.getInventory().getItemInMainHand();
            if (enableWax() && honeycombButton().allowsLeft()
                    && lmbHand.getType() == Material.HONEYCOMB && !isWaxed(frame)
                    && frame.getItem().getType() != Material.AIR
                    && player.hasPermission("reconomy.frames.wax")) {
                event.setCancelled(true);
                doWax(frame, player, lmbHand);
                return;
            }
            if (isInvisible(frame) && frame.getItem().getType() != Material.AIR) {
                frame.setVisible(true);
            }
        }
        boolean byPlayer = player != null
                || (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player);
        if (byPlayer && enableWax() && isWaxed(frame) && frame.getItem().getType() != Material.AIR) {
            unsetWaxed(frame);
            effects(frame, Sound.ITEM_AXE_WAX_OFF, Particle.WAX_OFF, 7, 0.2, SoundCategory.BLOCKS);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(HangingBreakByEntityEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        if (!(event.getRemover() instanceof Player player)) {
            return;
        }
        if (tryTool(frame, player, v -> event.setCancelled(true))) {
            return;
        }
        if (!fixWithLeather() || !isInvisible(frame) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        event.setCancelled(true);
        dropInvisibleFrameItem(frame);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreakPhysics(HangingBreakEvent event) {
        if (!enabled() || event instanceof HangingBreakByEntityEvent) {
            return;
        }
        if (event.getCause() != HangingBreakEvent.RemoveCause.PHYSICS) {
            return;
        }
        if (!(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        if (!fixWithLeather() || !isInvisible(frame)) {
            return;
        }
        event.setCancelled(true);
        dropInvisibleFrameItem(frame);
    }

    private void dropInvisibleFrameItem(ItemFrame frame) {
        var world = frame.getWorld();
        var loc = frame.getLocation();
        ItemStack content = frame.getItem();
        if (content.getType() != Material.AIR) {
            world.dropItemNaturally(loc, content);
        }
        boolean glow = frame instanceof GlowItemFrame;
        ItemStack drop = new ItemStack(glow ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME);
        ItemMeta meta = drop.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(invisibleKey, PersistentDataType.BYTE, (byte) 1);
            setFrameName(meta, glow ? invisibleGlowFrameName() : invisibleFrameName());
            Enchantment glint = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (glint != null) {
                meta.addEnchant(glint, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            drop.setItemMeta(meta);
        }
        world.dropItemNaturally(loc, drop);
        frame.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!enabled() || !(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }
        ItemStack rmbHand = event.getPlayer().getInventory().getItemInMainHand();
        if (rmbHand.getType() == Material.SHEARS && !isInvisible(frame) && !isWaxed(frame)
                && shearsButton().allowsRight() && !event.getPlayer().isSneaking()
                && event.getPlayer().hasPermission("reconomy.frames.shear")) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                doShear(frame, event.getPlayer(), rmbHand);
            }
            return;
        }
        if (rmbHand.getType() == Material.LEATHER && isInvisible(frame) && !isWaxed(frame)
                && fixWithLeather() && leatherButton().allowsRight() && !event.getPlayer().isSneaking()
                && event.getPlayer().hasPermission("reconomy.frames.restore")) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                doRestore(frame, event.getPlayer(), rmbHand);
            }
            return;
        }

        if (enableWax()) {
            ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
            boolean waxed = isWaxed(frame);
            if (waxed && isAxe(held.getType()) && axeButton().allowsRight()
                    && event.getPlayer().hasPermission("reconomy.frames.unwax")) {
                event.setCancelled(true);
                doUnwax(frame, event.getPlayer(), held);
                return;
            }
            if (!waxed && held.getType() == Material.HONEYCOMB && frame.getItem().getType() != Material.AIR
                    && honeycombButton().allowsRight()
                    && event.getPlayer().hasPermission("reconomy.frames.wax")) {
                event.setCancelled(true);
                doWax(frame, event.getPlayer(), held);
                return;
            }
            if (waxed) {
                event.setCancelled(true);
                if (event.getHand() == EquipmentSlot.HAND) {
                    deniedSound(frame);
                }
                return;
            }
        }

        if (!isInvisible(frame) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (frame.getItem().getType() == Material.AIR && hand.getType() != Material.AIR) {
            frame.setVisible(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageFullLock(EntityDamageEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        if (!(enableWax() && waxFullLock() && isWaxed(frame))) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player damager
                && axeButton().allowsLeft()
                && damager.hasPermission("reconomy.frames.unwax")) {
            ItemStack hand = damager.getInventory().getItemInMainHand();
            if (isAxe(hand.getType())) {
                event.setCancelled(true);
                doUnwax(frame, damager, hand);
                return;
            }
        }
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player) {
            deniedSound(frame);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreakFullLock(HangingBreakEvent event) {
        if (!enabled() || event.getCause() == HangingBreakEvent.RemoveCause.PHYSICS) {
            return;
        }
        if (!(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        if (!(enableWax() && waxFullLock() && isWaxed(frame))) {
            return;
        }
        event.setCancelled(true);
        if (event instanceof HangingBreakByEntityEvent byEntity && byEntity.getRemover() instanceof Player) {
            deniedSound(frame);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(HangingPlaceEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        ItemStack item = event.getItemStack();
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(invisibleKey, PersistentDataType.BYTE)) {
            return;
        }
        tag(frame);
        syncVisibility(frame);
    }

    private void setFrameName(ItemMeta meta, String name) {
        meta.displayName(Component.text(name)
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.WHITE));
    }

    private void effects(ItemFrame frame, Sound sound, Particle particle, int count, double spread, SoundCategory category) {
        var loc = frame.getLocation();
        frame.getWorld().playSound(loc, sound, category, 1f, 1.5f);
        frame.getWorld().spawnParticle(particle, loc, count, spread, spread, spread, 0.1);
    }

    private void deniedSound(ItemFrame frame) {
        frame.getWorld().playSound(frame.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.NEUTRAL, 0.8f, 1.5f);
    }
}
