package de.glowcube.bedwars;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
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
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
         if (!Game.running()) {
            return true;
         } else if (Game.isProtected(entity)) {
            return false;
         } else {
            Entity attacker = source.getEntity();
            return attacker == null || attacker == entity || Game.teamOf(attacker) == null || Game.teamOf(attacker) != Game.teamOf(entity);
         }
      });
      PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !(player instanceof ServerPlayer sp && !Game.onPlayerBreak(sp, pos, state)));
      UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
         if (Game.running() && entity.entityTags().contains("bw_shop")) {
            if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND) {
               Shop.open(sp);
            }

            return InteractionResult.SUCCESS;
         } else {
            return InteractionResult.PASS;
         }
      });
      UseItemCallback.EVENT.register((player, world, hand) -> {
         ItemStack stack = player.getItemInHand(hand);
         if (Game.running() && stack.is(Items.FIRE_CHARGE) && world instanceof ServerLevel level && player == Game.player()) {
            Vec3 look = player.getLookAngle();
            LargeFireball fb = new LargeFireball(level, player, look.scale(0.1), 2);
            fb.setPos(player.getX() + look.x * 1.5, player.getEyeY() + look.y * 1.5, player.getZ() + look.z * 1.5);
            level.addFreshEntity(fb);
            stack.shrink(1);
            player.getCooldowns().addCooldown(stack, 20);
            return InteractionResult.SUCCESS;
         } else {
            return InteractionResult.PASS;
         }
      });
      ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> Game.onChat(sender, message.signedContent()));
      CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
         Commands.literal("bedwars")
            .then(Commands.literal("start").executes(ctx -> {
               Game.start(ctx.getSource().getPlayerOrException());
               return 1;
            }))
            .then(Commands.literal("stop").executes(ctx -> {
               Game.stop(true);
               return 1;
            }))
            .then(Commands.literal("test").executes(ctx -> {
               ServerLevel level = ctx.getSource().getServer().overworld();
               FakePlayer fake = FakePlayer.get(level);
               fake.setPos(0.5, 70.0, 0.5);
               Game.debug = true;
               Game.start(fake);
               Game.benchPlayer();
               return 1;
            }))
            .then(Commands.literal("say").then(Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
               // zum Testen der Chat-KI ohne echten Chat
               ServerPlayer p = Game.player();
               if (p != null) {
                  BotChat.onPlayerChat(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "text"));
               }

               return 1;
            })))
            .then(Commands.literal("shop").executes(ctx -> {
               Shop.open(ctx.getSource().getPlayerOrException());
               return 1;
            }))
            .then(Commands.literal("status").executes(ctx -> {
               ctx.getSource().sendSuccess(() -> Component.literal(Game.status()), false);
               return 1;
            }))
            .then(Commands.literal("auto")
               .then(Commands.literal("an").executes(ctx -> {
                  Game.autoStart = true;
                  ctx.getSource().sendSuccess(() -> Component.literal("Automatischer Start beim Betreten: an").withStyle(ChatFormatting.GREEN), false);
                  return 1;
               }))
               .then(Commands.literal("aus").executes(ctx -> {
                  Game.autoStart = false;
                  ctx.getSource().sendSuccess(() -> Component.literal("Automatischer Start beim Betreten: aus").withStyle(ChatFormatting.YELLOW), false);
                  return 1;
               })))
      ));
   }
}
