package org.bukkit;
public class Location implements Cloneable {
    private World w; private double x, y, z; private float yaw, pitch;
    public Location(World w, double x, double y, double z) { this(w, x, y, z, 0, 0); }
    public Location(World w, double x, double y, double z, float yaw, float pitch) { this.w = w; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; }
    public World getWorld() { return w; }
    public int getBlockX() { return (int) Math.floor(x); } public int getBlockY() { return (int) Math.floor(y); } public int getBlockZ() { return (int) Math.floor(z); }
    public double getX() { return x; } public double getY() { return y; } public double getZ() { return z; }
    public float getYaw() { return yaw; } public float getPitch() { return pitch; }
    public void setYaw(float v) { yaw = v; } public void setPitch(float v) { pitch = v; }
    public Location clone() { return new Location(w, x, y, z, yaw, pitch); }
    public Location add(double a, double b, double c) { x += a; y += b; z += c; return this; }
    public Location add(org.bukkit.util.Vector v) { return add(v.getX(), v.getY(), v.getZ()); }
    public Location subtract(double a, double b, double c) { return add(-a, -b, -c); }
    public org.bukkit.util.Vector toVector() { return new org.bukkit.util.Vector(x, y, z); }
    public org.bukkit.util.Vector getDirection() { double r = Math.toRadians(yaw); return new org.bukkit.util.Vector(-Math.sin(r), 0, Math.cos(r)); }
    public double distance(Location o) { return Math.sqrt(distanceSquared(o)); }
    public double distanceSquared(Location o) { double a = x - o.x, b = y - o.y, c = z - o.z; return a*a + b*b + c*c; }
    public org.bukkit.block.Block getBlock() { return w.getBlockAt(this); }
    public String toString() { return String.format("(%.1f %.1f %.1f)", x, y, z); }
}
