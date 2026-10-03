package net.ch4rlesexe.economy.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.ch4rlesexe.economy.REconomyPlugin;
import net.ch4rlesexe.economy.team.TeamService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class REconomyPlaceholders extends PlaceholderExpansion {
    private final REconomyPlugin plugin;
    private final TeamService teams;

    public REconomyPlaceholders(REconomyPlugin plugin, TeamService teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "reconomy";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ch4rlesexe";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        var team = teams.getByPlayer(player.getUniqueId());
        return switch (params.toLowerCase()) {
            case "team_tag", "tag" -> team.map(t -> "[" + t.tag() + "]").orElse("");
            case "team_tag_spaced" -> team.map(t -> " &7[" + t.tag() + "]&r").orElse("");
            case "team_name", "name" -> team.map(TeamService.Team::name).orElse("");
            case "team" -> team.map(t -> t.name() + " [" + t.tag() + "]").orElse("");
            default -> null;
        };
    }
}
