package de.theboys.registry;

import de.theboys.TheBoys;
import de.theboys.entity.SupeHunter;
import de.theboys.entity.ThrownShield;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
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

	private static final ResourceKey<EntityType<?>> SUPE_HUNTER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TheBoys.id("supe_hunter"));

	public static final EntityType<SupeHunter> SUPE_HUNTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, SUPE_HUNTER_KEY,
			EntityType.Builder.<SupeHunter>of(SupeHunter::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.clientTrackingRange(10)
					.build(SUPE_HUNTER_KEY));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(SUPE_HUNTER, SupeHunter.createAttributes());
	}
}
