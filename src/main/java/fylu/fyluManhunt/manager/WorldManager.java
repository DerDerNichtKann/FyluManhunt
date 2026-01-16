package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import fylu.fyluManhunt.listeners.LobbyListener;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

public class WorldManager {

    private final FyluManhunt plugin;
    public static final String GAME_WORLD = "manhunt_game";
    public static final String NETHER_WORLD = "manhunt_game_nether";
    public static final String END_WORLD = "manhunt_game_the_end";

    private static final String LOBBY_WORLD = "world";

    private String nextSeed = null;

    public WorldManager(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    public void setNextSeed(String seed) { this.nextSeed = seed; }

    public void resetWorlds() {
        plugin.getGameManager().stopGame();
        teleportToLobbyForAll();

        Bukkit.broadcastMessage(ChatColor.RED + "Welten werden zurückgesetzt & Backups gelöscht... (Bitte warten)");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            unloadWorld(GAME_WORLD);
            unloadWorld(NETHER_WORLD);
            unloadWorld(END_WORLD);

            deleteFolder(new File(Bukkit.getWorldContainer(), GAME_WORLD));
            deleteFolder(new File(Bukkit.getWorldContainer(), NETHER_WORLD));
            deleteFolder(new File(Bukkit.getWorldContainer(), END_WORLD));

            File mainWorld = Bukkit.getWorlds().get(0).getWorldFolder();
            deleteFolder(new File(mainWorld, "advancements"));
            deleteFolder(new File(mainWorld, "stats"));

            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (backupDir.exists()) deleteFolder(backupDir);

            for(Player p : Bukkit.getOnlinePlayers()) {
                PlayerStateManager.resetPlayerFull(p);
                LobbyListener.giveLobbyItems(p);
            }

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

            plugin.getGameManager().setActivatorDisabled(plugin.getGameManager().isActivatorDisabled());

            Bukkit.broadcastMessage(ChatColor.GREEN + "Welt Reset fertig! Seed: " + seed);
        }, 20L);
    }

    public void saveGame(String slotName, CommandSender sender) {
        plugin.getGameManager().setPaused(true);
        sender.sendMessage(ChatColor.YELLOW + "Speichere Welten... (Bitte warten)");

        for(World w : Bukkit.getWorlds()) {
            w.save();
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    File backupDir = new File(plugin.getDataFolder(), "backups/" + slotName);
                    if (!backupDir.exists()) backupDir.mkdirs();

                    copyWorldFolder(GAME_WORLD, backupDir);
                    copyWorldFolder(NETHER_WORLD, backupDir);
                    copyWorldFolder(END_WORLD, backupDir);

                    File mainWorldFolder = Bukkit.getWorlds().get(0).getWorldFolder();

                    copyFolderFromTo(new File(mainWorldFolder, "advancements"), new File(backupDir, "advancements_data"));
                    copyFolderFromTo(new File(mainWorldFolder, "stats"), new File(backupDir, "stats_data"));

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        PlayerStateManager.savePlayerStates(new File(backupDir, "playerdata.yml"));

                        plugin.getGameManager().saveCrashRecoveryFile();
                        File crashFile = new File(plugin.getDataFolder(), "crash_recovery.yml");
                        if(crashFile.exists()) {
                            try {
                                Files.copy(crashFile.toPath(), new File(backupDir, "gamestate.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
                            } catch(IOException e) { e.printStackTrace(); }
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.RED + "Fehler beim Kopieren der Dateien: " + e.getMessage());
                }
            });
        }, 40L);
    }

    public void applyActivatorRules(boolean disabled) {
        String[] worlds = {GAME_WORLD, NETHER_WORLD, END_WORLD};

        for (String wName : worlds) {
            World w = Bukkit.getWorld(wName);
            if (w != null) {
                w.setGameRule(GameRule.LOCATOR_BAR, false);
            }
        }
    }

    public void loadGame(String slotName, CommandSender sender) {
        File backupDir = new File(plugin.getDataFolder(), "backups/" + slotName);
        if (!backupDir.exists()) {
            sender.sendMessage(ChatColor.RED + "Backup '" + slotName + "' existiert nicht!");
            return;
        }

        plugin.getGameManager().stopGame();
        teleportToLobbyForAll();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            unloadWorld(GAME_WORLD);
            unloadWorld(NETHER_WORLD);
            unloadWorld(END_WORLD);
            deleteFolder(new File(Bukkit.getWorldContainer(), GAME_WORLD));
            deleteFolder(new File(Bukkit.getWorldContainer(), NETHER_WORLD));
            deleteFolder(new File(Bukkit.getWorldContainer(), END_WORLD));

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    copyFolderFromBackup(new File(backupDir, GAME_WORLD), new File(Bukkit.getWorldContainer(), GAME_WORLD));
                    copyFolderFromBackup(new File(backupDir, NETHER_WORLD), new File(Bukkit.getWorldContainer(), NETHER_WORLD));
                    copyFolderFromBackup(new File(backupDir, END_WORLD), new File(Bukkit.getWorldContainer(), END_WORLD));
                    File mainWorldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
                    deleteFolder(new File(mainWorldFolder, "advancements"));
                    deleteFolder(new File(mainWorldFolder, "stats"));

                    copyFolderFromTo(new File(backupDir, "advancements_data"), new File(mainWorldFolder, "advancements"));
                    copyFolderFromTo(new File(backupDir, "stats_data"), new File(mainWorldFolder, "stats"));

                    File gameState = new File(backupDir, "gamestate.yml");
                    if (gameState.exists()) {
                        Files.copy(gameState.toPath(), new File(plugin.getDataFolder(), "crash_recovery.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        new WorldCreator(GAME_WORLD).createWorld();
                        new WorldCreator(NETHER_WORLD).environment(World.Environment.NETHER).createWorld();
                        new WorldCreator(END_WORLD).environment(World.Environment.THE_END).createWorld();

                        plugin.getGameManager().setActivatorDisabled(plugin.getGameManager().isActivatorDisabled());

                        PlayerStateManager.loadPlayerStates(new File(backupDir, "playerdata.yml"));
                        plugin.getGameManager().tryRestoreGame(false);
                    });

                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Fehler beim Laden: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }, 20L);
    }

    private void deleteFolder(File folder) {
        if (folder.exists()) {
            try (Stream<Path> walk = Files.walk(folder.toPath())) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) { e.printStackTrace(); }
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

    private void copyFolderFromTo(File source, File dest) throws IOException {
        if (!source.exists()) return;
        copyFolder(source.toPath(), dest.toPath());
    }

    private void copyFolder(Path src, Path dest) throws IOException {
        Files.walk(src).forEach(source -> {
            try {
                if(source.getFileName().toString().equals("session.lock")) return;
                if(source.getFileName().toString().equals("uid.dat")) return;

                Path destination = dest.resolve(src.relativize(source));
                if(Files.isDirectory(source)) {
                    if(!Files.exists(destination)) Files.createDirectory(destination);
                } else {
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) { e.printStackTrace(); }
        });
    }

    public void preloadChunksAndStart(Runnable onComplete) {
        World world = getGameWorld();
        if (world == null) return;

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
            for(Player p : Bukkit.getOnlinePlayers()) {
                p.teleport(spawn);
                p.setGameMode(GameMode.ADVENTURE);
            }
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
}