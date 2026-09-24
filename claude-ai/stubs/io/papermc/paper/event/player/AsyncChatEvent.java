// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package io.papermc.paper.event.player;
public abstract class AsyncChatEvent extends org.bukkit.event.Event {
    public org.bukkit.entity.Player getPlayer() { return null; }
    public net.kyori.adventure.text.Component message() { return null; }
}
