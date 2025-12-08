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

        // --- /TrackingCompass ---
        if (label.equalsIgnoreCase("TrackingCompass")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Nur für Spieler.");
                return true;
            }

            if (!plugin.getGameManager().isGameRunning()) {
                sender.sendMessage(ChatColor.RED + "Das Spiel läuft derzeit nicht.");
                return true;
            }

            // Runner brauchen keinen
            Player p = (Player) sender;
            if (plugin.getGameManager().isRunner(p)) {
                sender.sendMessage(ChatColor.RED + "Runner brauchen keinen Tracker!");
                return true;
            }

            plugin.getCompassManager().giveCompass(p);
            sender.sendMessage(ChatColor.GREEN + "Kompass erhalten.");
            return true;
        }

        // --- /setseed ---
        if (label.equalsIgnoreCase("setseed")) {
            if (!sender.hasPermission("manhunt.reset")) return true;
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Nutzung: /setseed <seed>");
                return true;
            }

            plugin.getWorldManager().setNextSeed(args[0]);
            sender.sendMessage(ChatColor.GREEN + "Seed für nächsten Reset gesetzt auf: " + ChatColor.YELLOW + args[0]);
            return true;
        }

        // --- /bedbomb ---
        if (label.equalsIgnoreCase("bedbomb")) {
            if (!sender.hasPermission("manhunt.bedbomb")) return true;
            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Nutzung: /bedbomb <nether|end> <true|false>");
                return true;
            }

            String dim = args[0].toLowerCase();
            boolean state = Boolean.parseBoolean(args[1]);

            if (dim.equals("nether")) {
                plugin.getGameManager().setBedbombNether(state);
                sender.sendMessage(ChatColor.GREEN + "Bedbomb Nether: " + state);
            } else if (dim.equals("end")) {
                plugin.getGameManager().setBedbombEnd(state);
                sender.sendMessage(ChatColor.GREEN + "Bedbomb End: " + state);
            }
            return true;
        }

        return false;
    }
}