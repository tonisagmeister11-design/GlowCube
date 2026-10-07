package de.theboys.item;

import java.util.function.Consumer;

import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import de.theboys.power.PowerManager;
import de.theboys.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Compound V, V-One and the uranium injector. Hold use (or press the inject key) to put the
 * needle in your arm; the animation is played client side, the effect happens when it is done.
 */
public class SyringeItem extends Item {
	public enum Kind {
		COMPOUND_V(0x3FA9FF),
		V_ONE(0xBFE6FF),
		URANIUM(0x7CFF4F);

		public final int color;

		Kind(int color) {
			this.color = color;
		}
	}

	public static final int INJECT_TICKS = 36;

	private final Kind kind;

	public SyringeItem(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		Power current = PowerAttachments.powerOf(player);
		de.theboys.TheBoys.LOGGER.info("syringe use: kind={} client={} power={}", kind, level.isClientSide(), current);
		if (kind == Kind.URANIUM ? current == Power.NONE : current != Power.NONE) {
			if (player instanceof ServerPlayer sp) {
				sp.sendSystemMessage(Component.translatable(kind == Kind.URANIUM
						? "message.theboys.nothing_to_remove" : "message.theboys.already_powered").withStyle(ChatFormatting.RED), true);
			}
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.6f, 1.6f);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return INJECT_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) {
			return stack;
		}
		Power current = PowerAttachments.powerOf(player);
		switch (kind) {
			case COMPOUND_V, V_ONE -> {
				if (current != Power.NONE) return stack;
				Power[] pool = kind == Kind.V_ONE ? Power.V_ONE_POOL : Power.COMPOUND_V_POOL;
				Power power = pool[server.getRandom().nextInt(pool.length)];
				PowerManager.setPower(player, power);
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
				server.sendParticles(new DustParticleOptions(kind.color, 1.5f), player.getX(), player.getY() + 1, player.getZ(), 60, 0.5, 0.8, 0.5, 0.1);
				player.sendSystemMessage(Component.translatable("message.theboys.got_power",
						Component.translatable(power.translationKey()).withColor(power.color()).withStyle(ChatFormatting.BOLD)));
				player.sendSystemMessage(Component.translatable("message.theboys.keys_hint").withStyle(ChatFormatting.GRAY));
			}
			case URANIUM -> {
				if (current == Power.NONE) return stack;
				PowerManager.setPower(player, Power.NONE);
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 1));
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
				server.sendParticles(new DustParticleOptions(kind.color, 1.5f), player.getX(), player.getY() + 1, player.getZ(), 60, 0.5, 0.8, 0.5, 0.1);
				player.sendSystemMessage(Component.translatable("message.theboys.power_removed").withStyle(ChatFormatting.GREEN));
			}
			default -> { }
		}
		if (player.isCreative()) {
			return stack;
		}
		stack.shrink(1);
		ItemStack empty = new ItemStack(ModItems.EMPTY_SYRINGE);
		if (stack.isEmpty()) {
			return empty;
		}
		if (!player.getInventory().add(empty)) {
			server.addFreshEntity(new ItemEntity(server, player.getX(), player.getY(), player.getZ(), empty));
		}
		return stack;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		String key = switch (kind) {
			case COMPOUND_V -> "item.theboys.compound_v.desc";
			case V_ONE -> "item.theboys.compound_v1.desc";
			case URANIUM -> "item.theboys.uranium_injector.desc";
		};
		tooltip.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.theboys.syringe.how").withStyle(ChatFormatting.DARK_GRAY));
	}
}
