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
        if (!sender.hasPermission("manhunt.load")) {
            plugin.getLogger().info(ChatColor.RED + "Dazu hast du keine Rechte.");
            return true;
        }

        String name;

        if (args.length == 0) {
            name = getLatestSaveName();
            if (name == null) {
                plugin.getLogger().info(ChatColor.RED + "Keine Backups gefunden, die geladen werden können.");
                return true;
            }
            plugin.getLogger().info(ChatColor.YELLOW + "Kein Name angegeben. Lade neuesten Save: " + ChatColor.GOLD + name);
        } else {
            name = args[0];
        }

        File backupDir = new File(plugin.getDataFolder(), "backups/" + name);
        if (!backupDir.exists()) {
            plugin.getLogger().info(ChatColor.RED + "Backup '" + name + "' existiert nicht!");
            return true;
        }

        plugin.getLogger().info(ChatColor.GREEN + "Backup '" + name + "' vorbereitet.");
        plugin.getLogger().info(ChatColor.RED + "SERVER WIRD NEU GESTARTET UM DATEN ZU LADEN...");
        plugin.getWorldManager().scheduleRestartAndLoad(name);

        return true;
    }

    private String getLatestSaveName() {
        File backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            return null;
        }

        File[] files = backupDir.listFiles(File::isDirectory);
        if (files == null || files.length == 0) {
            return null;
        }

        File newestFile = null;
        long lastModifiedTime = Long.MIN_VALUE;

        for (File file : files) {
            if (file.lastModified() > lastModifiedTime) {
                lastModifiedTime = file.lastModified();
                newestFile = file;
            }
        }

        return (newestFile != null) ? newestFile.getName() : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists() || !backupDir.isDirectory()) return new ArrayList<>();
            String[] files = backupDir.list();
            if (files != null) return Arrays.asList(files);
        }
        return new ArrayList<>();
    }
}