// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.scheduler;
public interface BukkitScheduler {
    BukkitTask runTaskTimer(org.bukkit.plugin.Plugin p, Runnable r, long delay, long period);
    BukkitTask runTask(org.bukkit.plugin.Plugin p, Runnable r);
    BukkitTask runTaskLater(org.bukkit.plugin.Plugin p, Runnable r, long delay);
}
