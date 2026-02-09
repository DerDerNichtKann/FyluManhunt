package fylu.fyluManhunt.commands;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;

public class CommandSave implements CommandExecutor {

    private final FyluManhunt plugin;

    public CommandSave(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("manhunt.save")) {
            plugin.getLogger().info(ChatColor.RED + "Dazu hast du keine Rechte.");
            return true;
        }

        String saveName;

        if (args.length == 0) {
            saveName = getNextSaveNumber();
            plugin.getLogger().info("Kein Name angegeben. Automatischer Save-Name: " + saveName);
        } else {
            saveName = args[0];
            plugin.getLogger().info("Manueller Save-Name gewählt: " + saveName);
        }
        plugin.getWorldManager().saveGame(saveName, Bukkit.getConsoleSender());

        return true;
    }

    private String getNextSaveNumber() {
        File backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists()) {
            return "1";
        }

        File[] files = backupDir.listFiles(File::isDirectory);
        if (files == null || files.length == 0) {
            return "1";
        }

        int maxNumber = 0;
        for (File file : files) {
            try {
                int currentNumber = Integer.parseInt(file.getName());
                if (currentNumber > maxNumber) {
                    maxNumber = currentNumber;
                }
            } catch (NumberFormatException e) {
            }
        }

        return String.valueOf(maxNumber + 1);
    }
}