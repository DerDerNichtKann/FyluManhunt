package fylu.fyluManhunt.commands;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandWorldReset implements CommandExecutor {
    private final FyluManhunt plugin;

    public CommandWorldReset(FyluManhunt plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("manhunt.reset")) return true;

        if (args.length > 0) {
            // Seed setzen
            plugin.getWorldManager().setNextSeed(args[0]);
        }

        // Reset ausführen (Nutzt den gesetzten Seed oder Zufall)
        plugin.getWorldManager().resetWorlds();
        sender.sendMessage(ChatColor.GREEN + "Welt Reset eingeleitet!");
        return true;
    }
}