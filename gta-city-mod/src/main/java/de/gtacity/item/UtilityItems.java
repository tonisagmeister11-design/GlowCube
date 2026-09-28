package de.gtacity.item;

import de.gtacity.entity.GrenadeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Grenade, body armor and medkit. */
public final class UtilityItems {
    private UtilityItems() {
    }

    public static class Grenade extends Item {
        public Grenade(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                    SoundSource.PLAYERS, 0.6F, 0.6F);
            if (!level.isClientSide()) {
                GrenadeEntity grenade = new GrenadeEntity(level, player);
                grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2F, 1.0F);
                level.addFreshEntity(grenade);
            }
            stack.consume(1, player);
            player.getCooldowns().addCooldown(stack, 20);
            return InteractionResult.SUCCESS;
        }
    }

    public static class BodyArmor extends Item {
        public BodyArmor(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60 * 10, 4));
                player.sendOverlayMessage(Component.literal("Schutzweste angelegt").withStyle(ChatFormatting.AQUA));
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }
    }

    public static class Medkit extends Item {
        public Medkit(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player.getHealth() >= player.getMaxHealth()) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                player.heal(20.0F);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }
    }
}
