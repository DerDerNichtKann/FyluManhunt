package fylu.fyluManhunt.commands;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;

public class CommandGame implements CommandExecutor {
    private final FyluManhunt plugin;

    public CommandGame(FyluManhunt plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("pause") || label.equalsIgnoreCase("unpause")) {
            if(!plugin.getGameManager().isGameRunning()) {
                plugin.getLogger().info(ChatColor.RED + "Spiel läuft nicht.");
                return true;
            }
            plugin.getGameManager().togglePause();
            return true;
        }
        if (label.equalsIgnoreCase("vorsprung")) {
            if(args.length == 1) {
                try {
                    int sek = Integer.parseInt(args[0]);
                    plugin.getGameManager().setHeadStartSeconds(sek);
                    plugin.getLogger().info("§aVorsprung auf " + sek + "s gesetzt.");
                } catch(NumberFormatException e) {
                    plugin.getLogger().info("§cBitte Zahl angeben.");
                }
            }
            return true;
        }
        return false;
    }
}