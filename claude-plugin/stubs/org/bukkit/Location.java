// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit;
public class Location implements Cloneable {
    public Location clone() { return null; }
    public Location add(double x, double y, double z) { return this; }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
    public float getYaw() { return 0; }
    public void setYaw(float yaw) {}
    public void setPitch(float pitch) {}
    public org.bukkit.block.Block getBlock() { return null; }
}
