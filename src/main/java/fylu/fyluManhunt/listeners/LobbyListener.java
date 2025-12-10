package fylu.fyluManhunt.listeners;

import fylu.fyluManhunt.FyluManhunt;
import fylu.fyluManhunt.manager.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;

public class LobbyListener implements Listener {

    private final FyluManhunt plugin;
    private final String TITLE_RUNNER = ChatColor.DARK_GRAY + "Teams: Runner wählen";
    private final String TITLE_SETTINGS = ChatColor.DARK_GRAY + "Manhunt Einstellungen";

    public LobbyListener(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    private boolean isInLobbyWorld(Player p) {
        return p.getWorld().getName().equals("world");
    }

    @EventHandler
    public void onMobSpawn(EntitySpawnEvent e) {
        if (e.getEntity().getWorld().getName().equals("world")) {
            // Blockiere alles außer Spieler, ArmorStands und Items
            if (e.getEntityType() != EntityType.PLAYER &&
                    e.getEntityType() != EntityType.ARMOR_STAND &&
                    e.getEntityType() != EntityType.ITEM) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (plugin.getGameManager().isGameRunning()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.teleport(plugin.getWorldManager().getGameWorld().getSpawnLocation());
            if (!plugin.getGameManager().isRunner(p)) {
                plugin.getCompassManager().giveCompass(p);
                p.sendMessage(ChatColor.YELLOW + "Du bist dem laufenden Spiel beigetreten.");
            }
            return;
        }

        handleLobbyJoin(p);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo().getWorld().getName().equals("world") && !plugin.getGameManager().isGameRunning()) {
            handleLobbyJoin(e.getPlayer());
        }
    }

    public void handleLobbyJoin(Player p) {
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.getActivePotionEffects().forEach(eff -> p.removePotionEffect(eff.getType()));
        p.setGameMode(GameMode.ADVENTURE);

        giveLobbyItems(p);
    }

    public static void giveLobbyItems(Player p) {
        p.getInventory().clear();
        if (p.isOp()) {
            p.getInventory().setItem(0, createItem(Material.CLOCK, ChatColor.GOLD + "Runner wählen " + ChatColor.GRAY + "(Rechtsklick)"));
            p.getInventory().setItem(4, createItem(Material.COMPARATOR, ChatColor.AQUA + "Einstellungen " + ChatColor.GRAY + "(Rechtsklick)"));
            p.getInventory().setItem(8, createItem(Material.LIME_STAINED_GLASS_PANE, ChatColor.RED + "SPIEL STARTEN " + ChatColor.GRAY + "(Rechtsklick)"));
        }
    }

    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!isInLobbyWorld(e.getPlayer()) || plugin.getGameManager().isGameRunning()) return;
        if (e.getAction() == Action.PHYSICAL) { e.setCancelled(true); return; }
        if (e.getItem() == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (!p.isOp()) return;

        Material type = e.getItem().getType();
        if (type == Material.CLOCK) { e.setCancelled(true); openRunnerGUI(p); }
        else if (type == Material.COMPARATOR) { e.setCancelled(true); openSettingsGUI(p); }
        else if (type == Material.LIME_STAINED_GLASS_PANE) { e.setCancelled(true); plugin.getGameManager().startGame(); }
    }

    private void openRunnerGUI(Player p) {
        Inventory gui = Bukkit.createInventory(null, 27, TITLE_RUNNER);
        for (Player online : Bukkit.getOnlinePlayers()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(online);
            if (plugin.getGameManager().isRunner(online)) {
                meta.setDisplayName(ChatColor.RED + online.getName());
                meta.setLore(Arrays.asList(ChatColor.GRAY + "Status: " + ChatColor.RED + "RUNNER", ChatColor.YELLOW + "Klicke um zu entfernen"));
            } else {
                meta.setDisplayName(ChatColor.GREEN + online.getName());
                meta.setLore(Arrays.asList(ChatColor.GRAY + "Status: " + ChatColor.GREEN + "HUNTER", ChatColor.YELLOW + "Klicke um als Runner zu setzen"));
            }
            head.setItemMeta(meta);
            gui.addItem(head);
        }
        p.openInventory(gui);
    }

    private void openSettingsGUI(Player p) {
        Inventory gui = Bukkit.createInventory(null, 27, TITLE_SETTINGS);
        GameManager gm = plugin.getGameManager();
        gui.setItem(10, createSettingItem(Material.RED_BED, "Bedbomb Nether", gm.isBedbombNether()));
        gui.setItem(11, createSettingItem(Material.PURPLE_BED, "Bedbomb End", gm.isBedbombEnd()));
        gui.setItem(13, createSettingItem(Material.CLOCK, "Timer sichtbar", gm.isTimerVisible()));

        ItemStack headstart = new ItemStack(Material.FEATHER);
        ItemMeta hm = headstart.getItemMeta();
        hm.setDisplayName(ChatColor.GOLD + "Vorsprung: " + ChatColor.WHITE + gm.getHeadStartSeconds() + "s");
        hm.setLore(Arrays.asList(ChatColor.GREEN + "Linksklick: +10s", ChatColor.RED + "Rechtsklick: -10s"));
        headstart.setItemMeta(hm);
        gui.setItem(15, headstart);

        gui.setItem(16, createSettingItem(Material.GOLDEN_APPLE, "Tab-Herzen", plugin.getScoreboardManager().isShowingHearts()));

        ItemStack diffItem = new ItemStack(Material.IRON_SWORD);
        ItemMeta dm = diffItem.getItemMeta();
        dm.setDisplayName(ChatColor.AQUA + "Schwierigkeit");
        dm.setLore(Arrays.asList(ChatColor.GRAY + "Aktuell: " + ChatColor.YELLOW + gm.getDifficulty().name(), ChatColor.YELLOW + "Klicke zum Ändern"));
        diffItem.setItemMeta(dm);
        gui.setItem(12, diffItem);

        p.openInventory(gui);
    }

    private ItemStack createSettingItem(Material mat, String name, boolean enabled) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        String status = enabled ? ChatColor.GREEN + "AKTIVIERT" : ChatColor.RED + "DEAKTIVIERT";
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Status: " + status, ChatColor.YELLOW + "Klicke zum Umschalten"));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.equals(TITLE_RUNNER) && !title.equals(TITLE_SETTINGS)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        if(!p.isOp()) return;

        GameManager gm = plugin.getGameManager();
        if (title.equals(TITLE_RUNNER)) {
            if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
                if (meta.getOwningPlayer() != null && meta.getOwningPlayer().getPlayer() != null) {
                    gm.toggleRunner(meta.getOwningPlayer().getPlayer());
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                    openRunnerGUI(p);
                }
            }
        } else if (title.equals(TITLE_SETTINGS)) {
            String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            if (name.contains("Bedbomb Nether")) gm.setBedbombNether(!gm.isBedbombNether());
            else if (name.contains("Bedbomb End")) gm.setBedbombEnd(!gm.isBedbombEnd());
            else if (name.contains("Timer sichtbar")) gm.toggleTimerVisibility();
            else if (name.contains("Tab-Herzen")) plugin.getScoreboardManager().setShowHearts(!plugin.getScoreboardManager().isShowingHearts());
            else if (name.contains("Schwierigkeit")) { gm.cycleDifficulty(); }
            else if (name.contains("Vorsprung")) {
                int current = gm.getHeadStartSeconds();
                if (e.isLeftClick()) current += 10;
                if (e.isRightClick()) current = Math.max(0, current - 10);
                gm.setHeadStartSeconds(current);
            }
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            openSettingsGUI(p);
        }
    }

    @EventHandler public void onBreak(BlockBreakEvent e) { if (isInLobbyWorld(e.getPlayer()) && !e.getPlayer().isOp()) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (isInLobbyWorld(e.getPlayer()) && !e.getPlayer().isOp()) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (isInLobbyWorld(e.getPlayer()) && !e.getPlayer().isOp()) e.setCancelled(true); }
}