package fylu.fyluManhunt.commands;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandTools implements CommandExecutor {

    private final FyluManhunt plugin;

    public CommandTools(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (label.equalsIgnoreCase("TrackingCompass")) {
            if (!(sender instanceof Player)) {
                plugin.getLogger().info(ChatColor.RED + "Nur für Spieler.");
                return true;
            }

            if (!plugin.getGameManager().isGameRunning()) {
                plugin.getLogger().info(ChatColor.RED + "Das Spiel läuft derzeit nicht.");
                return true;
            }

            Player p = (Player) sender;
            if (plugin.getGameManager().isRunner(p)) {
                plugin.getLogger().info(ChatColor.RED + "Runner brauchen keinen Tracker!");
                return true;
            }

            plugin.getCompassManager().giveCompass(p);
            plugin.getLogger().info(ChatColor.GREEN + "Kompass erhalten.");
            return true;
        }

        if (label.equalsIgnoreCase("setseed")) {
            if (!sender.hasPermission("manhunt.reset")) return true;
            if (args.length != 1) {
                plugin.getLogger().info(ChatColor.RED + "Nutzung: /setseed <seed>");
                return true;
            }

            plugin.getWorldManager().setNextSeed(args[0]);
            plugin.getLogger().info(ChatColor.GREEN + "Seed für nächsten Reset gesetzt auf: " + ChatColor.YELLOW + args[0]);
            return true;
        }

        if (label.equalsIgnoreCase("bedbomb")) {
            if (!sender.hasPermission("manhunt.bedbomb")) return true;
            if (args.length != 2) {
                plugin.getLogger().info(ChatColor.RED + "Nutzung: /bedbomb <nether|end> <true|false>");
                return true;
            }

            String dim = args[0].toLowerCase();
            boolean state = Boolean.parseBoolean(args[1]);

            if (dim.equals("nether")) {
                plugin.getGameManager().setBedbombNether(state);
                plugin.getLogger().info(ChatColor.GREEN + "Bedbomb Nether: " + state);
            } else if (dim.equals("end")) {
                plugin.getGameManager().setBedbombEnd(state);
                plugin.getLogger().info(ChatColor.GREEN + "Bedbomb End: " + state);
            }
            return true;
        }

        return false;
    }
}