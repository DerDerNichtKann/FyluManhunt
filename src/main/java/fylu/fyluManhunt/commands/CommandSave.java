package fylu.fyluManhunt.commands;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandSave implements CommandExecutor {

    private final FyluManhunt plugin;

    public CommandSave(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("manhunt.save")) {
            sender.sendMessage(ChatColor.RED + "Dazu hast du keine Rechte.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Benutzung: /save <name>");
            return true;
        }

        String name = args[0];
        plugin.getWorldManager().saveGame(name, sender);

        return true;
    }
}