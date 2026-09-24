// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.plugin;
public interface PluginManager { void registerEvents(org.bukkit.event.Listener l, Plugin p); void callEvent(org.bukkit.event.Event e); }
