// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.command;
public interface CommandSender {
    void sendMessage(String message);
    boolean hasPermission(String name);
    String getName();
}
