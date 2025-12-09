package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

    public void setNextSeed(String seed) { this.nextSeed = seed; }

    // --- WELT RESET ---

    public void resetWorlds() {
        plugin.getGameManager().stopGame();
        teleportToLobbyForAll();

        Bukkit.broadcastMessage(ChatColor.RED + "Welten werden zurückgesetzt... (Bitte warten)");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Stats & Achievements resetten
            resetPlayerAdvancementsAndStats();

            unloadWorld(GAME_WORLD);
            unloadWorld(NETHER_WORLD);
            unloadWorld(END_WORLD);
            deleteWorldFolder(GAME_WORLD);
            deleteWorldFolder(NETHER_WORLD);
            deleteWorldFolder(END_WORLD);

            long seed;
            if (nextSeed != null) {
                try { seed = Long.parseLong(nextSeed); }
                catch (NumberFormatException e) { seed = nextSeed.hashCode(); }
                nextSeed = null;
            } else {
                seed = new java.util.Random().nextLong();
            }

            createWorld(GAME_WORLD, World.Environment.NORMAL, seed);
            createWorld(NETHER_WORLD, World.Environment.NETHER, seed);
            createWorld(END_WORLD, World.Environment.THE_END, seed);

            disableActivatorRules();

            Bukkit.broadcastMessage(ChatColor.GREEN + "Welt Reset fertig! Seed: " + seed);
        }, 20L);
    }

    private void resetPlayerAdvancementsAndStats() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Iterator<Advancement> it = Bukkit.getServer().advancementIterator();
            while (it.hasNext()) {
                Advancement adv = it.next();
                AdvancementProgress progress = p.getAdvancementProgress(adv);
                for (String criteria : progress.getAwardedCriteria()) {
                    progress.revokeCriteria(criteria);
                }
            }
            // Stats Reset
            for (Statistic stat : Statistic.values()) {
                try {
                    if (stat.getType() == Statistic.Type.UNTYPED) {
                        p.setStatistic(stat, 0);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // --- SAVE SYSTEM ---

    public void saveGame(String slotName, CommandSender sender) {
        plugin.getGameManager().setPaused(true);
        sender.sendMessage(ChatColor.YELLOW + "Speichere Spielstand '" + slotName + "'... (Server laggt kurz)");

        for(World w : Bukkit.getWorlds()) w.save();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File backupDir = new File(plugin.getDataFolder(), "backups/" + slotName);
                if (!backupDir.exists()) backupDir.mkdirs();

                copyWorldFolder(GAME_WORLD, backupDir);
                copyWorldFolder(NETHER_WORLD, backupDir);
                copyWorldFolder(END_WORLD, backupDir);

                // Nutzt die GameManager Logik zum Speichern der PlayerData
                plugin.getGameManager().saveCrashRecoveryFile();
                // Wir kopieren die crash_recovery.yml als playerdata.yml ins Backup
                File crashFile = new File(plugin.getDataFolder(), "crash_recovery.yml");
                if(crashFile.exists()) {
                    Files.copy(crashFile.toPath(), new File(backupDir, "playerdata.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

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
            unloadWorld(GAME_WORLD);
            unloadWorld(NETHER_WORLD);
            unloadWorld(END_WORLD);
            deleteWorldFolder(GAME_WORLD);
            deleteWorldFolder(NETHER_WORLD);
            deleteWorldFolder(END_WORLD);

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    copyFolderFromBackup(new File(backupDir, GAME_WORLD), new File(Bukkit.getWorldContainer(), GAME_WORLD));
                    copyFolderFromBackup(new File(backupDir, NETHER_WORLD), new File(Bukkit.getWorldContainer(), NETHER_WORLD));
                    copyFolderFromBackup(new File(backupDir, END_WORLD), new File(Bukkit.getWorldContainer(), END_WORLD));

                    // Player Data wiederherstellen (kopieren nach crash_recovery für tryRestore)
                    File playerData = new File(backupDir, "playerdata.yml");
                    if (playerData.exists()) {
                        Files.copy(playerData.toPath(), new File(plugin.getDataFolder(), "crash_recovery.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        new WorldCreator(GAME_WORLD).createWorld();
                        new WorldCreator(NETHER_WORLD).environment(World.Environment.NETHER).createWorld();
                        new WorldCreator(END_WORLD).environment(World.Environment.THE_END).createWorld();

                        disableActivatorRules(); // Wichtig: Regeln auch beim Laden setzen!

                        // Nutzt die GameManager Restore Logik
                        plugin.getGameManager().tryRestoreGame();

                        sender.sendMessage(ChatColor.GREEN + "Backup geladen! Nutze /unpause um weiterzumachen.");
                    });

                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Fehler beim Laden: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }, 20L);
    }

    // --- FILE OPERATIONS ---

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

    // --- PRELOADING ---

    public void preloadChunksAndStart(Runnable onComplete) {
        World world = getGameWorld();
        if (world == null) return;

        // Radius 4 Chunks = 81 Chunks total (< 100)
        int radius = 4;
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

    // --- STANDARD ---

    public World getGameWorld() {
        World w = Bukkit.getWorld(GAME_WORLD);
        if (w == null) w = new WorldCreator(GAME_WORLD).createWorld();
        return w;
    }

    public void teleportToLobbyForAll() {
        World lobby = Bukkit.getWorld("world");
        if(lobby == null && !Bukkit.getWorlds().isEmpty()) lobby = Bukkit.getWorlds().get(0);

        if(lobby != null) {
            Location spawn = lobby.getSpawnLocation();
            for(Player p : Bukkit.getOnlinePlayers()) p.teleport(spawn);
        }
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
                w.setGameRule(GameRule.REDUCED_DEBUG_INFO, true); // Versteckt Koordinaten
            }
        }
    }
}