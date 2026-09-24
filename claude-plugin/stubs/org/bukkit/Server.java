// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit;
public interface Server {
    org.bukkit.plugin.messaging.Messenger getMessenger();
    org.bukkit.plugin.PluginManager getPluginManager();
    java.util.Collection<? extends org.bukkit.entity.Player> getOnlinePlayers();
    org.bukkit.entity.Player getPlayerExact(String name);
}
