/*
 * Decompiled with CFR 0.152.
 */
package com.glowcube.utils;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record Home(String name, UUID worldId, String worldName, double x, double y, double z, float yaw, float pitch) {
    public static Home of(String string, Location location) {
        World world = location.getWorld();
        return new Home(string, world.getUID(), world.getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location toLocation() {
        World world = Bukkit.getWorld((UUID)this.worldId);
        if (world == null) {
            world = Bukkit.getWorld((String)this.worldName);
        }
        if (world == null) {
            return null;
        }
        return new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
    }
}

