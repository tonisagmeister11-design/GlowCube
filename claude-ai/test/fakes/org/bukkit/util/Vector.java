package org.bukkit.util;
public class Vector implements Cloneable {
    double x, y, z;
    public Vector(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public double getX() { return x; } public double getY() { return y; } public double getZ() { return z; }
    public Vector subtract(Vector o) { x -= o.x; y -= o.y; z -= o.z; return this; }
    public Vector normalize() { double l = length(); if (l > 0) { x /= l; y /= l; z /= l; } return this; }
    public Vector multiply(double m) { x *= m; y *= m; z *= m; return this; }
    public double length() { return Math.sqrt(x*x + y*y + z*z); }
    public Vector setY(double v) { y = v; return this; }
    public Vector clone() { return new Vector(x, y, z); }
}
