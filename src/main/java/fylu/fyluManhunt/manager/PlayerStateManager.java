package fylu.fyluManhunt.manager;

import fylu.fyluManhunt.FyluManhunt;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

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

        resetAdvancements(p);

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

    public static void resetAdvancements(Player player) {
        Bukkit.getScheduler().runTask(FyluManhunt.getInstance(), () -> {
            for (Advancement advancement : getAllAdvancement()) {
                AdvancementProgress progress = player.getAdvancementProgress(advancement);
                for (String criteria : progress.getAwardedCriteria()) {
                    progress.revokeCriteria(criteria);
                }
            }
        });
    }

    public static List<Advancement> getAllAdvancement() {
        List<Advancement> advancements = new ArrayList<>();
        for (@NotNull Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            advancements.add(it.next());
        }
        advancements.removeIf(advancement -> advancement.getDisplay() == null);
        return advancements;
    }

    public static void savePlayerStates(File saveFile) {
        YamlConfiguration cfg = new YamlConfiguration();

        for (Player p : Bukkit.getOnlinePlayers()) {
            String path = p.getUniqueId().toString();

            cfg.set(path + ".loc", p.getLocation());
            cfg.set(path + ".health", p.getHealth());
            cfg.set(path + ".food", p.getFoodLevel());
            cfg.set(path + ".gamemode", p.getGameMode().toString());
            cfg.set(path + ".inv", p.getInventory().getContents());
            cfg.set(path + ".armor", p.getInventory().getArmorContents());
            cfg.set(path + ".exp", p.getExp());
            cfg.set(path + ".level", p.getLevel());

            List<String> doneAdvancements = new ArrayList<>();
            for (Advancement adv : getAllAdvancement()) {
                if (p.getAdvancementProgress(adv).isDone()) {
                    doneAdvancements.add(adv.getKey().toString());
                }
            }
            cfg.set(path + ".advancements", doneAdvancements);

            cfg.set(path + ".stats.deaths", p.getStatistic(Statistic.DEATHS));
            cfg.set(path + ".stats.player_kills", p.getStatistic(Statistic.PLAYER_KILLS));
            cfg.set(path + ".stats.mob_kills", p.getStatistic(Statistic.MOB_KILLS));
        }

        try { cfg.save(saveFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public static void loadPlayerStates(File saveFile) {
        if (!saveFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);

        for (Player p : Bukkit.getOnlinePlayers()) {
            String path = p.getUniqueId().toString();
            if (!cfg.contains(path)) {
                resetPlayerFull(p);
                continue;
            }

            resetPlayerFull(p);

            try {
                p.teleport(cfg.getLocation(path + ".loc"));
                p.setHealth(cfg.getDouble(path + ".health"));
                p.setFoodLevel(cfg.getInt(path + ".food"));
                p.setGameMode(GameMode.valueOf(cfg.getString(path + ".gamemode")));

                List<?> inv = cfg.getList(path + ".inv");
                if (inv != null) p.getInventory().setContents(inv.toArray(new ItemStack[0]));

                List<?> armor = cfg.getList(path + ".armor");
                if (armor != null) p.getInventory().setArmorContents(armor.toArray(new ItemStack[0]));

                p.setExp((float) cfg.getDouble(path + ".exp"));
                p.setLevel(cfg.getInt(path + ".level"));

                List<String> advKeys = cfg.getStringList(path + ".advancements");
                for (String key : advKeys) {
                    Advancement adv = Bukkit.getAdvancement(org.bukkit.NamespacedKey.fromString(key));
                    if (adv != null) {
                        AdvancementProgress prog = p.getAdvancementProgress(adv);
                        for(String c : prog.getRemainingCriteria()) prog.awardCriteria(c);
                    }
                }

                p.setStatistic(Statistic.DEATHS, cfg.getInt(path + ".stats.deaths"));
                p.setStatistic(Statistic.PLAYER_KILLS, cfg.getInt(path + ".stats.player_kills"));
                p.setStatistic(Statistic.MOB_KILLS, cfg.getInt(path + ".stats.mob_kills"));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}