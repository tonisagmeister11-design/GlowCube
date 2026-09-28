package de.gtacity.registry;

import de.gtacity.GtaCity;
import de.gtacity.entity.CarEntity;
import de.gtacity.entity.GrenadeEntity;
import de.gtacity.entity.NpcEntity;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.entity.RocketEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    private ModEntities() {
    }

    public static final EntityType<NpcEntity> PEDESTRIAN = register("pedestrian",
            EntityType.Builder.of(NpcEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .clientTrackingRange(10));
    public static final EntityType<PoliceEntity> POLICE = register("police",
            EntityType.Builder.of(PoliceEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .clientTrackingRange(10));
    public static final EntityType<CarEntity> CAR = register("car",
            EntityType.Builder.of(CarEntity::new, MobCategory.MISC).sized(2.3F, 1.5F).clientTrackingRange(10)
                    .updateInterval(1));
    public static final EntityType<RocketEntity> ROCKET = register("rocket",
            EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).sized(0.4F, 0.4F)
                    .clientTrackingRange(8).updateInterval(1));
    public static final EntityType<GrenadeEntity> GRENADE = register("grenade",
            EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC).sized(0.3F, 0.3F)
                    .clientTrackingRange(8).updateInterval(2));

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, GtaCity.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(PEDESTRIAN, NpcEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(POLICE, PoliceEntity.createPoliceAttributes());
    }
}
