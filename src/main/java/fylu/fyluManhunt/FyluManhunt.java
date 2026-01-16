package fylu.fyluManhunt;

import fylu.fyluManhunt.commands.*;
import fylu.fyluManhunt.listeners.*;
import fylu.fyluManhunt.manager.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class FyluManhunt extends JavaPlugin {

    private static FyluManhunt instance;
    private GameManager gameManager;
    private WorldManager worldManager;
    private CompassManager compassManager;
    private ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        instance = this;

        this.worldManager = new WorldManager(this);
        this.compassManager = new CompassManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.gameManager = new GameManager(this);

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ManhuntExpansion(this).register();
        }

        // Commands registrieren
        getCommand("game").setExecutor(new CommandGame(this));

        CommandManhunt cmdManhunt = new CommandManhunt(this);
        getCommand("manhuntstart").setExecutor(cmdManhunt);
        getCommand("secondrunner").setExecutor(cmdManhunt);
        getCommand("timer").setExecutor(cmdManhunt);
        getCommand("tabhearts").setExecutor(cmdManhunt);
        getCommand("activatorbar").setExecutor(cmdManhunt);

        CommandTools cmdTools = new CommandTools(this);
        getCommand("TrackingCompass").setExecutor(cmdTools);
        getCommand("bedbomb").setExecutor(cmdTools);
        getCommand("setseed").setExecutor(cmdTools);

        getCommand("save").setExecutor(new CommandSave(this));
        CommandLoad cmdLoad = new CommandLoad(this);
        getCommand("load").setExecutor(cmdLoad);
        getCommand("load").setTabCompleter(cmdLoad);

        getCommand("worldreset").setExecutor(new CommandWorldReset(this));
        getCommand("worldclear").setExecutor(new CommandWorldReset(this));

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);

        worldManager.teleportToLobbyForAll();

        getServer().getScheduler().runTaskLater(this, () -> {
            gameManager.tryRestoreGame();
        }, 40L);
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isGameRunning()) {
            gameManager.saveCrashRecoveryFile();
        }
    }

    public static FyluManhunt getInstance() { return instance; }
    public GameManager getGameManager() { return gameManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public CompassManager getCompassManager() { return compassManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
}