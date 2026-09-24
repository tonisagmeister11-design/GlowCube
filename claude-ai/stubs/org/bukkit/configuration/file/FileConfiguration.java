// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.configuration.file;
public abstract class FileConfiguration {
    public String getString(String p, String d) { return d; }
    public int getInt(String p, int d) { return d; }
    public boolean getBoolean(String p, boolean d) { return d; }
    public double getDouble(String p, double d) { return d; }
    public java.util.List<String> getStringList(String p) { return null; }
    public java.util.List<?> getList(String p) { return null; }
    public org.bukkit.configuration.ConfigurationSection getConfigurationSection(String p) { return null; }
    public void set(String p, Object v) {}
    public boolean contains(String p) { return false; }
    public void save(java.io.File f) throws java.io.IOException {}
}
