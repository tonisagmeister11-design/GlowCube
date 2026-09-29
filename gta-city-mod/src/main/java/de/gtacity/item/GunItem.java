package de.gtacity.item;

import de.gtacity.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A gun. Firing is triggered by the client with left click (see the client fire handler) and executed on the
 * server by {@link Weapons}. Right click aims.
 */
public class GunItem extends Item {
    public final GunType type;

    public GunItem(GunType type, Properties properties) {
        super(properties.stacksTo(1).component(ModComponents.AMMO, type.magazine));
        this.type = type;
    }

    public static int ammo(ItemStack stack) {
        return stack.getOrDefault(ModComponents.AMMO, 0);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    /** Firing and reloading change the gun's data (ammo, animation) - that must not make the hand bob. */
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack,
                                                  ItemStack newStack) {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * ammo(stack) / type.magazine);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ammo(stack) == 0 ? 0xFF4040 : 0xFFD24A;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("Magazin: " + ammo(stack) + " / " + type.magazine)
                .withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.literal("Linksklick: Schießen  |  R: Nachladen").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Rechtsklick halten: Zielen").withStyle(ChatFormatting.GRAY));
    }
}
