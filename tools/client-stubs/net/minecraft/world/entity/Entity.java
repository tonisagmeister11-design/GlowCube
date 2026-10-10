package net.minecraft.world.entity;
public class Entity {
    public double getX() { return 0; }
    public double getZ() { return 0; }
    public float getYRot() { return 0; }
    public float getXRot() { return 0; }
    public void setYRot(float f) {}
    public void setXRot(float f) {}
    public boolean onGround() { return false; }
    public float getBbHeight() { return 0; }
    public double distanceToSqr(Entity e) { return 0; }
    public net.minecraft.world.phys.Vec3 position() { return null; }
    public net.minecraft.network.chat.Component getName() { return null; }
    public boolean isSpectator() { return false; }
    public boolean isAlive() { return true; }
}
