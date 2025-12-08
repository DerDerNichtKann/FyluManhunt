package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class GameManager {

    private final FyluManhunt plugin;

    // Listen
    private final List<UUID> runners = new ArrayList<>();
    private final List<UUID> aliveRunners = new ArrayList<>();

    // Settings
    private boolean isRunning = false;
    private boolean isPaused = false;
    private boolean timerVisible = true;
    private boolean bedbombNether = true;
    private boolean bedbombEnd = true;
    private int headStartSeconds = 60;
    private Difficulty difficulty = Difficulty.NORMAL; // Standard Schwierigkeit

    // Runtime
    private int gameTime = 0;
    private BukkitTask timerTask;
    private BukkitTask autoSaveTask;

    public GameManager(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    // --- RUNNER LOGIK ---

    public void addRunner(Player p) {
        if (!runners.contains(p.getUniqueId())) {
            runners.add(p.getUniqueId());
            p.sendMessage(ChatColor.GREEN + "Du bist nun ein Runner!");
        }
    }

    public void removeRunner(Player p) {
        if (runners.contains(p.getUniqueId())) {
            runners.remove(p.getUniqueId());
            p.sendMessage(ChatColor.YELLOW + "Du bist kein Runner mehr.");
        }
    }

    public void clearRunners() {
        runners.clear();
    }

    public void toggleRunner(Player p) {
        if (runners.contains(p.getUniqueId())) {
            removeRunner(p);
        } else {
            addRunner(p);
        }
    }

    public boolean isRunner(Player p) { return runners.contains(p.getUniqueId()); }
    public List<UUID> getRunnerUUIDs() { return runners; }


    // --- SPIEL START ---
    public void startGame() {
        if (runners.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "Keine Runner ausgewählt!");
            return;
        }

        // Erst Chunks laden, dann Start ausführen
        plugin.getWorldManager().preloadChunksAndStart(this::executeStartLogic);
    }

    private void executeStartLogic() {
        isRunning = true;
        isPaused = false;
        gameTime = 0;
        aliveRunners.clear();
        aliveRunners.addAll(runners);

        // Welt vorbereiten
        World gw = plugin.getWorldManager().getGameWorld();
        gw.setTime(1000);
        gw.setStorm(false);
        gw.setDifficulty(difficulty); // Schwierigkeit setzen

        // Difficulty auch für Nether/End anwenden
        World nether = Bukkit.getWorld(WorldManager.NETHER_WORLD);
        if(nether != null) nether.setDifficulty(difficulty);
        World end = Bukkit.getWorld(WorldManager.END_WORLD);
        if(end != null) end.setDifficulty(difficulty);

        // Alle Spieler resetten
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setHealth(20);
            p.setFoodLevel(20);
            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().setArmorContents(null);
            p.setExp(0);
            p.setLevel(0);
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        }

        // Runner Teleport
        for (UUID uuid : runners) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.teleport(gw.getSpawnLocation());
                p.sendMessage(ChatColor.GREEN + "Lauf um dein Leben! Der Drache muss sterben!");
            }
        }

        plugin.getScoreboardManager().startHeartTask();
        startTimer();
        startAutoSave();

        // Hunter Logik
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Spiel gestartet! Hunter kommen in " + headStartSeconds + " Sekunden.");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!isRunner(p)) {
                    p.teleport(gw.getSpawnLocation());
                    p.sendMessage(ChatColor.RED + "JAGD DIE RUNNER!");
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    plugin.getCompassManager().giveCompass(p);
                }
            }
        }, headStartSeconds * 20L);
    }

    // --- GAME OVER LOGIK ---

    public void eliminateRunner(Player p) {
        if (!isRunning) return;
        if (aliveRunners.contains(p.getUniqueId())) {
            aliveRunners.remove(p.getUniqueId());
            p.setGameMode(GameMode.SPECTATOR);
            Bukkit.broadcastMessage(ChatColor.RED + ChatColor.BOLD.toString() + "Runner " + p.getName() + " ist ausgeschieden!");
            p.sendTitle(ChatColor.RED + "ELIMINIERT", "Du bist nun Zuschauer", 10, 60, 20);
            checkWinConditions();
        }
    }

    public void dragonDied() {
        if (!isRunning) return;
        finishGame(true);
    }

    private void checkWinConditions() {
        if (aliveRunners.isEmpty()) {
            finishGame(false);
        }
    }

    private void finishGame(boolean runnersWon) {
        stopGame(); // Stoppt Timer etc.

        String title = runnersWon ? ChatColor.GREEN + "RUNNER GEWINNEN!" : ChatColor.RED + "HUNTER GEWINNEN!";
        String sub = runnersWon ? "Der Drache ist tot" : "Alle Runner wurden eliminiert";

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(title, sub, 10, 100, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }

        // --- 10 SEKUNDEN TIMER BIS RESET ---
        Bukkit.broadcastMessage(ChatColor.GRAY + "--------------------------------");
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "RUNDE VORBEI!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Automatischer Reset und Teleport in 10 Sekunden...");
        Bukkit.broadcastMessage(ChatColor.GRAY + "--------------------------------");

        new BukkitRunnable() {
            int seconds = 10;
            @Override
            public void run() {
                if (seconds <= 0) {
                    this.cancel();
                    plugin.getWorldManager().resetWorlds(); // Reset & Teleport zur Lobby
                    return;
                }
                if (seconds <= 5) {
                    Bukkit.broadcastMessage(ChatColor.AQUA + "Reset in " + seconds + "...");
                    for(Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                }
                seconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // --- AUTO SAVE / CRASH RECOVERY ---

    public void startAutoSave() {
        // Speichert alle 30 Sekunden (600 Ticks)
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveCrashRecoveryFile, 600L, 600L);
    }

    public void saveCrashRecoveryFile() {
        if (!isRunning) return;

        File file = new File(plugin.getDataFolder(), "crash_recovery.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        cfg.set("game.running", true);
        cfg.set("game.time", gameTime);
        cfg.set("game.runners", runners.stream().map(UUID::toString).collect(Collectors.toList()));
        cfg.set("game.alive", aliveRunners.stream().map(UUID::toString).collect(Collectors.toList()));

        // Settings sichern
        cfg.set("settings.headstart", headStartSeconds);
        cfg.set("settings.bedbomb.nether", bedbombNether);
        cfg.set("settings.bedbomb.end", bedbombEnd);
        cfg.set("settings.difficulty", difficulty.name());

        // Spieler Inventare & Positionen sichern
        for (Player p : Bukkit.getOnlinePlayers()) {
            String path = "players." + p.getUniqueId();
            cfg.set(path + ".loc", p.getLocation());
            cfg.set(path + ".hp", p.getHealth());
            cfg.set(path + ".food", p.getFoodLevel());
            cfg.set(path + ".inv", p.getInventory().getContents());
            cfg.set(path + ".armor", p.getInventory().getArmorContents());
            cfg.set(path + ".gamemode", p.getGameMode().toString());
        }

        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public void tryRestoreGame() {
        File file = new File(plugin.getDataFolder(), "crash_recovery.yml");
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.getBoolean("game.running")) return;

        Bukkit.getLogger().info("FyluManhunt: Crash erkannt! Stelle Spielstand wieder her...");

        this.isRunning = true;
        this.isPaused = true;
        this.gameTime = cfg.getInt("game.time");
        this.headStartSeconds = cfg.getInt("settings.headstart");
        this.bedbombNether = cfg.getBoolean("settings.bedbomb.nether");
        this.bedbombEnd = cfg.getBoolean("settings.bedbomb.end");

        try {
            this.difficulty = Difficulty.valueOf(cfg.getString("settings.difficulty", "NORMAL"));
        } catch (Exception e) { this.difficulty = Difficulty.NORMAL; }

        this.runners.clear();
        for(String s : cfg.getStringList("game.runners")) this.runners.add(UUID.fromString(s));

        this.aliveRunners.clear();
        for(String s : cfg.getStringList("game.alive")) this.aliveRunners.add(UUID.fromString(s));

        // Spieler wiederherstellen
        for (Player p : Bukkit.getOnlinePlayers()) {
            String path = "players." + p.getUniqueId();
            if (cfg.contains(path)) {
                try {
                    p.teleport((Location) cfg.get(path + ".loc"));
                    p.setHealth(cfg.getDouble(path + ".hp"));
                    p.setFoodLevel(cfg.getInt(path + ".food"));

                    List<ItemStack> inv = (List<ItemStack>) cfg.getList(path + ".inv");
                    if(inv != null) p.getInventory().setContents(inv.toArray(new ItemStack[0]));

                    List<ItemStack> armor = (List<ItemStack>) cfg.getList(path + ".armor");
                    if(armor != null) p.getInventory().setArmorContents(armor.toArray(new ItemStack[0]));

                    p.setGameMode(GameMode.valueOf(cfg.getString(path + ".gamemode", "SURVIVAL")));
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Konnte Spieler " + p.getName() + " nicht komplett wiederherstellen.");
                }
            }
        }

        plugin.getScoreboardManager().startHeartTask();
        startTimer();
        startAutoSave();

        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "SERVER CRASH ERKANNT! Spielstand wiederhergestellt.");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Das Spiel ist PAUSIERT. Nutze /unpause zum Fortsetzen.");
    }

    public void deleteCrashFile() {
        File file = new File(plugin.getDataFolder(), "crash_recovery.yml");
        if(file.exists()) file.delete();
    }

    // --- SETTINGS GETTER/SETTER ---

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty d) { this.difficulty = d; }
    public void cycleDifficulty() {
        switch (difficulty) {
            case PEACEFUL: difficulty = Difficulty.EASY; break;
            case EASY: difficulty = Difficulty.NORMAL; break;
            case NORMAL: difficulty = Difficulty.HARD; break;
            case HARD: difficulty = Difficulty.PEACEFUL; break;
        }
    }

    public boolean isBedbombNether() { return bedbombNether; }
    public void setBedbombNether(boolean b) { this.bedbombNether = b; }
    public boolean isBedbombEnd() { return bedbombEnd; }
    public void setBedbombEnd(boolean b) { this.bedbombEnd = b; }
    public int getHeadStartSeconds() { return headStartSeconds; }
    public void setHeadStartSeconds(int s) { this.headStartSeconds = s; }
    public void toggleTimerVisibility() { this.timerVisible = !this.timerVisible; }
    public boolean isTimerVisible() { return timerVisible; }

    // --- STANDARD METHODEN ---

    public void stopGame() {
        isRunning = false;
        if (timerTask != null) timerTask.cancel();
        if (autoSaveTask != null) autoSaveTask.cancel();
        deleteCrashFile();
        aliveRunners.clear();
        plugin.getScoreboardManager().setShowHearts(false);
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
        if (paused) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "Spiel pausiert!");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tick freeze");
        } else {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Spiel läuft weiter!");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tick unfreeze");
        }
    }

    public boolean isGameRunning() { return isRunning; }
    public boolean isPaused() { return isPaused; }
    public int getGameTime() { return gameTime; }
    public void setGameTime(int t) { this.gameTime = t; }

    public void forceStartGameWithoutTeleport() {
        this.isRunning = true;
        this.isPaused = true;
        aliveRunners.clear();
        aliveRunners.addAll(runners);
        plugin.getScoreboardManager().startHeartTask();
        startTimer();
    }

    private void startTimer() {
        if (timerTask != null && !timerTask.isCancelled()) timerTask.cancel();

        // Update alle 5 Ticks (0.25s) für flüssigen Kompass
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int tickCounter = 0;

            @Override
            public void run() {
                if (isRunning && !isPaused) {
                    tickCounter++;
                    plugin.getCompassManager().updateTrackers();

                    // Jede Sekunde (4 * 5 Ticks = 20 Ticks)
                    if (tickCounter >= 4) {
                        tickCounter = 0;
                        gameTime++;
                        if (timerVisible) {
                            String time = String.format("%02d:%02d:%02d", gameTime / 3600, (gameTime % 3600) / 60, gameTime % 60);
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GOLD + time));
                            }
                        }
                    }
                }
            }
        }, 0L, 5L);
    }
}