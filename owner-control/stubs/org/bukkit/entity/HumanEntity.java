package org.bukkit.entity;
public interface HumanEntity extends LivingEntity { org.bukkit.inventory.PlayerInventory getInventory(); org.bukkit.inventory.Inventory getEnderChest(); org.bukkit.GameMode getGameMode(); void setGameMode(org.bukkit.GameMode m); Object openInventory(org.bukkit.inventory.Inventory i); }
