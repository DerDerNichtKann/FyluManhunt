package fylu.fyluManhunt.listeners;

import fylu.fyluManhunt.FyluManhunt;
import fylu.fyluManhunt.manager.CompassManager;
import org.bukkit.*;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class GameListener implements Listener {

    private final FyluManhunt plugin;

    public GameListener(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    // --- LOBBY SCHUTZ ---

    private boolean isInLobby(Player p) {
        return p.getWorld().getName().equals("world");
    }

    @EventHandler
    public void onLobbyDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && isInLobby((Player) e.getEntity())) {
            e.setCancelled(true); // Kein Schaden in Lobby
            // Falls Void, teleportiere zum Spawn
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                e.getEntity().teleport(Bukkit.getWorld("world").getSpawnLocation());
            }
        }
        // Pause check
        if (plugin.getGameManager().isPaused()) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (isInLobby((Player) e.getEntity())) {
            e.setCancelled(true);
            e.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (plugin.getGameManager().isPaused()) {
            if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ()) {
                e.setCancelled(true);
            }
        }
    }

    // --- GAMEPLAY: TOD & RESPAWN ---

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!plugin.getGameManager().isGameRunning()) return;
        Player p = e.getEntity();

        // RUNNER TOD
        if (plugin.getGameManager().isRunner(p)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getGameManager().eliminateRunner(p));
            e.setDeathMessage(ChatColor.RED + p.getName() + " ist gestorben!");
            e.getDrops().clear();
        }
        // HUNTER TOD
        else {
            // Kompass nicht droppen
            e.getDrops().removeIf(item -> item.getType() == Material.COMPASS);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();

        // Wenn Spiel läuft oder gerade vorbei ist
        if (plugin.getGameManager().isGameRunning() || !isInLobby(p)) {

            // Hunter Kompass wiedergeben (falls er ihn verloren hätte, sicherheitshalber)
            if (!plugin.getGameManager().isRunner(p)) {
                plugin.getCompassManager().giveCompass(p);
            }

            // --- RESPAWN LOCATION LOGIK ---
            Location bedSpawn = p.getBedSpawnLocation();
            World gameWorld = plugin.getWorldManager().getGameWorld();

            // Wenn Bett existiert UND in der Game-Welt liegt -> Bett nutzen
            if (bedSpawn != null && bedSpawn.getWorld().getName().startsWith(gameWorld.getName())) {
                e.setRespawnLocation(bedSpawn);
            } else {
                // Sonst immer Weltspawn der Spielwelt (NIEMALS Lobby)
                e.setRespawnLocation(gameWorld.getSpawnLocation());
            }
        }
    }

    // --- COMPASS INTERAKTION ---

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!plugin.getGameManager().isGameRunning()) return;

        // GUI öffnen bei Rechtsklick mit Kompass
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (e.getItem() != null && e.getItem().getType() == Material.COMPASS) {
                // Nur Hunter
                if (!plugin.getGameManager().isRunner(e.getPlayer())) {
                    plugin.getCompassManager().openTrackerGUI(e.getPlayer());
                    e.setCancelled(true); // Verhindert Animation
                }
            }
        }

        // Bedbomb Check
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Material type = e.getClickedBlock().getType();
            if (type.name().contains("_BED")) {
                World.Environment env = e.getPlayer().getWorld().getEnvironment();
                boolean blocked = false;
                if (env == World.Environment.NETHER && !plugin.getGameManager().isBedbombNether()) blocked = true;
                if (env == World.Environment.THE_END && !plugin.getGameManager().isBedbombEnd()) blocked = true;

                if (blocked) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage(ChatColor.RED + "Bedbombs sind hier deaktiviert!");
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(CompassManager.TRACKER_TITLE)) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
                if (meta.getOwningPlayer() != null && meta.getOwningPlayer().getPlayer() != null) {
                    Player hunter = (Player) e.getWhoClicked();
                    Player target = meta.getOwningPlayer().getPlayer();

                    plugin.getCompassManager().setTarget(hunter, target);
                    hunter.closeInventory();
                    hunter.playSound(hunter.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                }
            }
        }
    }

    // --- DRACHE ---
    @EventHandler
    public void onDragonDeath(EntityDeathEvent e) {
        if (!plugin.getGameManager().isGameRunning()) return;
        if (e.getEntity() instanceof EnderDragon) {
            plugin.getGameManager().dragonDied();
        }
    }
}