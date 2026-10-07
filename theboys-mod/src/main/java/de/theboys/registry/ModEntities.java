package de.theboys.registry;

import de.theboys.TheBoys;
import de.theboys.entity.ThrownShield;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> THROWN_SHIELD_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TheBoys.id("thrown_shield"));

	public static final EntityType<ThrownShield> THROWN_SHIELD = Registry.register(BuiltInRegistries.ENTITY_TYPE, THROWN_SHIELD_KEY,
			EntityType.Builder.<ThrownShield>of(ThrownShield::new, MobCategory.MISC)
					.sized(0.8f, 0.8f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noLootTable()
					.build(THROWN_SHIELD_KEY));

	private ModEntities() {
	}

	public static void init() {
	}
}
