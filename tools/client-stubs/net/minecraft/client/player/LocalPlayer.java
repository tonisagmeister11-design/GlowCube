package net.minecraft.client.player;
public class LocalPlayer extends net.minecraft.world.entity.player.Player {
    public boolean horizontalCollision;
    public boolean isUsingItem() { return false; }
    public float getAttackStrengthScale(float f) { return 0f; }
    public void resetAttackStrengthTicker() {}
    public net.minecraft.world.item.ItemStack getMainHandItem() { return null; }
}
