// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.plugin.messaging;
public interface Messenger {
    void registerOutgoingPluginChannel(org.bukkit.plugin.Plugin plugin, String channel);
    PluginMessageListenerRegistration registerIncomingPluginChannel(org.bukkit.plugin.Plugin plugin, String channel, PluginMessageListener listener);
}
