package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class ScoreboardManager {

    private final FyluManhunt plugin;
    private boolean showHearts = true;
    private BukkitTask task;
    private Scoreboard board;
    private Team teamRunners;
    private Team teamHunters;

    public ScoreboardManager(FyluManhunt plugin) {
        this.plugin = plugin;
        setupScoreboard();
    }

    private void setupScoreboard() {
        board = Bukkit.getScoreboardManager().getMainScoreboard();

        teamRunners = board.getTeam("MH_Runners");
        if (teamRunners == null) teamRunners = board.registerNewTeam("MH_Runners");
        teamRunners.setColor(ChatColor.RED);
        teamRunners.setPrefix(ChatColor.RED + "[R] ");
        teamRunners.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        teamHunters = board.getTeam("MH_Hunters");
        if (teamHunters == null) teamHunters = board.registerNewTeam("MH_Hunters");
        teamHunters.setColor(ChatColor.GREEN);
        teamHunters.setPrefix(ChatColor.GREEN + "[H] ");
        teamHunters.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
    }

    public void startHeartTask() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateTab, 0L, 20L);
    }

    public void updateTab() {
        if (!plugin.getGameManager().isGameRunning()) return;

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (plugin.getGameManager().isRunner(p)) {
                if (!teamRunners.hasEntry(p.getName())) teamRunners.addEntry(p.getName());
            } else {
                if (!teamHunters.hasEntry(p.getName())) teamHunters.addEntry(p.getName());
            }

            if (showHearts && plugin.getGameManager().isRunner(p)) {
                int hp = (int) Math.ceil(p.getHealth() / 2.0);
                StringBuilder hearts = new StringBuilder(" " + ChatColor.RED);
                for(int i=0; i<hp; i++) hearts.append("❤");
                p.setPlayerListName(ChatColor.RED + p.getName() + hearts.toString());
            } else {
                p.setPlayerListName(null);
            }
        }
    }

    public void clearTeams() {
        if(teamRunners != null) teamRunners.unregister();
        if(teamHunters != null) teamHunters.unregister();
        setupScoreboard();
        for(Player p : Bukkit.getOnlinePlayers()) p.setPlayerListName(null);
    }

    public void setShowHearts(boolean show) {
        this.showHearts = show;
        if(!show) {
            for(Player p : Bukkit.getOnlinePlayers()) p.setPlayerListName(null);
        }
    }

    public boolean isShowingHearts() { return showHearts; }
}