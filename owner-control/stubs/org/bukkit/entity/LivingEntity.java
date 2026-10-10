package org.bukkit.entity;
public interface LivingEntity extends Entity { double getHealth(); void setHealth(double h); int getRemainingAir(); void setRemainingAir(int a); boolean addPotionEffect(org.bukkit.potion.PotionEffect e); void removePotionEffect(org.bukkit.potion.PotionEffectType t); }
