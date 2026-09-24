// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.configuration.file;
public abstract class FileConfiguration {
    public String getString(String path, String def) { return def; }
    public boolean getBoolean(String path, boolean def) { return def; }
}
