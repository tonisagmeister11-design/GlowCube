/*
 * Decompiled with CFR 0.152.
 */
package com.glowcube.utils;

import com.glowcube.utils.HomeManager;
import com.glowcube.utils.Msg;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public class SidebarManager {
    private static final String OBJECTIVE = "glowcube";
    private final HomeManager homes;
    private final Map<UUID, Scoreboard> boards = new HashMap<UUID, Scoreboard>();

    public SidebarManager(HomeManager homeManager) {
        this.homes = homeManager;
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.update(player);
        }
    }

    public void update(Player player) {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) {
            return;
        }
        Scoreboard scoreboard = this.boards.computeIfAbsent(player.getUniqueId(), uUID -> scoreboardManager.getNewScoreboard());
        Objective objective = scoreboard.getObjective(OBJECTIVE);
        if (objective != null) {
            objective.unregister();
        }
        Objective objective2 = scoreboard.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Msg.mm("<gradient:#55FFFF:#5555FF><bold>\u2726 3A SMP \u2726</bold></gradient>", new TagResolver[0]));
        objective2.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective2.numberFormat(NumberFormat.blank());
        int n = Bukkit.getOnlinePlayers().size();
        int n2 = this.homes.getHomes(player.getUniqueId()).size();
        int n3 = this.homes.getMaxHomes();
        Object object = n3 > 0 ? n2 + "<dark_gray>/" + n3 : String.valueOf(n2);
        List<Component> list = List.of(Component.empty(), Msg.mm("<gray>\u00bb <white>Spieler: <aqua>" + n, new TagResolver[0]), Msg.mm("<gray>\u00bb <white>Homes: <aqua>" + (String)object, new TagResolver[0]), Component.empty(), Msg.mm("<dark_gray>/sethome <gray>\u00b7 <dark_gray>/home", new TagResolver[0]), Component.empty(), Msg.mm("<gray>Du: <yellow>" + player.getName(), new TagResolver[0]));
        int n4 = list.size();
        for (int i = 0; i < list.size(); ++i) {
            String string = "\u00a7" + Integer.toHexString(i) + "\u00a7r";
            Score score = objective2.getScore(string);
            score.setScore(n4--);
            score.customName(list.get(i));
        }
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }
    }

    public void remove(Player player) {
        this.boards.remove(player.getUniqueId());
    }

    public void clearAll() {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!this.boards.containsKey(player.getUniqueId())) continue;
                player.setScoreboard(scoreboardManager.getMainScoreboard());
            }
        }
        this.boards.clear();
    }
}

