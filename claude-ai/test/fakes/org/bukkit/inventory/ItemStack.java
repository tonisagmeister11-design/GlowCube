package org.bukkit.inventory;
public class ItemStack implements Cloneable {
    final org.bukkit.Material type; int amount;
    public ItemStack(org.bukkit.Material m, int amount) { this.type = m; this.amount = amount; }
    public org.bukkit.Material getType() { return type; }
    public int getAmount() { return amount; }
    public void setAmount(int a) { amount = a; }
    public ItemStack clone() { return new ItemStack(type, amount); }
    public int getMaxStackSize() { return type.getMaxStackSize(); }
    public boolean isSimilar(ItemStack o) { return o != null && o.type == type; }
}
