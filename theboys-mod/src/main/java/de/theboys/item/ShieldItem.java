package de.theboys.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Soldier Boy's shield. Blocks like a vanilla shield (BLOCKS_ATTACKS component); Soldier Boy can also throw it. */
public class ShieldItem extends net.minecraft.world.item.ShieldItem {
	public ShieldItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.theboys.soldier_boy_shield.desc").withStyle(ChatFormatting.GRAY));
	}
}
