package org.bukkit;
public interface World { org.bukkit.block.Block getBlockAt(int x,int y,int z); void strikeLightning(Location l); void strikeLightningEffect(Location l); <T extends org.bukkit.entity.Entity> T spawn(Location l,Class<T> c,java.util.function.Consumer<? super T> f); }
