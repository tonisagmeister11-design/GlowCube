package org.bukkit.plugin.java;
public abstract class JavaPlugin implements org.bukkit.plugin.Plugin {
    final org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
    public void onEnable() {} public void onDisable() {}
    public org.bukkit.configuration.file.FileConfiguration getConfig() { return config; }
    public void saveDefaultConfig() {} public void reloadConfig() {}
    public final java.io.File getDataFolder() { return new java.io.File(System.getProperty("java.io.tmpdir"), "claudeai-test"); }
    public java.util.logging.Logger getLogger() { return java.util.logging.Logger.getLogger("ClaudeAI"); }
    public org.bukkit.command.PluginCommand getCommand(String n) { return null; }
}
