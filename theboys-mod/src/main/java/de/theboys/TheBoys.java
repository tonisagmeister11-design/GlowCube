package de.theboys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.theboys.command.TheBoysCommand;
import de.theboys.net.ModNetworking;
import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import de.theboys.power.PowerManager;
import de.theboys.registry.ModBlocks;
import de.theboys.registry.ModEntities;
import de.theboys.registry.ModItems;
import de.theboys.time.TimeRewind;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;

public class TheBoys implements ModInitializer {
	public static final String MOD_ID = "theboys";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PowerAttachments.init();
		ModBlocks.init();
		ModItems.init();
		ModEntities.init();
		ModNetworking.init();

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			PowerManager.tick(server);
			TimeRewind.tick(server);
			de.theboys.power.MiniMaus.tickLaunches();
		});
		// MiniMaus: Poison Bite and To the Moon ride on her normal hits
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && PowerAttachments.powerOf(sp) == Power.BLACK_NOIR) {
				de.theboys.power.BlackNoir.onAttack(sp, target);
			}
			if (!level.isClientSide() && player instanceof ServerPlayer sp && PowerAttachments.powerOf(sp) == Power.MINIMAUS
					&& de.theboys.power.MiniMaus.onAttack(sp, target)) {
				return net.minecraft.world.InteractionResult.FAIL;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		ServerPlayerEvents.JOIN.register(PowerManager::applyPassives);
		ServerPlayerEvents.LEAVE.register(PowerManager::forget);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PowerManager.forget(oldPlayer);
			PowerManager.applyPassives(newPlayer);
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// MiniMaus is winding up To the Moon on this one
			if (de.theboys.power.MiniMaus.blocksDamage(entity)) return false;
			if (!(entity instanceof ServerPlayer player)) return true;
			Power power = PowerAttachments.powerOf(player);
			// Homelander does not burn
			if (power == Power.HOMELANDER && source.is(DamageTypeTags.IS_FIRE)) return false;
			// nobody is hurt by his own nuke / explosions
			if (power != Power.NONE && source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == player) return false;
			// Butcher's tendrils catch him
			if (power == Power.BUTCHER && source.is(DamageTypeTags.IS_FALL) && PowerAttachments.active(player).has(ActiveState.CANCER_WALK)) return false;
			// A-Train does not trip over his own feet while running
			if (power == Power.A_TRAIN && source.is(DamageTypeTags.IS_FALL) && PowerAttachments.active(player).has(ActiveState.SPEED)) return false;
			// Starlight drinks lightning, Stormfront is made of it
			if (source.is(DamageTypeTags.IS_LIGHTNING) && (power == Power.STARLIGHT || power == Power.STORMFRONT)) {
				// the bolt set them alight just before it hit
				player.clearFire();
				if (power == Power.STARLIGHT) de.theboys.power.Starlight.absorbLightning(player);
				return false;
			}
			// ... but fire burns her twice as badly
			if (power == Power.STORMFRONT && source.is(DamageTypeTags.IS_FIRE)) return de.theboys.power.Stormfront.fireHit(player, source, amount);
			// fliers do not crash
			if ((power == Power.STARLIGHT || power == Power.STORMFRONT) && source.is(DamageTypeTags.IS_FALL) && PowerManager.session(player).flying) return false;
			// The Deep comes from the deep sea: cold does not bother him
			if (power == Power.THE_DEEP && source.is(DamageTypeTags.IS_FREEZING)) return false;
			// Super Cancer
			if (power == Power.BUTCHER) return de.theboys.power.Butcher.allowDamage(player, source, amount);
			return true;
		});

		// Black Noir never says a word while he wears the suit
		net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			de.theboys.power.PowerData d = PowerAttachments.power(sender);
			if (d.power() == Power.BLACK_NOIR && d.suit()) {
				sender.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.theboys.noir_silent")
						.withStyle(net.minecraft.ChatFormatting.DARK_GRAY), true);
				return false;
			}
			return true;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> TheBoysCommand.register(dispatcher));
		LOGGER.info("Compound V loaded. Never meet your heroes.");
	}
}
