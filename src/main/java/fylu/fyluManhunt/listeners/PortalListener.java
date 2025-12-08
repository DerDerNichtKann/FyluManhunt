package fylu.fyluManhunt.listeners;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PortalListener implements Listener {

    private final FyluManhunt plugin;

    public PortalListener(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (!plugin.getGameManager().isGameRunning()) return;

        Player p = e.getPlayer();
        World fromWorld = e.getFrom().getWorld();
        String gameWorldName = "manhunt_game";

        if (fromWorld.getName().equals("world")) return;

        if (plugin.getGameManager().isRunner(p)) {
            plugin.getCompassManager().updateLastPortalLocation(p.getUniqueId(), fromWorld.getName(), e.getFrom());
        }

        Location toLocation = null;

        if (e.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                World nether = Bukkit.getWorld(gameWorldName + "_nether");
                if (nether != null) {
                    double x = e.getFrom().getX() / 8.0;
                    double z = e.getFrom().getZ() / 8.0;
                    toLocation = new Location(nether, x, e.getFrom().getY(), z);
                }
            } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
                World overworld = Bukkit.getWorld(gameWorldName);
                if (overworld != null) {
                    double x = e.getFrom().getX() * 8.0;
                    double z = e.getFrom().getZ() * 8.0;
                    toLocation = new Location(overworld, x, e.getFrom().getY(), z);
                }
            }
        }

        if (e.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                World end = Bukkit.getWorld(gameWorldName + "_the_end");
                if (end != null) {
                    toLocation = new Location(end, 100, 49, 0);
                    createEndPlatform(toLocation);
                }
            } else if (fromWorld.getEnvironment() == World.Environment.THE_END) {
                World overworld = Bukkit.getWorld(gameWorldName);
                if (overworld != null) {
                    toLocation = overworld.getSpawnLocation();
                }
            }
        }

        if (toLocation != null) {
            e.setTo(toLocation);
            e.setCanCreatePortal(true);
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent e) {
        if (!plugin.getGameManager().isGameRunning()) return;
        if (!(e.getEntity() instanceof EnderPearl)) return;

        World fromWorld = e.getFrom().getWorld();
        String gameWorldName = "manhunt_game";
        Location toLocation = null;

        if (fromWorld.getName().equals(gameWorldName)) {
            World nether = Bukkit.getWorld(gameWorldName + "_nether");
            if (nether != null) {
                toLocation = new Location(nether, e.getFrom().getX() / 8, e.getFrom().getY(), e.getFrom().getZ() / 8);
            }
        } else if (fromWorld.getName().equals(gameWorldName + "_nether")) {
            World overworld = Bukkit.getWorld(gameWorldName);
            if (overworld != null) {
                toLocation = new Location(overworld, e.getFrom().getX() * 8, e.getFrom().getY(), e.getFrom().getZ() * 8);
            }
        }


        if (fromWorld.getName().equals(gameWorldName) && e.getEntity().getLocation().getBlock().getType() == Material.END_PORTAL) {
            World end = Bukkit.getWorld(gameWorldName + "_the_end");
            if (end != null) toLocation = new Location(end, 100, 50, 0);
        }

        if (toLocation != null) {
            e.setTo(toLocation);
        }
    }

    private void createEndPlatform(Location loc) {
        int y = loc.getBlockY() - 1;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                loc.getWorld().getBlockAt(loc.getBlockX() + x, y, loc.getBlockZ() + z).setType(Material.OBSIDIAN);
                for(int i = 1; i <= 3; i++) {
                    loc.getWorld().getBlockAt(loc.getBlockX() + x, y + i, loc.getBlockZ() + z).setType(Material.AIR);
                }
            }
        }
    }
}