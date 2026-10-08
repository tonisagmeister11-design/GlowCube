package de.theboys.registry;

import java.util.function.Function;

import de.theboys.TheBoys;
import de.theboys.block.VFridgeBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Block LAB_TILES = register("lab_tiles", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(2.0f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops());
	public static final Block LAB_FLOOR = register("lab_floor", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops());
	public static final Block VOUGHT_PANEL = register("vought_panel", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f, 9.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());
	public static final Block CONTAINMENT_GLASS = register("containment_glass", TransparentBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(3.0f, 12.0f).sound(SoundType.GLASS).noOcclusion());
	public static final Block V_FRIDGE = register("v_fridge", VFridgeBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(3.0f, 6.0f).sound(SoundType.METAL).lightLevel(s -> 4));

	/** A block MiniMaus chipped pixels out of. No item: it only exists in the world. */
	public static final Block CARVED = Registry.register(BuiltInRegistries.BLOCK, TheBoys.id("carved_block"),
			new de.theboys.block.CarvedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 6.0f)
					.noOcclusion().dynamicShape().sound(SoundType.STONE)
					// you can hide inside: no suffocation, no block texture over the screen
					.isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false)
					.setId(ResourceKey.create(Registries.BLOCK, TheBoys.id("carved_block")))));
	public static final net.minecraft.world.level.block.entity.BlockEntityType<de.theboys.block.CarvedBlockEntity> CARVED_TYPE =
			Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TheBoys.id("carved_block"),
					new net.minecraft.world.level.block.entity.BlockEntityType<>(de.theboys.block.CarvedBlockEntity::new, java.util.Set.of(CARVED)));

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		Identifier id = TheBoys.id(name);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(props.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void init() {
	}
}
