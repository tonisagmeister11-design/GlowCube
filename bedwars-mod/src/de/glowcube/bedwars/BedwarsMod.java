package de.glowcube.bedwars;

import com.mojang.brigadier.Command;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class BedwarsMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Game.onJoin(handler.getPlayer(), server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Game.onDisconnect(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Game.onServerStopping());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Game.tick(server);
            Game.afterTick();
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> Game.onDeath(entity, source));

        // Kein Friendly Fire, keine Schaeden waehrend des Spawnschutzes
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!Game.running()) return true;
            if (Game.isProtected(entity)) return false;
            var attacker = source.getEntity();
            if (attacker != null && attacker != entity && Game.teamOf(attacker) != null && Game.teamOf(attacker) == Game.teamOf(entity)) {
                return false;
            }
            return true;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer sp) || Game.onPlayerBreak(sp, pos, state));

        // Shop-Dorfbewohner oeffnen das Shop-Menue statt zu handeln
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (Game.running() && entity.entityTags().contains("bw_shop")) {
                if (player instanceof ServerPlayer sp && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                    Shop.open(sp);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        // Feuerball: Rechtsklick verschiesst ihn
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!Game.running() || !stack.is(Items.FIRE_CHARGE) || !(world instanceof ServerLevel level) || player != Game.player()) {
                return InteractionResult.PASS;
            }
            Vec3 look = player.getLookAngle();
            LargeFireball ball = new LargeFireball(level, player, look.scale(0.1), 2);
            ball.setPos(player.getX() + look.x * 1.5, player.getEyeY() + look.y * 1.5, player.getZ() + look.z * 1.5);
            level.addFreshEntity(ball);
            stack.shrink(1);
            player.getCooldowns().addCooldown(stack, 20);
            return InteractionResult.SUCCESS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(Commands.literal("bedwars")
                        .then(Commands.literal("start").executes(ctx -> {
                            Game.start(ctx.getSource().getPlayerOrException());
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("stop").executes(ctx -> {
                            Game.stop(true);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("test").executes(ctx -> {
                            // Selbsttest ohne echten Spieler: Fake-Spieler plus Bots, mit Log-Ausgabe
                            ServerLevel level = ctx.getSource().getServer().overworld();
                            var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
                            fake.setPos(0.5, 70, 0.5);
                            Game.debug = true;
                            Game.start(fake);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("shop").executes(ctx -> {
                            Shop.open(ctx.getSource().getPlayerOrException());
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(Game.status()), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("auto")
                                .then(Commands.literal("an").executes(ctx -> {
                                    Game.autoStart = true;
                                    ctx.getSource().sendSuccess(() -> Component.literal("Automatischer Start beim Betreten: an").withStyle(ChatFormatting.GREEN), false);
                                    return Command.SINGLE_SUCCESS;
                                }))
                                .then(Commands.literal("aus").executes(ctx -> {
                                    Game.autoStart = false;
                                    ctx.getSource().sendSuccess(() -> Component.literal("Automatischer Start beim Betreten: aus").withStyle(ChatFormatting.YELLOW), false);
                                    return Command.SINGLE_SUCCESS;
                                })))));
    }
}
