package org.bukkit.scheduler;
public interface BukkitScheduler { BukkitTask runTaskTimer(org.bukkit.plugin.Plugin p,Runnable r,long d,long period); BukkitTask runTask(org.bukkit.plugin.Plugin p,Runnable r); }
