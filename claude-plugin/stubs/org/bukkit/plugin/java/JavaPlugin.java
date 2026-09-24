// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.plugin.java;
public abstract class JavaPlugin implements org.bukkit.plugin.Plugin {
    public void onEnable() {}
    public void onDisable() {}
    public org.bukkit.configuration.file.FileConfiguration getConfig() { return null; }
    public void saveDefaultConfig() {}
    public void reloadConfig() {}
    public final org.bukkit.Server getServer() { return null; }
    public java.util.logging.Logger getLogger() { return null; }
    public org.bukkit.command.PluginCommand getCommand(String name) { return null; }
}
