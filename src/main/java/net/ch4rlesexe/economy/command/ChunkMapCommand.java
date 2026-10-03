package net.ch4rlesexe.economy.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.ch4rlesexe.economy.claim.ClaimService;
import net.ch4rlesexe.economy.team.TeamService;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ChunkMapCommand implements CommandExecutor {
    private static final int RADIUS = 4;

    private static final TextColor[] TEAM_COLORS = {
            NamedTextColor.RED,
            NamedTextColor.GOLD,
            NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.AQUA,
            NamedTextColor.YELLOW,
            NamedTextColor.BLUE,
            NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_PURPLE,
            NamedTextColor.DARK_RED,
            NamedTextColor.GREEN
    };

    private final ClaimService claims;
    private final TeamService teams;

    public ChunkMapCommand(ClaimService claims, TeamService teams) {
        this.claims = claims;
        this.teams = teams;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Chunk center = player.getLocation().getChunk();
        String world = center.getWorld().getName();
        int cx = center.getX();
        int cz = center.getZ();
        Long myTeam = teams.getByPlayer(player.getUniqueId()).map(TeamService.Team::id).orElse(null);
        Map<Long, TeamStyle> styles = new LinkedHashMap<>();

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text()
                .append(Component.text("Claim Map", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("N ↑", NamedTextColor.WHITE))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("+", NamedTextColor.GREEN))
                .append(Component.text(" you  ", NamedTextColor.GRAY))
                .append(Component.text(".", NamedTextColor.DARK_GRAY))
                .append(Component.text(" wild  ", NamedTextColor.GRAY))
                .append(Component.text("#", NamedTextColor.GREEN))
                .append(Component.text(" yours  ", NamedTextColor.GRAY))
                .append(Component.text("A", NamedTextColor.GOLD))
                .append(Component.text(" others", NamedTextColor.GRAY))
                .build());
        player.sendMessage(Component.text("Hover a tile for owner · coords", NamedTextColor.DARK_GRAY));

        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            TextComponent.Builder row = Component.text().content("  ");
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int x = cx + dx;
                int z = cz + dz;
                boolean here = dx == 0 && dz == 0;
                Optional<ClaimService.Claim> claim = claims.get(world, x, z);

                String owner;
                TextColor color;
                char glyph;

                if (claim.isEmpty()) {
                    owner = "Wilderness";
                    color = NamedTextColor.DARK_GRAY;
                    glyph = '.';
                } else {
                    long teamId = claim.get().teamId();
                    TeamStyle style = styles.computeIfAbsent(teamId, id -> newStyle(id, styles.size()));
                    boolean mine = myTeam != null && myTeam == teamId;
                    owner = mine ? "Your team · " + style.name() : style.name();
                    color = mine ? NamedTextColor.GREEN : style.color();
                    glyph = mine ? '#' : style.letter();
                }

                if (here) {
                    glyph = '+';
                    color = NamedTextColor.GREEN;
                }

                row.append(tile(glyph, color, here, owner, world, x, z));

                row.append(Component.text(" "));
            }
            player.sendMessage(row.build());
        }

        boolean anyForeign = styles.keySet().stream()
                .anyMatch(id -> myTeam == null || !myTeam.equals(id));
        if (anyForeign) {
            player.sendMessage(Component.text("Teams nearby", NamedTextColor.DARK_GRAY));
            for (Map.Entry<Long, TeamStyle> e : styles.entrySet()) {
                if (myTeam != null && myTeam.equals(e.getKey())) continue;
                TeamStyle s = e.getValue();
                player.sendMessage(Component.text()
                        .append(Component.text("  " + s.letter() + "  ", s.color()))
                        .append(Component.text(s.name(), NamedTextColor.WHITE))
                        .build());
            }
        }

        Optional<ClaimService.Claim> standing = claims.get(world, cx, cz);
        Component where;
        if (standing.isEmpty()) {
            where = Component.text("Wilderness", NamedTextColor.GRAY);
        } else {
            long id = standing.get().teamId();
            boolean mine = myTeam != null && myTeam == id;
            String name = teams.getById(id)
                    .map(t -> t.name() + " [" + t.tag() + "]")
                    .orElse("Team #" + id);
            where = Component.text(name, mine ? NamedTextColor.GREEN : NamedTextColor.GOLD);
        }
        player.sendMessage(Component.text()
                .append(Component.text("You are in ", NamedTextColor.DARK_GRAY))
                .append(where)
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(cx + ", " + cz, NamedTextColor.DARK_GRAY))
                .build());
        player.sendMessage(Component.empty());
        return true;
    }

    private TeamStyle newStyle(long teamId, int index) {
        String name = teams.getById(teamId)
                .map(t -> t.name() + " [" + t.tag() + "]")
                .orElse("Team #" + teamId);
        return new TeamStyle(name, TEAM_COLORS[index % TEAM_COLORS.length], (char) ('A' + (index % 26)));
    }


    private static Component tile(char glyph, TextColor color, boolean here,
                                  String owner, String world, int chunkX, int chunkZ) {
        Component tip = Component.text()
                .append(Component.text(owner, color).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(world + " · chunk " + chunkX + ", " + chunkZ, NamedTextColor.GRAY))
                .append(here
                        ? Component.newline().append(Component.text("You are here", NamedTextColor.GREEN))
                        : Component.empty())
                .build();
        return Component.text(String.valueOf(glyph), color)
                .hoverEvent(HoverEvent.showText(tip));
    }

    private record TeamStyle(String name, TextColor color, char letter) {}
}
