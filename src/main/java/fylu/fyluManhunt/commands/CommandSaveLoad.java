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

public class CommandSaveLoad implements CommandExecutor, TabCompleter {

    private final FyluManhunt plugin;

    public CommandSaveLoad(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("manhunt.save")) return true;

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /save <new|load> <name>");
            return true;
        }

        String action = args[0];
        String name = args[1];

        if (action.equalsIgnoreCase("new")) {
            plugin.getWorldManager().saveGame(name, sender);
        } else if (action.equalsIgnoreCase("load")) {
            plugin.getWorldManager().loadGame(name, sender);
        } else {
            sender.sendMessage(ChatColor.RED + "Unbekannte Aktion. Nutze 'new' oder 'load'.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("new", "load");
        if (args.length == 2 && args[0].equalsIgnoreCase("load")) {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists() || !backupDir.isDirectory()) return new ArrayList<>();
            String[] files = backupDir.list();
            if(files != null) return Arrays.asList(files);
        }
        return new ArrayList<>();
    }
}