// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.configuration;
public interface ConfigurationSection {
    java.util.Set<String> getKeys(boolean deep);
    String getString(String path);
    double getDouble(String path);
    int getInt(String path);
    ConfigurationSection getConfigurationSection(String path);
}
