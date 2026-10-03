package net.ch4rlesexe.economy.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

public final class ColorFormatListener implements Listener {
    public static final String PERMISSION = "reconomy.color";

    private static final LegacyComponentSerializer AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!event.getPlayer().hasPermission(PERMISSION)) return;
        for (int i = 0; i < 4; i++) {
            Component line = event.line(i);
            if (line == null) continue;
            String raw = PLAIN.serialize(line);
            if (raw.indexOf('&') < 0) continue;
            event.line(i, AMPERSAND.deserialize(raw));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (!player.hasPermission(PERMISSION)) return;
        if (!(event.getView() instanceof AnvilView anvil)) return;

        String rename = anvil.getRenameText();
        if (rename == null || rename.indexOf('&') < 0) return;

        ItemStack result = event.getResult();
        if (result == null) return;

        ItemStack colored = result.clone();
        ItemMeta meta = colored.getItemMeta();
        if (meta == null) return;
        meta.displayName(AMPERSAND.deserialize(rename));
        colored.setItemMeta(meta);
        event.setResult(colored);
    }
}
