// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.entity;
public interface Player extends org.bukkit.command.CommandSender {
    String getName();
    org.bukkit.Location getLocation();
    boolean teleport(org.bukkit.Location location);
    void setGameMode(org.bukkit.GameMode mode);
    void hidePlayer(org.bukkit.plugin.Plugin plugin, Player player);
    void showPlayer(org.bukkit.plugin.Plugin plugin, Player player);
    void sendPluginMessage(org.bukkit.plugin.Plugin source, String channel, byte[] message);
}
