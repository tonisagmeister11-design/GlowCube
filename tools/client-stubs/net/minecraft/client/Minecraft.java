package net.minecraft.client;
public class Minecraft {
    public Options options;
    public net.minecraft.world.phys.HitResult hitResult;
    public net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode;
    public net.minecraft.client.player.LocalPlayer player;
    public static Minecraft getInstance() { return null; }
    public net.minecraft.client.server.IntegratedServer getSingleplayerServer() { return null; }
}
