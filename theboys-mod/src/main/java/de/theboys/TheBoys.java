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
		});
		ServerPlayerEvents.JOIN.register(PowerManager::applyPassives);
		ServerPlayerEvents.LEAVE.register(PowerManager::forget);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PowerManager.forget(oldPlayer);
			PowerManager.applyPassives(newPlayer);
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player)) return true;
			Power power = PowerAttachments.powerOf(player);
			// Homelander does not burn
			if (power == Power.HOMELANDER && source.is(DamageTypeTags.IS_FIRE)) return false;
			// nobody is hurt by his own nuke / explosions
			if (power != Power.NONE && source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == player) return false;
			// A-Train does not trip over his own feet while running
			if (power == Power.A_TRAIN && source.is(DamageTypeTags.IS_FALL) && PowerAttachments.active(player).has(ActiveState.SPEED)) return false;
			return true;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> TheBoysCommand.register(dispatcher));
		LOGGER.info("Compound V loaded. Never meet your heroes.");
	}
}
