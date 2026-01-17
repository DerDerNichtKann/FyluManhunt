package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerStateManager {

    public static void resetPlayerFull(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setExp(0);
        p.setLevel(0);
        p.setGameMode(GameMode.SURVIVAL);
        p.getActivePotionEffects().forEach(eff -> p.removePotionEffect(eff.getType()));

        Iterator<Advancement> iterator = Bukkit.getServer().advancementIterator();
        while (iterator.hasNext()) {
            Advancement progress = iterator.next();
            AdvancementProgress pProgress = p.getAdvancementProgress(progress);
            for (String criteria : pProgress.getAwardedCriteria()) {
                pProgress.revokeCriteria(criteria);
            }
        }

        for (Statistic stat : Statistic.values()) {
            try {
                if (stat.getType() == Statistic.Type.UNTYPED) {
                    p.setStatistic(stat, 0);
                } else if (stat.getType() == Statistic.Type.ENTITY) {
                    for (EntityType type : EntityType.values()) {
                        if (type.isAlive()) try { p.setStatistic(stat, type, 0); } catch (Exception ignored) {}
                    }
                } else if (stat.getType() == Statistic.Type.ITEM || stat.getType() == Statistic.Type.BLOCK) {
                    for (Material mat : Material.values()) {
                        try { p.setStatistic(stat, mat, 0); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public static void savePlayerStates(File saveFile) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Player p : Bukkit.getOnlinePlayers()) {
            saveSinglePlayerToConfig(p, cfg);
        }
        try { cfg.save(saveFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // Hilfsmethode zum Speichern
    private static void saveSinglePlayerToConfig(Player p, YamlConfiguration cfg) {
        String path = p.getUniqueId().toString();
        cfg.set(path + ".loc", p.getLocation());
        cfg.set(path + ".health", p.getHealth());
        cfg.set(path + ".food", p.getFoodLevel());
        cfg.set(path + ".gamemode", p.getGameMode().toString());
        cfg.set(path + ".inv", p.getInventory().getContents());
        cfg.set(path + ".armor", p.getInventory().getArmorContents());
        cfg.set(path + ".exp", p.getExp());
        cfg.set(path + ".level", p.getLevel());

        List<String> awardedCriteria = new ArrayList<>();
        Iterator<Advancement> it = Bukkit.getServer().advancementIterator();
        while (it.hasNext()) {
            Advancement adv = it.next();
            try {
                AdvancementProgress prog = p.getAdvancementProgress(adv);
                Collection<String> criteria = prog.getAwardedCriteria();
                if (!criteria.isEmpty()) {
                    for (String c : criteria) {
                        awardedCriteria.add(adv.getKey().toString() + ";" + c);
                    }
                }
            } catch (Exception ignored) {}
        }
        cfg.set(path + ".advancements_criteria", awardedCriteria);

        for (Statistic stat : Statistic.values()) {
            try {
                if (stat.getType() == Statistic.Type.UNTYPED) {
                    int val = p.getStatistic(stat);
                    if (val > 0) cfg.set(path + ".stats.generic." + stat.name(), val);
                }
                else if (stat.getType() == Statistic.Type.ENTITY) {
                    for (EntityType type : EntityType.values()) {
                        if (type == EntityType.UNKNOWN) continue;
                        try {
                            int val = p.getStatistic(stat, type);
                            if (val > 0) cfg.set(path + ".stats.entity." + stat.name() + "." + type.name(), val);
                        } catch (Exception ignored) {}
                    }
                }
                else if (stat.getType() == Statistic.Type.ITEM || stat.getType() == Statistic.Type.BLOCK) {
                    for (Material mat : Material.values()) {
                        try {
                            int val = p.getStatistic(stat, mat);
                            if (val > 0) cfg.set(path + ".stats.mat." + stat.name() + "." + mat.name(), val);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public static void loadSinglePlayerState(Player p, File saveFile) {
        if (!saveFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
        String path = p.getUniqueId().toString();
        if (!cfg.contains(path)) {
            return;
        }
        resetPlayerFull(p);

        try {
            if(cfg.contains(path + ".loc")) p.teleport(cfg.getLocation(path + ".loc"));
            p.setHealth(cfg.getDouble(path + ".health", 20));
            p.setFoodLevel(cfg.getInt(path + ".food", 20));
            p.setGameMode(GameMode.valueOf(cfg.getString(path + ".gamemode", "SURVIVAL")));

            List<?> inv = cfg.getList(path + ".inv");
            if (inv != null) p.getInventory().setContents(inv.toArray(new ItemStack[0]));

            List<?> armor = cfg.getList(path + ".armor");
            if (armor != null) p.getInventory().setArmorContents(armor.toArray(new ItemStack[0]));

            p.setExp((float) cfg.getDouble(path + ".exp"));
            p.setLevel(cfg.getInt(path + ".level"));

            List<String> criteriaList = cfg.getStringList(path + ".advancements_criteria");
            for (String entry : criteriaList) {
                try {
                    String[] parts = entry.split(";");
                    if (parts.length >= 2) {
                        String keyString = parts[0];
                        String criteria = entry.substring(keyString.length() + 1);

                        NamespacedKey key = NamespacedKey.fromString(keyString);
                        if (key != null) {
                            Advancement adv = Bukkit.getAdvancement(key);
                            if (adv != null) {
                                p.getAdvancementProgress(adv).awardCriteria(criteria);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (cfg.isConfigurationSection(path + ".stats.generic")) {
                ConfigurationSection sec = cfg.getConfigurationSection(path + ".stats.generic");
                for (String key : sec.getKeys(false)) {
                    try {
                        p.setStatistic(Statistic.valueOf(key), sec.getInt(key));
                    } catch (Exception ignored) {}
                }
            }
            if (cfg.isConfigurationSection(path + ".stats.entity")) {
                ConfigurationSection secMain = cfg.getConfigurationSection(path + ".stats.entity");
                for (String statKey : secMain.getKeys(false)) {
                    ConfigurationSection secType = secMain.getConfigurationSection(statKey);
                    for (String typeKey : secType.getKeys(false)) {
                        try {
                            p.setStatistic(Statistic.valueOf(statKey), EntityType.valueOf(typeKey), secType.getInt(typeKey));
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (cfg.isConfigurationSection(path + ".stats.mat")) {
                ConfigurationSection secMain = cfg.getConfigurationSection(path + ".stats.mat");
                for (String statKey : secMain.getKeys(false)) {
                    ConfigurationSection secMat = secMain.getConfigurationSection(statKey);
                    for (String matKey : secMat.getKeys(false)) {
                        try {
                            p.setStatistic(Statistic.valueOf(statKey), Material.valueOf(matKey), secMat.getInt(matKey));
                        } catch (Exception ignored) {}
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadPlayerStates(File saveFile) {
        if (!saveFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
        for (Player p : Bukkit.getOnlinePlayers()) {
            loadSinglePlayerState(p, saveFile);
        }
    }
}