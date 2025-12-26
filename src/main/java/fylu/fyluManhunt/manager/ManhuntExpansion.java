package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ManhuntExpansion extends PlaceholderExpansion {

    private final FyluManhunt plugin;

    public ManhuntExpansion(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "fylumanhunt"; }

    @Override
    public @NotNull String getAuthor() { return "Fylu"; }

    @Override
    public @NotNull String getVersion() { return "1.0"; }

    @Override
    public boolean persist() { return true; }

    public String onPlaceholderRequest(Player one, Player two, String params) {
        if (one == null || two == null) return "";

        if (params.equalsIgnoreCase("hp")) {
            if (plugin.getGameManager().isRunner(one)) return "";

            if (plugin.getGameManager().isRunner(two)) {
                return " " + ChatColor.RED + (int)two.getHealth() + "❤";
            }
            return "";
        }
        return null;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        return "";
    }
}