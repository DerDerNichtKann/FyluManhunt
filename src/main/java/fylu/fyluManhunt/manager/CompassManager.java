package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CompassManager {

    private final FyluManhunt plugin;

    private final Map<UUID, Map<String, Location>> lastPortalLocations = new HashMap<>();
    private final Map<UUID, UUID> hunterTargets = new HashMap<>();

    public static final String TRACKER_TITLE = ChatColor.DARK_GRAY + "Wähle Ziel";

    public CompassManager(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    // --- TRACKING LOGIK ---

    public void giveCompass(Player hunter) {
        // Prüfen, ob er schon einen hat, um Inventar-Spam zu vermeiden
        for (ItemStack is : hunter.getInventory().getContents()) {
            if (is != null && is.getType() == Material.COMPASS) {
                return;
            }
        }

        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Tracker Kompass " + ChatColor.GRAY + "(Rechtsklick)");
        meta.setLore(Arrays.asList(ChatColor.YELLOW + "Zeigt auf den Runner", ChatColor.GRAY + "Rechtsklick um Ziel zu wechseln"));
        meta.setLodestoneTracked(false);
        compass.setItemMeta(meta);

        hunter.getInventory().addItem(compass);
    }

    public void setTarget(Player hunter, Player runner) {
        hunterTargets.put(hunter.getUniqueId(), runner.getUniqueId());
        hunter.sendMessage(ChatColor.GREEN + "Du trackst nun: " + ChatColor.YELLOW + runner.getName());
        updateCompassForHunter(hunter, runner);
    }

    public void updateTrackers() {
        if (plugin.getGameManager().getRunnerUUIDs().isEmpty()) return;

        // Standard-Ziel: Der erste Runner in der Liste
        UUID defaultRunnerUUID = plugin.getGameManager().getRunnerUUIDs().get(0);

        for (Player hunter : Bukkit.getOnlinePlayers()) {
            if (plugin.getGameManager().isRunner(hunter)) continue;

            UUID targetUUID = hunterTargets.getOrDefault(hunter.getUniqueId(), defaultRunnerUUID);

            // Falls das Ziel offline ging oder kein Runner mehr ist -> Fallback
            if (!plugin.getGameManager().getRunnerUUIDs().contains(targetUUID)) {
                targetUUID = defaultRunnerUUID;
            }

            Player targetRunner = Bukkit.getPlayer(targetUUID);
            // Wir updaten den Kompass auch, wenn der Runner offline ist (auf letzte Position),
            // aber hier vereinfacht: nur wenn online.
            if (targetRunner != null) {
                updateCompassForHunter(hunter, targetRunner);
            }
        }
    }

    private void updateCompassForHunter(Player hunter, Player runner) {
        World hunterWorld = hunter.getWorld();
        World runnerWorld = runner.getWorld();
        Location targetLoc = null;

        // 1. Ziel berechnen
        if (hunterWorld.getUID().equals(runnerWorld.getUID())) {
            // Gleiche Welt -> Zeige direkt auf Spieler
            targetLoc = runner.getLocation();
        } else {
            // Andere Welt -> Zeige auf das letzte bekannte Portal in DIESER Welt
            Map<String, Location> locs = lastPortalLocations.get(runner.getUniqueId());
            if (locs != null && locs.containsKey(hunterWorld.getName())) {
                targetLoc = locs.get(hunterWorld.getName());
            } else {
                targetLoc = hunterWorld.getSpawnLocation(); // Fallback
            }
        }

        if (targetLoc != null) {
            // Vanilla Kompass Ziel setzen (für Spawn-Kompass Verhalten)
            hunter.setCompassTarget(targetLoc);

            // 2. ALLE Kompasse im Inventar aktualisieren (Lodestone Meta)
            // Das behebt den Fehler, dass er sich nur in der Hand updated.
            for (ItemStack item : hunter.getInventory().getContents()) {
                if (item != null && item.getType() == Material.COMPASS) {
                    updateCompassItem(item, targetLoc);
                }
            }
        }
    }

    private void updateCompassItem(ItemStack item, Location loc) {
        CompassMeta meta = (CompassMeta) item.getItemMeta();
        // Nur updaten, wenn sich was ändert (Performance), oder Lodestone erzwingen
        meta.setLodestone(loc);
        meta.setLodestoneTracked(false); // WICHTIG: Sonst dreht er durch
        item.setItemMeta(meta);
    }

    public void updateLastPortalLocation(UUID runnerId, String worldName, Location loc) {
        lastPortalLocations.computeIfAbsent(runnerId, k -> new HashMap<>()).put(worldName, loc);
    }

    // --- GUI LOGIK ---

    public void openTrackerGUI(Player hunter) {
        int size = 9;
        while (plugin.getGameManager().getRunnerUUIDs().size() > size) size += 9;
        Inventory gui = Bukkit.createInventory(null, size, TRACKER_TITLE);

        for (UUID runnerUUID : plugin.getGameManager().getRunnerUUIDs()) {
            Player p = Bukkit.getPlayer(runnerUUID);
            if (p != null) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(p);
                meta.setDisplayName(ChatColor.RED + p.getName());
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Welt: " + getDimensionName(p.getWorld()),
                        ChatColor.YELLOW + "Klicke zum Tracken"
                ));
                head.setItemMeta(meta);
                gui.addItem(head);
            }
        }
        hunter.openInventory(gui);
    }

    private String getDimensionName(World w) {
        switch(w.getEnvironment()) {
            case NETHER: return "Nether";
            case THE_END: return "End";
            default: return "Overworld";
        }
    }
}