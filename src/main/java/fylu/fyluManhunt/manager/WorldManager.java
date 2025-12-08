package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WorldManager {

    private final FyluManhunt plugin;
    public static final String GAME_WORLD = "manhunt_game";
    public static final String NETHER_WORLD = "manhunt_game_nether";
    public static final String END_WORLD = "manhunt_game_the_end";

    private String nextSeed = null;

    public WorldManager(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    public void setNextSeed(String seed) {
        this.nextSeed = seed;
    }

    // --- WELT RESET & GENERIERUNG ---

    public void resetWorlds() {
        plugin.getGameManager().stopGame();
        teleportToLobbyForAll();

        Bukkit.broadcastMessage(ChatColor.RED + "Welten werden zurückgesetzt... (Bitte warten)");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 1. Entladen & Löschen
            unloadWorld(GAME_WORLD);
            unloadWorld(NETHER_WORLD);
            unloadWorld(END_WORLD);
            deleteWorldFolder(GAME_WORLD);
            deleteWorldFolder(NETHER_WORLD);
            deleteWorldFolder(END_WORLD);

            // 2. Seed bestimmen
            long seed;
            if (nextSeed != null) {
                try {
                    seed = Long.parseLong(nextSeed);
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Nutze gesetzten Seed: " + seed);
                } catch (NumberFormatException e) {
                    seed = nextSeed.hashCode();
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Nutze Text-Seed: " + nextSeed);
                }
                nextSeed = null;
            } else {
                seed = new java.util.Random().nextLong();
            }

            // 3. Neu erstellen
            createWorld(GAME_WORLD, World.Environment.NORMAL, seed);
            createWorld(NETHER_WORLD, World.Environment.NETHER, seed);
            createWorld(END_WORLD, World.Environment.THE_END, seed);

            disableActivatorRules();

            Bukkit.broadcastMessage(ChatColor.GREEN + "Welt Reset fertig! Seed: " + seed);
        }, 20L);
    }

    // --- SAVE SYSTEM ---

    public void saveGame(String slotName, CommandSender sender) {
        plugin.getGameManager().setPaused(true);
        sender.sendMessage(ChatColor.YELLOW + "Speichere Spielstand '" + slotName + "'... (Server laggt kurz)");

        // Erzwinge Speichern aller Chunks auf Disk
        for(World w : Bukkit.getWorlds()) w.save();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File backupDir = new File(plugin.getDataFolder(), "backups/" + slotName);
                if (!backupDir.exists()) backupDir.mkdirs();

                // Weltordner kopieren
                copyWorldFolder(GAME_WORLD, backupDir);
                copyWorldFolder(NETHER_WORLD, backupDir);
                copyWorldFolder(END_WORLD, backupDir);

                // Spielerdaten & GameState speichern
                savePlayerData(new File(backupDir, "playerdata.yml"));

                sender.sendMessage(ChatColor.GREEN + "Backup '" + slotName + "' erfolgreich gespeichert!");
            } catch (Exception e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.RED + "Fehler beim Speichern: " + e.getMessage());
            }
        });
    }

    // --- LOAD SYSTEM ---

    public void loadGame(String slotName, CommandSender sender) {
        File backupDir = new File(plugin.getDataFolder(), "backups/" + slotName);
        if (!backupDir.exists()) {
            sender.sendMessage(ChatColor.RED + "Backup '" + slotName + "' existiert nicht!");
            return;
        }

        plugin.getGameManager().stopGame();
        teleportToLobbyForAll();
        sender.sendMessage(ChatColor.YELLOW + "Lade Backup (bitte warten)...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Alte Welten entfernen
            unloadWorld(GAME_WORLD);
            unloadWorld(NETHER_WORLD);
            unloadWorld(END_WORLD);
            deleteWorldFolder(GAME_WORLD);
            deleteWorldFolder(NETHER_WORLD);
            deleteWorldFolder(END_WORLD);

            // Async kopieren
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // Backup wiederherstellen
                    copyFolderFromBackup(new File(backupDir, GAME_WORLD), new File(Bukkit.getWorldContainer(), GAME_WORLD));
                    copyFolderFromBackup(new File(backupDir, NETHER_WORLD), new File(Bukkit.getWorldContainer(), NETHER_WORLD));
                    copyFolderFromBackup(new File(backupDir, END_WORLD), new File(Bukkit.getWorldContainer(), END_WORLD));

                    // Zurück zum Main Thread -> Welten laden
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        new WorldCreator(GAME_WORLD).createWorld();
                        new WorldCreator(NETHER_WORLD).environment(World.Environment.NETHER).createWorld();
                        new WorldCreator(END_WORLD).environment(World.Environment.THE_END).createWorld();

                        // Spielerdaten wiederherstellen
                        restorePlayerData(new File(backupDir, "playerdata.yml"));

                        sender.sendMessage(ChatColor.GREEN + "Backup geladen! Nutze /unpause um weiterzumachen.");
                    });

                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Fehler beim Laden: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }, 20L);
    }

    // --- HELPER FÜR SAVE/LOAD ---

    private void savePlayerData(File file) throws IOException {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Game State
        cfg.set("game.time", plugin.getGameManager().getGameTime());
        cfg.set("game.runners", plugin.getGameManager().getRunnerUUIDs().stream().map(UUID::toString).collect(Collectors.toList()));

        // Settings
        cfg.set("settings.headstart", plugin.getGameManager().getHeadStartSeconds());
        cfg.set("settings.bedbomb.nether", plugin.getGameManager().isBedbombNether());
        cfg.set("settings.bedbomb.end", plugin.getGameManager().isBedbombEnd());

        // Player Data
        for(Player p : Bukkit.getOnlinePlayers()) {
            String path = "players." + p.getUniqueId();
            cfg.set(path + ".health", p.getHealth());
            cfg.set(path + ".food", p.getFoodLevel());
            cfg.set(path + ".inv", p.getInventory().getContents());
            cfg.set(path + ".armor", p.getInventory().getArmorContents());
            cfg.set(path + ".loc.world", p.getWorld().getName());
            cfg.set(path + ".loc.x", p.getLocation().getX());
            cfg.set(path + ".loc.y", p.getLocation().getY());
            cfg.set(path + ".loc.z", p.getLocation().getZ());
            cfg.set(path + ".gamemode", p.getGameMode().toString());
        }
        cfg.save(file);
    }

    private void restorePlayerData(File file) {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Game State Restore
        plugin.getGameManager().setGameTime(cfg.getInt("game.time", 0));
        plugin.getGameManager().setHeadStartSeconds(cfg.getInt("settings.headstart", 60));
        plugin.getGameManager().setBedbombNether(cfg.getBoolean("settings.bedbomb.nether", true));
        plugin.getGameManager().setBedbombEnd(cfg.getBoolean("settings.bedbomb.end", true));

        // Runner Restore
        List<String> runnerStrings = cfg.getStringList("game.runners");
        for(String s : runnerStrings) {
            try {
                Player p = Bukkit.getPlayer(UUID.fromString(s));
                if(p != null) plugin.getGameManager().addRunner(p);
            } catch(Exception ignored){}
        }

        // Start (Paused)
        plugin.getGameManager().forceStartGameWithoutTeleport();

        // Player Restore
        if (cfg.contains("players")) {
            for(String uuid : cfg.getConfigurationSection("players").getKeys(false)) {
                Player p = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
                if (p != null) {
                    String path = "players." + uuid;

                    try {
                        // Inventory
                        List<ItemStack> inv = (List<ItemStack>) cfg.getList(path + ".inv");
                        if (inv != null) p.getInventory().setContents(inv.toArray(new ItemStack[0]));
                        List<ItemStack> armor = (List<ItemStack>) cfg.getList(path + ".armor");
                        if (armor != null) p.getInventory().setArmorContents(armor.toArray(new ItemStack[0]));

                        // Stats
                        p.setHealth(cfg.getDouble(path + ".health"));
                        p.setFoodLevel(cfg.getInt(path + ".food"));
                        p.setGameMode(GameMode.valueOf(cfg.getString(path + ".gamemode", "SURVIVAL")));

                        // Teleport
                        String wName = cfg.getString(path + ".loc.world");
                        World w = Bukkit.getWorld(wName);
                        if (w != null) {
                            Location loc = new Location(w, cfg.getDouble(path + ".loc.x"), cfg.getDouble(path + ".loc.y"), cfg.getDouble(path + ".loc.z"));
                            p.teleport(loc);
                        }
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("Fehler beim Wiederherstellen von Spieler " + p.getName());
                    }
                }
            }
        }
    }

    private void copyWorldFolder(String worldName, File targetDir) throws IOException {
        File source = new File(Bukkit.getWorldContainer(), worldName);
        if (!source.exists()) return;
        File dest = new File(targetDir, worldName);
        copyFolder(source.toPath(), dest.toPath());
    }

    private void copyFolderFromBackup(File source, File dest) throws IOException {
        if (!source.exists()) return;
        copyFolder(source.toPath(), dest.toPath());
    }

    private void copyFolder(Path src, Path dest) throws IOException {
        Files.walk(src).forEach(source -> {
            try {
                if(source.getFileName().toString().equals("session.lock")) return;
                Path destination = dest.resolve(src.relativize(source));
                if(Files.isDirectory(source)) {
                    if(!Files.exists(destination)) Files.createDirectory(destination);
                } else {
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) { e.printStackTrace(); }
        });
    }

    // --- PRELOADING SYSTEM ---

    public void preloadChunksAndStart(Runnable onComplete) {
        World world = getGameWorld();
        if (world == null) return;

        int radius = 6;
        int totalChunks = (radius * 2 + 1) * (radius * 2 + 1);

        BossBar bar = Bukkit.createBossBar("Welt wird vorgeladen...", BarColor.GREEN, BarStyle.SOLID);
        for(Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        bar.setVisible(true);

        Bukkit.broadcastMessage(ChatColor.YELLOW + "Generiere Spawn-Bereich... (" + totalChunks + " Chunks)");
        new BukkitRunnableChain(plugin, radius, world, bar, onComplete).runTaskTimer(plugin, 0L, 1L);
    }

    private class BukkitRunnableChain extends org.bukkit.scheduler.BukkitRunnable {
        private final int radius;
        private final World world;
        private final BossBar bar;
        private final Runnable onComplete;
        private int currentX;
        private int currentZ;
        private final int total;
        private int count = 0;

        public BukkitRunnableChain(FyluManhunt pl, int r, World w, BossBar b, Runnable callback) {
            this.radius = r;
            this.world = w;
            this.bar = b;
            this.onComplete = callback;
            this.currentX = -radius;
            this.currentZ = -radius;
            this.total = (radius * 2 + 1) * (radius * 2 + 1);
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 40 && currentX <= radius) {
                world.getChunkAt(world.getSpawnLocation().add(currentX * 16, 0, currentZ * 16));
                count++;
                currentZ++;
                if (currentZ > radius) {
                    currentZ = -radius;
                    currentX++;
                }
            }
            double progress = (double) count / total;
            if (progress > 1.0) progress = 1.0;
            bar.setProgress(progress);
            bar.setTitle("Welt wird vorgeladen: " + (int)(progress * 100) + "%");

            if (currentX > radius) {
                this.cancel();
                bar.removeAll();
                Bukkit.broadcastMessage(ChatColor.GREEN + "Vorladen abgeschlossen!");
                onComplete.run();
            }
        }
    }

    // --- STANDARD METHODEN ---
    public World getGameWorld() {
        World w = Bukkit.getWorld(GAME_WORLD);
        if (w == null) w = new WorldCreator(GAME_WORLD).createWorld();
        return w;
    }

    public void teleportToLobbyForAll() {
        World lobby = Bukkit.getWorld("world");
        if(lobby == null && !Bukkit.getWorlds().isEmpty()) lobby = Bukkit.getWorlds().get(0);
        if(lobby != null) for(Player p : Bukkit.getOnlinePlayers()) p.teleport(lobby.getSpawnLocation());
    }

    private void createWorld(String name, World.Environment env, long seed) {
        WorldCreator c = new WorldCreator(name);
        c.environment(env);
        c.seed(seed);
        c.createWorld();
    }

    private void unloadWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) Bukkit.unloadWorld(w, false);
    }

    private void deleteWorldFolder(String worldName) {
        File path = new File(Bukkit.getWorldContainer(), worldName);
        if (path.exists()) {
            try (Stream<Path> walk = Files.walk(path.toPath())) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    public void disableActivatorRules() {
        for(String s : new String[]{GAME_WORLD, NETHER_WORLD, END_WORLD}) {
            World w = Bukkit.getWorld(s);
            if(w != null) {
                w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                w.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
            }
        }
    }
}