package de.theboys.registry;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import de.theboys.TheBoys;
import de.theboys.item.ShieldItem;
import de.theboys.item.SyringeItem;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;

public final class ModItems {
	public static final Item COMPOUND_V = register("compound_v", p -> new SyringeItem(SyringeItem.Kind.COMPOUND_V, p),
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item COMPOUND_V1 = register("compound_v1", p -> new SyringeItem(SyringeItem.Kind.V_ONE, p),
			new Item.Properties().stacksTo(4).rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
	public static final Item URANIUM_INJECTOR = register("uranium_injector", p -> new SyringeItem(SyringeItem.Kind.URANIUM, p),
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	public static final Item MINI_V = register("mini_v", p -> new SyringeItem(SyringeItem.Kind.MINI_V, p),
			new Item.Properties().stacksTo(8).rarity(Rarity.RARE));
	public static final Item SHAZAM_SERUM = register("shazam_serum", p -> new SyringeItem(SyringeItem.Kind.SHAZAM, p),
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
	public static final Item EMPTY_SYRINGE = register("empty_syringe", Item::new, new Item.Properties());
	public static final Item SOLDIER_BOY_SHIELD = register("soldier_boy_shield", ShieldItem::new,
			new Item.Properties().durability(2500).rarity(Rarity.EPIC).fireResistant()
					.component(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(0.25f, 0.5f,
							List.of(new BlocksAttacks.DamageReduction(90.0f, Optional.empty(), 0.0f, 1.0f)),
							new BlocksAttacks.ItemDamageFunction(3.0f, 1.0f, 1.0f),
							Optional.empty(),
							Optional.of(SoundEvents.SHIELD_BLOCK),
							Optional.of(SoundEvents.SHIELD_BREAK))));
	public static final Item CROWBAR = register("crowbar", Item::new,
			new Item.Properties().sword(ToolMaterial.IRON, 5.0f, -2.6f).rarity(Rarity.UNCOMMON));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TheBoys.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TheBoys.id("the_boys"), FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.theboys"))
				.icon(() -> new ItemStack(COMPOUND_V))
				.displayItems((context, entries) -> {
					entries.accept(COMPOUND_V);
					entries.accept(COMPOUND_V1);
					entries.accept(MINI_V);
					entries.accept(SHAZAM_SERUM);
					entries.accept(URANIUM_INJECTOR);
					entries.accept(EMPTY_SYRINGE);
					entries.accept(SOLDIER_BOY_SHIELD);
					entries.accept(CROWBAR);
					entries.accept(ModBlocks.V_FRIDGE);
					entries.accept(ModBlocks.LAB_TILES);
					entries.accept(ModBlocks.LAB_FLOOR);
					entries.accept(ModBlocks.VOUGHT_PANEL);
					entries.accept(ModBlocks.CONTAINMENT_GLASS);
				})
				.build());
	}
}
