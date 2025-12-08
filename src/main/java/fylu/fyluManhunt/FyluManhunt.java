package fylu.fyluManhunt;

import fylu.fyluManhunt.commands.*;
import fylu.fyluManhunt.listeners.*;
import fylu.fyluManhunt.manager.*;
import org.bukkit.plugin.java.JavaPlugin;

public class FyluManhunt extends JavaPlugin {

    private static FyluManhunt instance;
    private GameManager gameManager;
    private WorldManager worldManager;
    private CompassManager compassManager;
    private fylu.fyluManhunt.manager.ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Manager initialisieren
        this.worldManager = new WorldManager(this);
        this.compassManager = new CompassManager(this);
        this.scoreboardManager = new fylu.fyluManhunt.manager.ScoreboardManager(this);
        this.gameManager = new GameManager(this);

        // 2. Commands registrieren (Reihenfolge korrigiert!)

        // Game Commands (Pause, Vorsprung, etc.)
        CommandGame cmdGame = new CommandGame(this);
        getCommand("game").setExecutor(cmdGame);
        getCommand("pause").setExecutor(cmdGame);
        getCommand("unpause").setExecutor(cmdGame);
        getCommand("vorsprung").setExecutor(cmdGame);

        // Manhunt Commands (Start, Settings)
        CommandManhunt cmdManhunt = new CommandManhunt(this);
        getCommand("manhuntstart").setExecutor(cmdManhunt);
        getCommand("secondrunner").setExecutor(cmdManhunt);
        getCommand("timer").setExecutor(cmdManhunt);
        getCommand("tabhearts").setExecutor(cmdManhunt);
        getCommand("activatorbar").setExecutor(cmdManhunt);

        // Tools Commands (Seed, Compass, Bedbomb) - HIER WAR DER FEHLER
        CommandTools cmdTools = new CommandTools(this);
        getCommand("TrackingCompass").setExecutor(cmdTools);
        getCommand("bedbomb").setExecutor(cmdTools);
        getCommand("setseed").setExecutor(cmdTools);

        // Save & Reset Commands
        CommandSaveLoad cmdSaveLoad = new CommandSaveLoad(this);
        getCommand("save").setExecutor(cmdSaveLoad);
        getCommand("save").setTabCompleter(cmdSaveLoad);
        getCommand("worldreset").setExecutor(new CommandWorldReset(this));
        getCommand("worldclear").setExecutor(new CommandWorldReset(this));

        // 3. Listener registrieren
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);

        getLogger().info("FyluManhunt v1.21.8 geladen!");

        // 4. Initiale Aktionen
        worldManager.teleportToLobbyForAll();

        // 5. Crash Recovery Check (Verzögert, damit Welten laden können)
        getServer().getScheduler().runTaskLater(this, () -> {
            gameManager.tryRestoreGame();
        }, 40L);
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isGameRunning()) {
            // Beim Stop nochmal speichern für alle Fälle
            gameManager.saveCrashRecoveryFile();
        }
    }

    // --- Getter ---

    public static FyluManhunt getInstance() { return instance; }
    public GameManager getGameManager() { return gameManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public CompassManager getCompassManager() { return compassManager; }
    public fylu.fyluManhunt.manager.ScoreboardManager getScoreboardManager() { return scoreboardManager; }
}