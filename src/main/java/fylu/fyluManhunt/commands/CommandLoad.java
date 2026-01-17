package fylu.fyluManhunt.commands;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandLoad implements CommandExecutor, TabCompleter {

    private final FyluManhunt plugin;

    public CommandLoad(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("manhunt.load")) return true;

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Benutzung: /load <name>");
            return true;
        }

        String name = args[0];
        File backupDir = new File(plugin.getDataFolder(), "backups/" + name);
        if (!backupDir.exists()) {
            sender.sendMessage(ChatColor.RED + "Backup '" + name + "' existiert nicht!");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Backup '" + name + "' vorbereitet.");
        sender.sendMessage(ChatColor.RED + "SERVER WIRD NEU GESTARTET UM DATEN ZU LADEN...");
        plugin.getWorldManager().scheduleRestartAndLoad(name);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists() || !backupDir.isDirectory()) return new ArrayList<>();
            String[] files = backupDir.list();
            if(files != null) return Arrays.asList(files);
        }
        return new ArrayList<>();
    }
}