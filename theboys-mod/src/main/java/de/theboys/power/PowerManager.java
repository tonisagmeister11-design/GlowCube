package de.theboys.power;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.theboys.TheBoys;
import de.theboys.net.AbilityKeyPayload;
import de.theboys.net.StatusPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Gives and takes powers, applies their passive boni and routes key presses to the right power. */
public final class PowerManager {
	private static final Map<UUID, PlayerSession> SESSIONS = new HashMap<>();

	private static final Identifier DAMAGE = TheBoys.id("power_damage");
	private static final Identifier ARMOR = TheBoys.id("power_armor");
	private static final Identifier TOUGHNESS = TheBoys.id("power_toughness");
	private static final Identifier KNOCKBACK = TheBoys.id("power_knockback");
	private static final Identifier FALL = TheBoys.id("power_fall");
	private static final Identifier HEALTH = TheBoys.id("power_health");
	private static final Identifier SPEED = TheBoys.id("power_speed");
	private static final Identifier SWIM = TheBoys.id("power_water");
	public static final Identifier DOUBLE_SPEED = TheBoys.id("minimaus_speed");
	public static final Identifier RUN = TheBoys.id("a_train_run");
	public static final Identifier STEP = TheBoys.id("a_train_step");

	private PowerManager() {
	}

	public static PlayerSession session(ServerPlayer player) {
		return SESSIONS.computeIfAbsent(player.getUUID(), u -> new PlayerSession());
	}

	public static void forget(ServerPlayer player) {
		PlayerSession s = SESSIONS.remove(player.getUUID());
		if (s != null) {
			stopEverything(player, s);
		}
	}

	/** Gives a power (or Power.NONE to take it away). */
	public static void setPower(ServerPlayer player, Power power) {
		PlayerSession s = session(player);
		stopEverything(player, s);
		SESSIONS.put(player.getUUID(), new PlayerSession());
		player.setAttached(PowerAttachments.POWER, PowerAttachments.power(player).withPower(power));
		PowerAttachments.setActive(player, ActiveState.IDLE);
		applyPassives(player);
	}

	public static void toggleSuit(ServerPlayer player) {
		PowerData d = PowerAttachments.power(player);
		if (d.power() == Power.NONE) return;
		player.setAttached(PowerAttachments.POWER, d.withSuit(!d.suit()));
		player.sendSystemMessage(Component.translatable(d.suit() ? "message.theboys.suit_off" : "message.theboys.suit_on").withStyle(ChatFormatting.GRAY), true);
	}

	/** Ends all running abilities (flight, held entities, speed ...). */
	private static void stopEverything(ServerPlayer player, PlayerSession s) {
		Homelander.stop(player, s);
		ATrain.stop(player, s);
		Butcher.stop(player, s);
		SoldierBoy.stop(player, s);
		MiniMaus.stop(player, s);
		Starlight.stop(player, s);
		Stormfront.stop(player, s);
		TheDeep.stop(player, s);
		BlackNoir.stop(player, s);
		BlackAdam.stop(player, s);
		PowerAttachments.setActive(player, ActiveState.IDLE);
	}

	public static void applyPassives(ServerPlayer player) {
		Power p = PowerAttachments.powerOf(player);
		double damage = 0, armor = 0, toughness = 0, knockback = 0, fall = 0, health = 0, speed = 0;
		switch (p) {
			case HOMELANDER -> { damage = 14; armor = 20; toughness = 14; knockback = 0.95; fall = 4096; health = 28; speed = 0.025; }
			case SOLDIER_BOY -> { damage = 11; armor = 20; toughness = 12; knockback = 0.8; fall = 24; health = 26; }
			case A_TRAIN -> { damage = 3; armor = 6; fall = 12; speed = 0.05; health = 4; }
			case BUTCHER -> { damage = 7; armor = 14; toughness = 6; knockback = 0.6; fall = 16; health = 10; }
			// twice as fast, and as strong when she is a pixel as when she is full size
			case MINIMAUS -> { damage = 8; armor = 10; toughness = 4; knockback = 0.8; fall = 30; health = 6; }
			case STARLIGHT -> { damage = 5; armor = 8; toughness = 2; knockback = 0.3; fall = 12; health = 6; }
			case STORMFRONT -> { damage = 10; armor = 16; toughness = 8; knockback = 0.6; fall = 4096; health = 20; speed = 0.015; }
			case THE_DEEP -> { damage = 2; armor = 6; toughness = 2; knockback = 0.3; fall = 14; health = 6; }
			// the god of the game
			case BLACK_ADAM -> { damage = 22; armor = 30; toughness = 20; knockback = 1.0; fall = 4096; health = 80; speed = 0.04; }
			case BLACK_NOIR -> { damage = 9; armor = 14; toughness = 6; knockback = 0.5; fall = 20; health = 10; speed = 0.02; }
			default -> { }
		}
		modifier(player, Attributes.ATTACK_DAMAGE, DAMAGE, damage, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.ARMOR, ARMOR, armor, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS, toughness, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, knockback, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.SAFE_FALL_DISTANCE, FALL, fall, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.MAX_HEALTH, HEALTH, health, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.MOVEMENT_SPEED, SPEED, speed, AttributeModifier.Operation.ADD_VALUE);
		// The Deep swims like a torpedo and digs under water like on land
		modifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM, p == Power.THE_DEEP ? 1.0 : 0, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.SUBMERGED_MINING_SPEED, SWIM, p == Power.THE_DEEP ? 4.0 : 0, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.OXYGEN_BONUS, SWIM, p == Power.THE_DEEP ? 100.0 : 0, AttributeModifier.Operation.ADD_VALUE);
		// fire is Stormfront's weakness: she burns twice as long
		modifier(player, Attributes.BURNING_TIME, SWIM, p == Power.STORMFRONT ? 1.0 : 0, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.MOVEMENT_SPEED, DOUBLE_SPEED, p == Power.MINIMAUS ? 1.0 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Adds (amount != 0) or removes (amount == 0) a transient attribute modifier. */
	public static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = player.getAttribute(attribute);
		if (inst == null) return;
		if (amount == 0) {
			inst.removeModifier(id);
		} else {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	public static void onKey(ServerPlayer player, int slot, boolean pressed) {
		if (slot == AbilityKeyPayload.SUIT) {
			if (pressed) toggleSuit(player);
			return;
		}
		if (slot < 0 || slot > 3 || player.isSpectator() || !player.isAlive()) return;
		PlayerSession s = session(player);
		s.keyDown[slot] = pressed;
		switch (PowerAttachments.powerOf(player)) {
			case HOMELANDER -> Homelander.key(player, s, slot, pressed);
			case SOLDIER_BOY -> SoldierBoy.key(player, s, slot, pressed);
			case A_TRAIN -> ATrain.key(player, s, slot, pressed);
			case BUTCHER -> Butcher.key(player, s, slot, pressed);
			case MINIMAUS -> MiniMaus.key(player, s, slot, pressed);
			case STARLIGHT -> Starlight.key(player, s, slot, pressed);
			case STORMFRONT -> Stormfront.key(player, s, slot, pressed);
			case THE_DEEP -> TheDeep.key(player, s, slot, pressed);
			case BLACK_NOIR -> BlackNoir.key(player, s, slot, pressed);
			case BLACK_ADAM -> BlackAdam.key(player, s, slot, pressed);
			default -> {
				if (pressed) player.sendSystemMessage(Component.translatable("message.theboys.no_power").withStyle(ChatFormatting.GRAY), true);
			}
		}
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Power power = PowerAttachments.powerOf(player);
			if (power == Power.NONE) {
				continue;
			}
			PlayerSession s = session(player);
			s.motion = s.lastPos == null ? net.minecraft.world.phys.Vec3.ZERO : player.position().subtract(s.lastPos);
			s.lastPos = player.position();
			s.tickCooldowns();
			int meter = 0;
			if (!player.isAlive()) {
				stopEverything(player, s);
				continue;
			}
			switch (power) {
				case HOMELANDER -> meter = Homelander.tick(player, s);
				case SOLDIER_BOY -> meter = SoldierBoy.tick(player, s);
				case A_TRAIN -> meter = ATrain.tick(player, s);
				case BUTCHER -> meter = Butcher.tick(player, s);
				case MINIMAUS -> meter = MiniMaus.tick(player, s);
				case STARLIGHT -> meter = Starlight.tick(player, s);
				case STORMFRONT -> meter = Stormfront.tick(player, s);
				case THE_DEEP -> meter = TheDeep.tick(player, s);
				case BLACK_NOIR -> meter = BlackNoir.tick(player, s);
				case BLACK_ADAM -> meter = BlackAdam.tick(player, s);
				default -> { }
			}
			if (++s.statusTimer >= 3) {
				s.statusTimer = 0;
				ServerPlayNetworking.send(player, new StatusPayload(
						StatusPayload.pack(s.cooldown[0], s.cooldownTotal[0]),
						StatusPayload.pack(s.cooldown[1], s.cooldownTotal[1]),
						StatusPayload.pack(s.cooldown[2], s.cooldownTotal[2]),
						StatusPayload.pack(s.cooldown[3], s.cooldownTotal[3]),
						meter));
			}
		}
	}

	/** Message shown when an ability is still cooling down. */
	public static void notReady(ServerPlayer player, PlayerSession s, int slot) {
		player.sendSystemMessage(Component.translatable("message.theboys.cooldown",
				Component.translatable(PowerAttachments.powerOf(player).abilityKey(slot)),
				String.format("%.1f", s.cooldown[slot] / 20f)).withStyle(ChatFormatting.RED), true);
	}
}
