package de.gtacity.registry;

import de.gtacity.GtaCity;
import de.gtacity.item.AmmoItem;
import de.gtacity.item.CashItem;
import de.gtacity.item.GunItem;
import de.gtacity.item.GunType;
import de.gtacity.item.UtilityItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumables;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModItems {
    private ModItems() {
    }

    private static final List<Item> TAB_ITEMS = new ArrayList<>();

    public static final Item PISTOL = gun("pistol", GunType.PISTOL);
    public static final Item SMG = gun("smg", GunType.SMG);
    public static final Item CARBINE = gun("carbine", GunType.CARBINE);
    public static final Item SHOTGUN = gun("shotgun", GunType.SHOTGUN);
    public static final Item SNIPER = gun("sniper", GunType.SNIPER);
    public static final Item MINIGUN = gun("minigun", GunType.MINIGUN);
    public static final Item RPG = gun("rpg", GunType.RPG);

    public static final Item PISTOL_AMMO = ammo("pistol_ammo", GunType.AmmoKind.PISTOL);
    public static final Item SMG_AMMO = ammo("smg_ammo", GunType.AmmoKind.SMG);
    public static final Item RIFLE_AMMO = ammo("rifle_ammo", GunType.AmmoKind.RIFLE);
    public static final Item SHOTGUN_SHELLS = ammo("shotgun_shells", GunType.AmmoKind.SHELLS);
    public static final Item SNIPER_AMMO = ammo("sniper_ammo", GunType.AmmoKind.SNIPER);
    public static final Item ROCKET = ammo("rocket", GunType.AmmoKind.ROCKET);

    public static final Item GRENADE = register("grenade", UtilityItems.Grenade::new, new Item.Properties().stacksTo(16));
    public static final Item KNIFE = register("knife", Item::new,
            new Item.Properties().sword(ToolMaterial.IRON, 3.0F, -1.6F));
    public static final Item BASEBALL_BAT = register("baseball_bat", Item::new,
            new Item.Properties().sword(ToolMaterial.WOOD, 4.0F, -2.6F));
    public static final Item BODY_ARMOR = register("body_armor", UtilityItems.BodyArmor::new,
            new Item.Properties().stacksTo(4));
    public static final Item MEDKIT = register("medkit", UtilityItems.Medkit::new, new Item.Properties().stacksTo(8));
    public static final Item THERMAL_DRILL = register("thermal_drill", Item::new, new Item.Properties().stacksTo(1));
    public static final Item CASH = register("cash", CashItem::new, new Item.Properties().stacksTo(64));
    public static final Item CAR_KEY = register("car_key", Item::new, new Item.Properties().stacksTo(1));

    public static final Item BURGER = register("burger", Item::new, new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build()));
    public static final Item ECOLA = register("ecola", Item::new, new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).alwaysEdible().build(),
                    Consumables.defaultDrink().build()));
    public static final Item CANDY_BAR = register("candy_bar", Item::new, new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).alwaysEdible().build()));

    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            GtaCity.id("gta_city"),
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.literal("GTA City"))
                    .icon(() -> new ItemStack(PISTOL))
                    .displayItems((params, output) -> {
                        for (Item item : TAB_ITEMS) {
                            output.accept(item);
                        }
                        output.accept(ModBlocks.ELEVATOR);
                        output.accept(ModBlocks.BANK_VAULT);
                        output.accept(ModBlocks.WEAPON_COUNTER);
                        output.accept(ModBlocks.STORE_COUNTER);
                        output.accept(ModBlocks.CAR_COUNTER);
                        output.accept(ModBlocks.GAS_PUMP);
                        output.accept(ModBlocks.ATM);
                    })
                    .build());

    private static Item gun(String name, GunType type) {
        return register(name, p -> new GunItem(type, p), new Item.Properties());
    }

    private static Item ammo(String name, GunType.AmmoKind kind) {
        return register(name, p -> new AmmoItem(kind, p), new Item.Properties());
    }

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, GtaCity.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
        TAB_ITEMS.add(item);
        return item;
    }

    public static void init() {
    }
}
