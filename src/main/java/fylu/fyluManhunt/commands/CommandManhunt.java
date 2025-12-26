package fylu.fyluManhunt.commands;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandManhunt implements CommandExecutor {

    private final FyluManhunt plugin;

    public CommandManhunt(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (label.equalsIgnoreCase("manhuntstart")) {
            if (!sender.hasPermission("manhunt.start")) return true;
            plugin.getGameManager().startGame();
            return true;
        }

        if (label.equalsIgnoreCase("secondrunner")) {
            if (!sender.hasPermission("manhunt.setrunner")) return true;
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Benutzung: /secondrunner <Spieler>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            plugin.getGameManager().addRunner(target);
            sender.sendMessage(ChatColor.GREEN + target.getName() + " ist nun auch Runner.");
            return true;
        }

        if (label.equalsIgnoreCase("timer")) {
            if (!sender.hasPermission("manhunt.timer")) return true;
            if (args.length == 1) {
                boolean state = args[0].equalsIgnoreCase("on");
                plugin.getGameManager().toggleTimerVisibility();
                sender.sendMessage(ChatColor.YELLOW + "Timer Sichtbarkeit umgeschaltet.");
            }
            return true;
        }

        if (label.equalsIgnoreCase("tabhearts")) {
            if (!sender.hasPermission("manhunt.hearts")) return true;
            if (args.length == 1) {
                sender.sendMessage(ChatColor.YELLOW + "Tab-Herzen werden über TAB-Config gesteuert.");
            }
            return true;
        }

        if (label.equalsIgnoreCase("activatorbar")) {
            if (!sender.hasPermission("manhunt.activator")) return true;
            if (args.length == 1) {
                boolean off = args[0].equalsIgnoreCase("off");
                plugin.getGameManager().setActivatorDisabled(off);
                sender.sendMessage(ChatColor.YELLOW + "Activator Bar (Gamerules) update: " + (off ? "Deaktiviert" : "Aktiviert"));
            }
            return true;
        }

        return false;
    }
}