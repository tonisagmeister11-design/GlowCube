package de.gtacity.gameplay;

import de.gtacity.block.AtmBlock;
import de.gtacity.block.BankVaultBlock;
import de.gtacity.block.ElevatorBlock;
import de.gtacity.block.ShopCounterBlock;
import de.gtacity.entity.CarEntity;
import de.gtacity.entity.NpcEntity;
import de.gtacity.shop.ShopType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionHand;
import de.gtacity.item.GunItem;
import de.gtacity.registry.ModAttachments;
import de.gtacity.registry.ModItems;
import de.gtacity.world.CityChunkGenerator;
import de.gtacity.world.CityLayout;
import de.gtacity.world.CityPlaces;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Glue between Fabric events and the city systems. */
public final class CityEvents {
    private CityEvents() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(CityEvents::setupWorld);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            WantedSystem.tick(server);
            CitySpawns.tick(server);
            Heists.tick(server);
            Jobs.tick(server);
            Clerks.tick(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            WantedSystem.forget(handler.getPlayer());
            PoliceDispatch.forget(handler.getPlayer());
            Jobs.forget(handler.getPlayer());
            Garage.forget(handler.getPlayer());
        });

        // Pickpocketing: sneak up to a pedestrian and right click with an empty hand.
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            // Talking to a clerk (right click): shop or job board.
            if (hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown() && entity instanceof NpcEntity clerk
                    && !clerk.role().isEmpty()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    return Clerks.talk(serverPlayer, clerk) ? InteractionResult.SUCCESS : InteractionResult.PASS;
                }
                return InteractionResult.SUCCESS;
            }
            if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                    || !(entity instanceof NpcEntity npc)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                return Jobs.pickpocket(serverPlayer, npc) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return InteractionResult.SUCCESS;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                WantedSystem.onPlayerDeath(player);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && isCity(newPlayer.level())) {
                BlockPos hospital = CityPlaces.nearest(CityLayout.LotType.HOSPITAL, oldPlayer.getBlockX(),
                        oldPlayer.getBlockZ());
                newPlayer.teleportTo(hospital.getX() + 0.5, hospital.getY(), hospital.getZ() + 0.5);
            }
        });

        // Guns: right click aims, it must not open doors or place blocks
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack stack = player.getItemInHand(hand);
            Block block = level.getBlockState(hit.getBlockPos()).getBlock();
            // Shoplifting: sneak + right click the 24/7 counter with an empty hand.
            if (block instanceof ShopCounterBlock counter && counter.type() == ShopType.STORE
                    && player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND && stack.isEmpty()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    Jobs.shoplift(serverPlayer, hit.getBlockPos());
                }
                return InteractionResult.SUCCESS;
            }
            if (!(stack.getItem() instanceof GunItem)) {
                return InteractionResult.PASS;
            }
            // Hold-up: sneak + right click on a counter. Vanilla skips block interactions while sneaking with
            // something in hand, so the counter itself never hears about it - it has to happen here.
            if (block instanceof ShopCounterBlock counter && player.isShiftKeyDown()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    Robbery.robCounter(serverPlayer, hit.getBlockPos(), counter.type());
                }
                return InteractionResult.SUCCESS;
            }
            boolean ours = block instanceof ShopCounterBlock || block instanceof BankVaultBlock
                    || block instanceof ElevatorBlock || block instanceof AtmBlock;
            return ours ? InteractionResult.PASS : InteractionResult.FAIL;
        });
        // Guns: left click shoots, it must not break blocks
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                player.getMainHandItem().getItem() instanceof GunItem ? InteractionResult.FAIL
                        : InteractionResult.PASS);
    }

    public static boolean isCity(net.minecraft.world.level.Level level) {
        return level instanceof ServerLevel server && server.getChunkSource().getGenerator() instanceof CityChunkGenerator;
    }

    private static void setupWorld(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (!isCity(overworld)) {
            return;
        }
        overworld.getWorldBorder().setCenter(CityLayout.CENTER + 0.5, CityLayout.CENTER + 0.5);
        overworld.getWorldBorder().setSize(CityLayout.BORDER_SIZE);
        CitySetup.setSpawn(overworld, CityPlaces.spawn());
        CitySetup.setGameRules(overworld, server);
    }

    private static void onJoin(ServerPlayer player) {
        if (!isCity(player.level())) {
            return;
        }
        if (!player.hasAttached(ModAttachments.MONEY)) {
            // Only values that are actually set get synced - without this the HUD shows $0 instead of $500.
            Economy.set(player, Economy.get(player));
        }
        // The story does not wait for the player to find the job centre: the next chapter phones them.
        Jobs.scheduleStory(player, 20 * 8);
        Boolean hasKit = player.getAttached(ModAttachments.STARTER_KIT);
        if (hasKit != null && hasKit) {
            return;
        }
        player.setAttached(ModAttachments.STARTER_KIT, true);
        BlockPos spawn = CityPlaces.spawn();
        player.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        player.getInventory().add(new ItemStack(ModItems.PISTOL));
        player.getInventory().add(new ItemStack(ModItems.PISTOL_AMMO, 48));
        player.getInventory().add(new ItemStack(ModItems.BASEBALL_BAT));
        player.getInventory().add(new ItemStack(ModItems.BURGER, 4));
        player.sendSystemMessage(Component.literal("Willkommen in Los Santos!").withStyle(ChatFormatting.GOLD,
                ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Waffen: Linksklick schießen, Rechtsklick zielen, R nachladen. "
                + "Autos: F oder Rechtsklick einsteigen, WASD fahren, Shift driften, H hupen, F aussteigen. "
                + "G im Auto: Auto behalten, B: eigenes Auto herbeiholen. M: Karte mit Navi, Jobs, Garage und Villen. "
                + "Läden: Rechtsklick auf die Theke. Überfall: Schleichen + Rechtsklick mit Waffe.")
                .withStyle(ChatFormatting.GRAY));
    }

    /** Car helper for other systems. */
    public static boolean inCar(ServerPlayer player) {
        return player.getVehicle() instanceof CarEntity;
    }
}
