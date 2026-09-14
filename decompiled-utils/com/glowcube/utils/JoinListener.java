/*
 * Decompiled with CFR 0.152.
 */
package com.glowcube.utils;

import com.glowcube.utils.Msg;
import com.glowcube.utils.SidebarManager;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class JoinListener
implements Listener {
    private final JavaPlugin plugin;
    private final SidebarManager sidebar;
    private static final Title.Times TIMES = Title.Times.times((Duration)Duration.ofMillis(500L), (Duration)Duration.ofMillis(3000L), (Duration)Duration.ofMillis(1000L));

    public JoinListener(JavaPlugin javaPlugin, SidebarManager sidebarManager) {
        this.plugin = javaPlugin;
        this.sidebar = sidebarManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        Player player = playerJoinEvent.getPlayer();
        Component component = Msg.mm("<gradient:#55FFFF:#5555FF><bold>Willkommen</bold></gradient>", new TagResolver[0]);
        Component component2 = Msg.mm("<gray>auf dem <gradient:#55FFFF:#5555FF><bold>3A SMP</bold></gradient>", new TagResolver[0]);
        player.showTitle(Title.title((Component)component, (Component)component2, (Title.Times)TIMES));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        Msg.info((CommandSender)player, "Hey <aqua><name></aqua>! <yellow>/sethome</yellow> setzt dein Zuhause, <yellow>/home</yellow> bringt dich zurueck.", Msg.ph("name", player.getName()));
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, this.sidebar::updateAll, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        this.sidebar.remove(playerQuitEvent.getPlayer());
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, this.sidebar::updateAll, 1L);
    }
}

