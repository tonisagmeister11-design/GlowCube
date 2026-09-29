package de.gtacity.shop;

import de.gtacity.registry.ModSounds;
import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.gameplay.Economy;
import de.gtacity.gameplay.Garage;
import de.gtacity.registry.ModEntities;
import de.gtacity.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Everything that can be bought, per shop. Shared by client (display) and server (purchase). */
public final class ShopCatalog {
    private ShopCatalog() {
    }

    /** One offer: either an item stack or a car. */
    public record Offer(String name, int price, Item item, int count, CarVariant car) {
        static Offer item(String name, int price, Item item, int count) {
            return new Offer(name, price, item, count, null);
        }

        static Offer car(String name, int price, CarVariant car) {
            return new Offer(name, price, null, 1, car);
        }

        public ItemStack icon() {
            return item != null ? new ItemStack(item, count) : new ItemStack(ModItems.CAR_KEY);
        }
    }

    private static List<Offer> weapons;
    private static List<Offer> store;
    private static List<Offer> cars;

    public static List<Offer> offers(ShopType type) {
        if (weapons == null) {
            weapons = List.of(
                    Offer.item("Pistole", 750, ModItems.PISTOL, 1),
                    Offer.item("Micro-SMG", 2200, ModItems.SMG, 1),
                    Offer.item("Karabiner", 4500, ModItems.CARBINE, 1),
                    Offer.item("Pumpgun", 3000, ModItems.SHOTGUN, 1),
                    Offer.item("Scharfschützengewehr", 9000, ModItems.SNIPER, 1),
                    Offer.item("Raketenwerfer", 25000, ModItems.RPG, 1),
                    Offer.item("Minigun", 50000, ModItems.MINIGUN, 1),
                    Offer.item("Granaten x5", 1000, ModItems.GRENADE, 5),
                    Offer.item("Messer", 200, ModItems.KNIFE, 1),
                    Offer.item("Baseballschläger", 150, ModItems.BASEBALL_BAT, 1),
                    Offer.item("Schutzweste", 500, ModItems.BODY_ARMOR, 1),
                    Offer.item("Thermobohrer", 2500, ModItems.THERMAL_DRILL, 1),
                    Offer.item("Pistolenmunition x48", 100, ModItems.PISTOL_AMMO, 48),
                    Offer.item("SMG-Munition x90", 150, ModItems.SMG_AMMO, 90),
                    Offer.item("Gewehrmunition x90", 200, ModItems.RIFLE_AMMO, 90),
                    Offer.item("Schrotpatronen x24", 150, ModItems.SHOTGUN_SHELLS, 24),
                    Offer.item("Scharfschützenmunition x20", 300, ModItems.SNIPER_AMMO, 20),
                    Offer.item("Raketen x4", 1500, ModItems.ROCKET, 4));
            store = List.of(
                    Offer.item("Burger", 15, ModItems.BURGER, 1),
                    Offer.item("eCola", 5, ModItems.ECOLA, 1),
                    Offer.item("Schokoriegel", 3, ModItems.CANDY_BAR, 1),
                    Offer.item("Burger x8", 100, ModItems.BURGER, 8),
                    Offer.item("Medikit", 250, ModItems.MEDKIT, 1),
                    Offer.item("Schutzweste", 600, ModItems.BODY_ARMOR, 1));
            cars = List.of(
                    Offer.car("Limousine (rot)", 12000, CarVariant.SEDAN_RED),
                    Offer.car("Limousine (schwarz)", 12000, CarVariant.SEDAN_BLACK),
                    Offer.car("Taxi", 9000, CarVariant.TAXI),
                    Offer.car("SUV (schwarz)", 30000, CarVariant.SUV_BLACK),
                    Offer.car("SUV (weiß)", 30000, CarVariant.SUV_WHITE),
                    Offer.car("Sportwagen (rot)", 95000, CarVariant.SPORTS_RED),
                    Offer.car("Sportwagen (gelb)", 95000, CarVariant.SPORTS_YELLOW),
                    Offer.car("Sportwagen (schwarz)", 95000, CarVariant.SPORTS_BLACK),
                    Offer.car("Sportwagen (lime)", 110000, CarVariant.SPORTS_LIME),
                    Offer.car("Supersport Furia (rot)", 240000, CarVariant.SUPER_RED),
                    Offer.car("Supersport Furia (orange)", 240000, CarVariant.SUPER_ORANGE),
                    Offer.car("Supersport Furia (grün)", 260000, CarVariant.SUPER_LIME),
                    Offer.car("Supersport Furia (perlweiß)", 280000, CarVariant.SUPER_PEARL),
                    Offer.car("Supersport Furia (magenta)", 280000, CarVariant.SUPER_MAGENTA),
                    Offer.car("Supersport Furia Carbon", 350000, CarVariant.SUPER_CARBON));
        }
        return switch (type) {
            case WEAPONS -> weapons;
            case STORE -> store;
            case CARS -> cars;
        };
    }

    public static void buy(ServerPlayer player, ShopType type, int index) {
        List<Offer> list = offers(type);
        if (index < 0 || index >= list.size()) {
            return;
        }
        Offer offer = list.get(index);
        if (!Economy.trySpend(player, offer.price())) {
            player.sendOverlayMessage(Component.literal("Zu wenig Geld! Du brauchst " + Economy.format(offer.price()))
                    .withStyle(ChatFormatting.RED));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
            return;
        }
        if (offer.car() != null) {
            ServerLevel level = (ServerLevel) player.level();
            CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.EVENT);
            if (car != null) {
                Vec3 spot = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(4.0));
                car.setVariant(offer.car());
                car.setPersistentCar(true);
                car.setOwner(player.getUUID());
                car.snapTo(spot.x, player.getY() + 0.5, spot.z, player.getYRot(), 0.0F);
                level.addFreshEntity(car);
                Garage.add(player, offer.car());
                Garage.delivered(player, car);
                player.sendSystemMessage(Component.literal("Das Auto gehört dir. Du kannst es jederzeit über die "
                        + "Karte (M) → Garage liefern lassen.").withStyle(ChatFormatting.GRAY));
            }
        } else {
            ItemStack stack = new ItemStack(offer.item(), offer.count());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, Prediction.SERVER_ONLY);
            }
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASH,
                SoundSource.PLAYERS, 0.8F, 1.0F);
        player.sendOverlayMessage(Component.literal("Gekauft: " + offer.name() + " für "
                + Economy.format(offer.price())).withStyle(ChatFormatting.GREEN));
    }
}
