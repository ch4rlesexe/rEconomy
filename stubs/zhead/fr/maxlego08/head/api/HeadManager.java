package fr.maxlego08.head.api;

import fr.maxlego08.head.api.enums.HeadCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/** Compile-only stub. Runtime uses the real zHead plugin. */
public interface HeadManager {
    void downloadHead(boolean force);
    void loadHeads();
    long count();
    void openCategory(Player player, HeadCategory category, int page);
    long count(HeadCategory category);
    List<Head> getHeads(HeadCategory category);
    void give(CommandSender sender, Player player, Head head, int amount);
    void give(CommandSender sender, Player player, String id, int amount);
    Optional<Head> getHead(String id);
    ItemStack createItemStack(String id);
    ItemStack createItemStack(Head head);
    Date getUpdatedAt();
    void search(Player player, String query);
    List<Head> search(String query);
    void registerPlaceholders();
    void sendInformations(CommandSender sender, Head head);
    HeadSignature getHeadSignature();
    List<Head> getHeads();
    List<String> searchHeads(String query);
    void saveHead(CommandSender sender, Head head);
}
