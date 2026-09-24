// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit;
public class Location implements Cloneable {
    public Location(World w, double x, double y, double z) {}
    public Location(World w, double x, double y, double z, float yaw, float pitch) {}
    public World getWorld() { return null; }
    public int getBlockX() { return 0; }
    public int getBlockY() { return 0; }
    public int getBlockZ() { return 0; }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
    public float getYaw() { return 0; }
    public float getPitch() { return 0; }
    public void setYaw(float y) {}
    public void setPitch(float p) {}
    public Location clone() { return null; }
    public Location add(double x, double y, double z) { return null; }
    public Location add(org.bukkit.util.Vector v) { return null; }
    public Location subtract(double x, double y, double z) { return null; }
    public org.bukkit.util.Vector toVector() { return null; }
    public org.bukkit.util.Vector getDirection() { return null; }
    public double distance(Location o) { return 0; }
    public double distanceSquared(Location o) { return 0; }
    public org.bukkit.block.Block getBlock() { return null; }
}
