package de.gtacity.item;

import de.gtacity.gameplay.Economy;
import de.gtacity.registry.ModComponents;
import de.gtacity.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Dropped money. It goes straight into the wallet as soon as it is picked up. */
public class CashItem extends Item {
    public CashItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(int dollars) {
        ItemStack stack = new ItemStack(ModItems.CASH);
        stack.set(ModComponents.CASH_VALUE, dollars);
        return stack;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (entity instanceof ServerPlayer player && !stack.isEmpty()) {
            int value = stack.getOrDefault(ModComponents.CASH_VALUE, 10) * stack.getCount();
            stack.setCount(0);
            Economy.add(player, value);
            player.displayClientMessage(Component.literal("+" + Economy.format(value))
                    .withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }
}
