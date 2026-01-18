package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class GameManager {

    private final FyluManhunt plugin;

    private final List<UUID> runners = new ArrayList<>();
    private final List<UUID> aliveRunners = new ArrayList<>();

    private boolean isRunning = false;
    private boolean isPaused = false;
    private boolean timerVisible = true;
    private boolean bedbombNether = true;
    private boolean bedbombEnd = true;

    private boolean activatorDisabled = false;

    private int headStartSeconds = 60;
    private Difficulty difficulty = Difficulty.NORMAL;

    private int gameTime = 0;
    private BukkitTask timerTask;
    private BukkitTask autoSaveTask;

    public GameManager(FyluManhunt plugin) {
        this.plugin = plugin;
    }

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

    public void startGame() {
        if (runners.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "Keine Runner ausgewählt!");
            return;
        }
        plugin.getWorldManager().preloadChunksAndStart(this::executeStartLogic);
    }

    private void executeStartLogic() {
        isRunning = true;
        isPaused = false;
        gameTime = 0;
        aliveRunners.clear();
        aliveRunners.addAll(runners);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tick unfreeze");

        plugin.getWorldManager().applyActivatorRules(activatorDisabled);

        World gw = plugin.getWorldManager().getGameWorld();
        gw.setTime(1000);
        gw.setStorm(false);
        gw.setDifficulty(difficulty);

        World nether = Bukkit.getWorld(WorldManager.NETHER_WORLD);
        if(nether != null) nether.setDifficulty(difficulty);
        World end = Bukkit.getWorld(WorldManager.END_WORLD);
        if(end != null) end.setDifficulty(difficulty);

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerStateManager.resetPlayerFull(p);
        }

        Location spawn = gw.getSpawnLocation();
        spawn.setY(gw.getHighestBlockYAt(spawn) + 1);

        for (UUID uuid : runners) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.teleport(spawn);
            }
        }

        plugin.getScoreboardManager().startHeartTask();
        startTimer();
        startAutoSave();

        Bukkit.broadcastMessage(ChatColor.YELLOW + "Spiel gestartet! Hunter kommen in " + headStartSeconds + " Sekunden.");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!isRunner(p)) {
                    p.teleport(spawn);
                    p.sendMessage(ChatColor.RED + "JAGD DIE RUNNER!");
                    plugin.getCompassManager().giveCompass(p);
                }
            }
        }, headStartSeconds * 20L);
    }

    public void eliminateRunner(Player p) {
        if (!isRunning) return;
        p.setGameMode(GameMode.SPECTATOR);
        if (aliveRunners.contains(p.getUniqueId())) {
            aliveRunners.remove(p.getUniqueId());
        }
    }

    public void dragonDied() {
        if (!isRunning) return;
        finishGame();
    }

    private void finishGame() {
        stopGame();
    }

    public void startAutoSave() {
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveCrashRecoveryFile, 1200L, 1200L);
    }

    public void saveCrashRecoveryFile() {
        File file = new File(plugin.getDataFolder(), "crash_recovery.yml");

        if (isRunning) {
            PlayerStateManager.savePlayerStates(file);
        }

        org.bukkit.configuration.file.FileConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        cfg.set("game.running", isRunning);
        cfg.set("game.time", gameTime);
        cfg.set("game.runners", runners.stream().map(UUID::toString).collect(Collectors.toList()));
        cfg.set("game.alive", aliveRunners.stream().map(UUID::toString).collect(Collectors.toList()));

        cfg.set("settings.headstart", headStartSeconds);
        cfg.set("settings.bedbomb.nether", bedbombNether);
        cfg.set("settings.bedbomb.end", bedbombEnd);
        cfg.set("settings.activator", activatorDisabled);
        cfg.set("settings.difficulty", difficulty.name());
        cfg.set("settings.timer_visible", timerVisible);
        cfg.set("settings.show_hearts", plugin.getScoreboardManager().isShowingHearts());
        cfg.set("game.paused", isPaused);

        try { cfg.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    public void restorePlayerData(Player p) {
        File file = new File(plugin.getDataFolder(), "crash_recovery.yml");
        if (!file.exists()) return;
        PlayerStateManager.loadSinglePlayerState(p, file);
    }

    public void tryRestoreGame() {
        tryRestoreGame(true);
    }

    public void tryRestoreGame(boolean isCrash) {
        File file = new File(plugin.getDataFolder(), "crash_recovery.yml");
        if (!file.exists()) return;

        if (Bukkit.getWorld(WorldManager.GAME_WORLD) == null) new WorldCreator(WorldManager.GAME_WORLD).createWorld();
        if (Bukkit.getWorld(WorldManager.NETHER_WORLD) == null) new WorldCreator(WorldManager.NETHER_WORLD).environment(World.Environment.NETHER).createWorld();
        if (Bukkit.getWorld(WorldManager.END_WORLD) == null) new WorldCreator(WorldManager.END_WORLD).environment(World.Environment.THE_END).createWorld();

        org.bukkit.configuration.file.FileConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        this.headStartSeconds = cfg.getInt("settings.headstart", 60);
        this.bedbombNether = cfg.getBoolean("settings.bedbomb.nether", true);
        this.bedbombEnd = cfg.getBoolean("settings.bedbomb.end", true);
        this.activatorDisabled = cfg.getBoolean("settings.activator", false);
        this.timerVisible = cfg.getBoolean("settings.timer_visible", true);
        plugin.getScoreboardManager().setShowHearts(cfg.getBoolean("settings.show_hearts", true));
        try { this.difficulty = Difficulty.valueOf(cfg.getString("settings.difficulty", "NORMAL")); } catch (Exception e) {}

        this.runners.clear();
        for(String s : cfg.getStringList("game.runners")) {
            try { this.runners.add(UUID.fromString(s)); } catch(Exception ignored){}
        }

        plugin.getWorldManager().applyActivatorRules(activatorDisabled);

        if (!cfg.getBoolean("game.running")) return;

        if (isCrash) {
            Bukkit.getLogger().info("FyluManhunt: Crash erkannt! Stelle Spielstand wieder her...");
            Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "SERVER CRASH ERKANNT! Spielstand wiederhergestellt.");
            this.isPaused = true;
        } else {
            this.isPaused = false;
        }

        if (cfg.contains("game.paused")) {
            this.isPaused = cfg.getBoolean("game.paused");
        }

        if (this.isPaused) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tick freeze");
        else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tick unfreeze");

        this.isRunning = true;
        this.gameTime = cfg.getInt("game.time");

        this.aliveRunners.clear();
        for(String s : cfg.getStringList("game.alive")) {
            try { this.aliveRunners.add(UUID.fromString(s)); } catch(Exception ignored){}
        }

        plugin.getScoreboardManager().startHeartTask();
        startTimer();
        startAutoSave();
    }

    public void deleteCrashFile() {
        File file = new File(plugin.getDataFolder(), "crash_recovery.yml");
        if(file.exists()) file.delete();
    }

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

    public boolean isActivatorDisabled() { return activatorDisabled; }
    public void setActivatorDisabled(boolean disabled) {
        this.activatorDisabled = disabled;
        plugin.getWorldManager().applyActivatorRules(disabled);
    }

    public void stopGame() {
        isRunning = false;
        isPaused = false;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tick unfreeze");
        if (timerTask != null) timerTask.cancel();
        if (autoSaveTask != null) autoSaveTask.cancel();
        deleteCrashFile();
        aliveRunners.clear();
        plugin.getScoreboardManager().clearTeams();
    }

    public void togglePause() {
        this.isPaused = !this.isPaused;
        String msg = isPaused ? ChatColor.GOLD + "Spiel pausiert!" : ChatColor.GREEN + "Spiel läuft weiter!";
        for(Player p : Bukkit.getOnlinePlayers()) {
            if(!isRunner(p)) {
                p.sendMessage(msg);
            }
        }
        if (isPaused) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tick freeze");
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tick unfreeze");
        }
    }

    public void setPaused(boolean paused) {
        if (this.isPaused != paused) togglePause();
    }

    public boolean isGameRunning() { return isRunning; }
    public boolean isPaused() { return isPaused; }
    public int getGameTime() { return gameTime; }
    public void setGameTime(int t) { this.gameTime = t; }

    private void startTimer() {
        if (timerTask != null && !timerTask.isCancelled()) timerTask.cancel();
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int tickCounter = 0;
            @Override
            public void run() {
                if (isRunning && !isPaused) {
                    tickCounter++;
                    plugin.getCompassManager().updateTrackers();
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