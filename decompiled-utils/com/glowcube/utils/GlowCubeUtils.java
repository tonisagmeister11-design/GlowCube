/*
 * Decompiled with CFR 0.152.
 */
package com.glowcube.utils;

import com.glowcube.utils.HomeCommands;
import com.glowcube.utils.HomeManager;
import com.glowcube.utils.JoinListener;
import com.glowcube.utils.SidebarManager;
import com.glowcube.utils.TeleportManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class GlowCubeUtils
extends JavaPlugin {
    private HomeManager homeManager;
    private SidebarManager sidebarManager;
    private TeleportManager teleportManager;

    public void onEnable() {
        this.saveDefaultConfig();
        this.homeManager = new HomeManager(this);
        this.homeManager.load();
        this.sidebarManager = new SidebarManager(this.homeManager);
        this.teleportManager = new TeleportManager(this);
        this.getServer().getPluginManager().registerEvents((Listener)new JoinListener(this, this.sidebarManager), (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)this.teleportManager, (Plugin)this);
        HomeCommands homeCommands = new HomeCommands(this.homeManager, this.teleportManager, this.sidebarManager);
        for (String string : new String[]{"sethome", "home", "delhome", "homes", "movehome"}) {
            PluginCommand pluginCommand = this.getCommand(string);
            if (pluginCommand == null) {
                this.getLogger().warning("Command /" + string + " fehlt in plugin.yml");
                continue;
            }
            pluginCommand.setExecutor((CommandExecutor)homeCommands);
            pluginCommand.setTabCompleter((TabCompleter)homeCommands);
        }
        this.sidebarManager.updateAll();
        this.getLogger().info("GlowCubeUtils aktiv: 3A-SMP-Willkommensnachricht, Sidebar und Home-System geladen (" + this.homeManager.totalHomes() + " Homes).");
    }

    public void onDisable() {
        if (this.teleportManager != null) {
            this.teleportManager.cancelAll();
        }
        if (this.homeManager != null) {
            this.homeManager.save();
        }
        if (this.sidebarManager != null) {
            this.sidebarManager.clearAll();
        }
    }
}

