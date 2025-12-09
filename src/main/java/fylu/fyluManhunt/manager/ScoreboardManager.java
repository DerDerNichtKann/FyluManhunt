package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class ScoreboardManager {

    private final FyluManhunt plugin;
    private boolean showHearts = true;
    private BukkitTask task;

    public ScoreboardManager(FyluManhunt plugin) {
        this.plugin = plugin;
    }

    public void startHeartTask() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateTabHearts, 0L, 10L);
    }

    public void updateTabHearts() {
        if (!showHearts || !plugin.getGameManager().isGameRunning()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setPlayerListName(p.getName());
            }
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (plugin.getGameManager().isRunner(p)) {
                double health = p.getHealth();
                int heartCount = (int) Math.ceil(health / 2.0);

                // Visualisierung mit Herzen statt Zahlen
                StringBuilder heartsVisual = new StringBuilder();

                ChatColor color = (health <= 6) ? ChatColor.RED : (health <= 14 ? ChatColor.YELLOW : ChatColor.GREEN);
                heartsVisual.append(color);

                for(int i = 0; i < heartCount; i++) {
                    heartsVisual.append("❤");
                }

                String newName = p.getName() + " " + heartsVisual.toString();
                p.setPlayerListName(newName);
            } else {
                p.setPlayerListName(p.getName());
            }
        }
    }

    public void setShowHearts(boolean show) {
        this.showHearts = show;
        updateTabHearts();
    }

    public boolean isShowingHearts() { return showHearts; }
}