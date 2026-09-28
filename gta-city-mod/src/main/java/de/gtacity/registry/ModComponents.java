package de.gtacity.registry;

import com.mojang.serialization.Codec;
import de.gtacity.GtaCity;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModComponents {
    private ModComponents() {
    }

    /** Rounds left in the magazine of a gun. */
    public static final DataComponentType<Integer> AMMO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            GtaCity.id("ammo"), DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Dollar value of one cash item. */
    public static final DataComponentType<Integer> CASH_VALUE = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, GtaCity.id("cash_value"), DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    public static void init() {
    }
}
