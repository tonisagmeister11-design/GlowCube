// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit;
public abstract class Bukkit {
    public static java.util.Collection<? extends org.bukkit.entity.Player> getOnlinePlayers() { return null; }
    public static org.bukkit.entity.Player getPlayerExact(String name) { return null; }
    public static org.bukkit.entity.Player getPlayer(java.util.UUID id) { return null; }
    public static org.bukkit.scheduler.BukkitScheduler getScheduler() { return null; }
    public static org.bukkit.plugin.PluginManager getPluginManager() { return null; }
    public static org.bukkit.command.ConsoleCommandSender getConsoleSender() { return null; }
    public static boolean dispatchCommand(org.bukkit.command.CommandSender sender, String line) { return false; }
    public static org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size, net.kyori.adventure.text.Component title) { return null; }
    public static org.bukkit.block.data.BlockData createBlockData(String data) { return null; }
    public static java.util.List<org.bukkit.World> getWorlds() { return null; }
    public static org.bukkit.World getWorld(String name) { return null; }
    public static org.bukkit.World getWorld(java.util.UUID id) { return null; }
}
