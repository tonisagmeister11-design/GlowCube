package de.gtacity.registry;

import de.gtacity.GtaCity;
import de.gtacity.block.AtmBlock;
import de.gtacity.block.BankVaultBlock;
import de.gtacity.block.ElevatorBlock;
import de.gtacity.block.GasPumpBlock;
import de.gtacity.block.ShopCounterBlock;
import de.gtacity.shop.ShopType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

public final class ModBlocks {
    private ModBlocks() {
    }

    public static final Block ELEVATOR = register("elevator", ElevatorBlock::new, unbreakable().lightLevel(s -> 8));
    public static final Block BANK_VAULT = register("bank_vault", BankVaultBlock::new, unbreakable());
    public static final Block WEAPON_COUNTER = register("weapon_counter",
            p -> new ShopCounterBlock(ShopType.WEAPONS, p), unbreakable());
    public static final Block STORE_COUNTER = register("store_counter",
            p -> new ShopCounterBlock(ShopType.STORE, p), unbreakable());
    /** Desks of the job centre and the harbour office: right click talks to the clerk / opens the job board. */
    public static final Block JOB_DESK = register("job_desk",
            p -> new de.gtacity.block.JobDeskBlock(false, p), unbreakable());
    public static final Block SHADY_DESK = register("shady_desk",
            p -> new de.gtacity.block.JobDeskBlock(true, p), unbreakable());
    public static final Block CAR_COUNTER = register("car_counter",
            p -> new ShopCounterBlock(ShopType.CARS, p), unbreakable());
    public static final Block GAS_PUMP = register("gas_pump", GasPumpBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0F).sound(SoundType.METAL)
                    .noOcclusion());
    public static final Block ATM = register("atm", AtmBlock::new, unbreakable().lightLevel(s -> 5));

    /** The GlowCube billboard on some roofs and skyscrapers (no item: it only exists in the city). */
    public static final de.gtacity.block.BillboardBlock BILLBOARD = registerBlockOnly("billboard",
            de.gtacity.block.BillboardBlock::new, unbreakable().lightLevel(s -> 10).emissiveRendering(s -> true));

    private static BlockBehaviour.Properties unbreakable() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F)
                .sound(SoundType.METAL).noLootTable();
    }

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, GtaCity.id(name));
        Block block = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, GtaCity.id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    private static <T extends Block> T registerBlockOnly(String name, Function<BlockBehaviour.Properties, T> factory,
                                                         BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, GtaCity.id(name));
        T block = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        return block;
    }

    public static void init() {
    }
}
