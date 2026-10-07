package de.theboys.gametest;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.theboys.client.Keys;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;

/**
 * Starts a real client, creates a world and tries every power once, taking screenshots along the way.
 * Run with ./gradlew runClientGametest (CI does this headless).
 */
public class TheBoysClientGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("theboys-gametest");
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("tp @a 0 -60 0 0 0");
			ctx.waitTicks(20);

			step(ctx, "lab", () -> {
				server.runCommand("place template theboys:vought_lab/main 6 -61 12");
				ctx.waitTicks(10);
				server.runCommand("tp @a 14 -59 4 0 10");
				sp.getConnection().waitForChunksRender();
				ctx.waitTicks(20);
				ctx.takeScreenshot("lab_outside");
				server.runCommand("tp @a 14 -60 15 160 15");
				ctx.waitTicks(20);
				ctx.takeScreenshot("lab_inside");
				String at = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(7, -60, 23)).toString() + " / " + s.overworld().getBlockState(new BlockPos(7, -61, 13)));
				LOG.info("lab blocks: {}", at);
				boolean fridge = at.contains("v_fridge");
				if (!fridge) throw new AssertionError("no V fridge where the template should have placed one");
				server.runCommand("tp @a 0 -60 0 0 0");
			});

			step(ctx, "structure", () -> {
				// the jigsaw structure definition (as used by world generation) must be placeable
				server.runCommand("place structure theboys:vought_lab -40 -60 -40");
				ctx.waitTicks(10);
				boolean placed = server.computeOnServer(s -> {
					var level = s.overworld();
					for (BlockPos p : BlockPos.betweenClosed(-60, -62, -60, -20, -55, -20)) {
						if (level.getBlockState(p).getBlock().toString().contains("v_fridge")) return true;
					}
					return false;
				});
				if (!placed) throw new AssertionError("place structure theboys:vought_lab placed no lab");
			});

			step(ctx, "inject", () -> {
				server.runCommand("clear @a");
				server.runCommand("give @a theboys:compound_v");
				ctx.waitTicks(10);
				ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
				ctx.waitTicks(5);
				ctx.takeScreenshot("hold_syringe_first_person");
				ctx.getInput().pressKey(Keys.INJECT);
				ctx.waitTicks(5);
				ctx.takeScreenshot("inject_first_person_1");
				ctx.waitTicks(7);
				ctx.takeScreenshot("inject_first_person_2");
				ctx.waitTicks(8);
				ctx.takeScreenshot("inject_first_person_3");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				ctx.waitTicks(4);
				ctx.takeScreenshot("inject_third_person");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				ctx.waitTicks(16);
				ctx.takeScreenshot("inject_flash");
				ctx.waitTicks(14);
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				Power got = server.computeOnServer(s -> PowerAttachments.powerOf(s.getPlayerList().getPlayers().get(0)));
				LOG.info("Compound V gave: {}", got);
				if (got != Power.A_TRAIN && got != Power.BUTCHER) throw new AssertionError("Compound V gave " + got);
			});

			step(ctx, "items_3d", () -> {
				String[][] items = {{"theboys:compound_v1", "v1"}, {"theboys:crowbar", "crowbar"}, {"theboys:soldier_boy_shield", "shield"}};
				for (String[] it : items) {
					server.runCommand("clear @a");
					server.runCommand("give @a " + it[0]);
					ctx.waitTicks(8);
					ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
					ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
					ctx.waitTicks(4);
					ctx.takeScreenshot("item_" + it[1] + "_first_person");
					sideView(ctx, server, 2.6, -58.6, 2.2, 130, 18);
					ctx.takeScreenshot("item_" + it[1] + "_third_person");
					playerView(ctx, server);
				}
				// shield raised
				ctx.getInput().holdKey(o -> o.keyUse);
				ctx.waitTicks(8);
				ctx.takeScreenshot("item_shield_blocking_first_person");
				sideView(ctx, server, 2.6, -58.6, 2.2, 130, 18);
				ctx.takeScreenshot("item_shield_blocking_third_person");
				playerView(ctx, server);
				ctx.getInput().releaseKey(o -> o.keyUse);
				server.runCommand("clear @a");
			});

			for (Power p : new Power[] {Power.HOMELANDER, Power.SOLDIER_BOY, Power.A_TRAIN, Power.BUTCHER}) {
				step(ctx, "suit_" + p.id(), () -> {
					server.runCommand("theboys power set @a " + p.id());
					ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
					ctx.waitTicks(8);
					ctx.takeScreenshot("suit_" + p.id());
					ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				});
			}

			step(ctx, "homelander", () -> {
				server.runCommand("theboys power set @a homelander");
				server.runCommand("summon minecraft:zombie 0 -60 9 {NoAI:1b}");
				ctx.getInput().lookAt(0, 0);
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				ctx.getInput().holdKey(Keys.ABILITY[0]);
				ctx.waitTicks(10);
				ctx.takeScreenshot("homelander_laser_third_person");
				sideView(ctx, server, 7, -58.5, 5, 90, 5);
				ctx.takeScreenshot("homelander_laser_side");
				ctx.getInput().lookAt(-25, -20);
				ctx.waitTicks(4);
				ctx.takeScreenshot("homelander_laser_sky");
				ctx.getInput().lookAt(0, 0);
				playerView(ctx, server);
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				ctx.waitTicks(3);
				ctx.takeScreenshot("homelander_laser_first_person");
				ctx.getInput().releaseKey(Keys.ABILITY[0]);
				ctx.waitTicks(5);
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				for (int i = 0; i < 5; i++) {
					ctx.waitTicks(2);
					String info = ctx.computeOnClient(mc -> "mayfly=" + mc.player.getAbilities().mayfly + " flying=" + mc.player.getAbilities().flying + " y=" + mc.player.getY());
					String srv = server.computeOnServer(s -> "server mayfly=" + s.getPlayerList().getPlayers().get(0).getAbilities().mayfly + " flying=" + s.getPlayerList().getPlayers().get(0).getAbilities().flying);
					LOG.info("flight check {}: {} | {}", i, info, srv);
				}
				ctx.takeScreenshot("homelander_flight");
				boolean flying = ctx.computeOnClient(mc -> mc.player.getAbilities().flying);
				if (!flying) throw new AssertionError("Homelander is not flying");
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				server.runCommand("kill @e[type=!player]");
				ctx.waitTicks(20);
			});

			step(ctx, "soldier_boy", () -> {
				server.runCommand("tp @a 0 -60 0 0 0");
				server.runCommand("theboys power set @a soldier_boy");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				ctx.getInput().lookAt(30, 0);
				sideView(ctx, server, 8, -58, 8, 120, 10);
				ctx.getInput().holdKey(Keys.ABILITY[0]);
				ctx.waitTicks(12);
				ctx.takeScreenshot("soldier_boy_beam");
				ctx.getInput().releaseKey(Keys.ABILITY[0]);
				ctx.getInput().holdKey(Keys.ABILITY[1]);
				ctx.waitTicks(50);
				ctx.takeScreenshot("soldier_boy_nuke_charge");
				sideView(ctx, server, 3, -59, 3, 135, 10);
				ctx.takeScreenshot("soldier_boy_nuke_pose");
				playerView(ctx, server);
				sideView(ctx, server, 8, -58, 8, 120, 10);
				ctx.getInput().releaseKey(Keys.ABILITY[1]);
				ctx.waitTicks(4);
				ctx.takeScreenshot("soldier_boy_nuke");
				ctx.waitTicks(6);
				ctx.takeScreenshot("soldier_boy_nuke_2");
				playerView(ctx, server);
				ctx.waitTicks(40);
			});

			step(ctx, "a_train", () -> {
				server.runCommand("tp @a 0 -60 0 0 0");
				server.runCommand("theboys power set @a a_train");
				server.runCommand("summon minecraft:zombie 0 -60 14 {NoAI:1b}");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				ctx.getInput().lookAt(0, 10);
				ctx.getInput().pressKey(Keys.ABILITY[0]);
				ctx.waitTicks(3);
				ctx.getInput().holdKey(o -> o.keyUp);
				ctx.getInput().holdKey(o -> o.keySprint);
				ctx.waitTicks(12);
				ctx.takeScreenshot("a_train_running");
				sideView(ctx, server, 10, -58, 30, 110, 5);
				ctx.waitTicks(4);
				ctx.takeScreenshot("a_train_running_side");
				playerView(ctx, server);
				ctx.waitTicks(20);
				ctx.getInput().releaseKey(o -> o.keyUp);
				ctx.getInput().releaseKey(o -> o.keySprint);
				ctx.waitTicks(10);
				// drop an item, then rewind: it must fly back into the inventory
				server.runCommand("give @a minecraft:diamond 1");
				ctx.waitTicks(4);
				ctx.getInput().pressKey(o -> o.keyDrop);
				ctx.waitTicks(30);
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				ctx.getInput().holdKey(o -> o.keyUp);
				ctx.waitTicks(25);
				ctx.takeScreenshot("a_train_time_jump");
				ctx.getInput().releaseKey(o -> o.keyUp);
				ctx.waitTicks(100);
				boolean hasDiamond = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) > 0);
				LOG.info("diamond returned by time jump: {}", hasDiamond);
				ctx.getInput().pressKey(Keys.ABILITY[0]);
				server.runCommand("kill @e[type=!player]");
				ctx.waitTicks(10);
			});

			step(ctx, "butcher", () -> {
				server.runCommand("tp @a 0 -60 0 0 0");
				server.runCommand("theboys power set @a butcher");
				server.runCommand("summon minecraft:pig 0 -60 6");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				ctx.getInput().lookAt(0, 15);
				ctx.waitTicks(10);
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				ctx.waitTicks(12);
				sideView(ctx, server, 6, -58.5, 3, 90, 10);
				ctx.takeScreenshot("butcher_grab");
				ctx.getInput().pressKey(Keys.ABILITY[2]);
				ctx.waitTicks(25);
				ctx.takeScreenshot("butcher_rip");
				ctx.waitTicks(16);
				ctx.takeScreenshot("butcher_rip_end");
				playerView(ctx, server);
				ctx.waitTicks(30);
				ctx.getInput().lookAt(0, 30);
				sideView(ctx, server, 6, -58.5, 3, 90, 10);
				ctx.getInput().pressKey(Keys.ABILITY[0]);
				ctx.waitTicks(3);
				ctx.takeScreenshot("butcher_lash");
				playerView(ctx, server);
				ctx.waitTicks(20);
			});

			step(ctx, "remove", () -> {
				server.runCommand("clear @a");
				server.runCommand("give @a theboys:uranium_injector");
				ctx.waitTicks(10);
				ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
				ctx.waitTicks(2);
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				String direct = ctx.computeOnClient(mc -> "power(client)=" + PowerAttachments.powerOf(mc.player)
						+ " cooldown=" + mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem())
						+ " mode=" + mc.gameMode.getPlayerMode()
						+ " class=" + mc.player.getMainHandItem().getItem().getClass().getName()
						+ " directUse=" + mc.player.getMainHandItem().use(mc.level, mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)
						+ " usingAfterDirect=" + mc.player.isUsingItem());
				ctx.runOnClient(mc -> mc.player.stopUsingItem());
				LOG.info("remove step direct use: {}", direct);
				ctx.getInput().pressKey(Keys.INJECT);
				for (int i = 0; i < 6; i++) {
					ctx.waitTicks(10);
					String info = ctx.computeOnClient(mc -> "using=" + mc.player.isUsingItem() + " item=" + mc.player.getMainHandItem() + " remaining=" + mc.player.getUseItemRemainingTicks());
					LOG.info("remove step tick {}: {}", i * 10, info);
				}
				Power after = server.computeOnServer(s -> PowerAttachments.powerOf(s.getPlayerList().getPlayers().get(0)));
				if (after != Power.NONE) throw new AssertionError("uranium injector did not remove the power: " + after);
			});
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("The Boys game test failures: " + failures);
		}
	}

	/** Looks at the player from the side through an invisible armor stand. */
	private static void sideView(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
			double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(java.util.Locale.ROOT,
				"summon minecraft:armor_stand %.2f %.2f %.2f {Invisible:1b,NoGravity:1b,Marker:1b,Rotation:[%.1ff,%.1ff]}", x, y, z, yaw, pitch));
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			for (var e : mc.level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.decoration.ArmorStand && !e.isRemoved()) {
					mc.setCameraEntity(e);
				}
			}
		});
		ctx.waitTicks(2);
	}

	private static void playerView(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		ctx.runOnClient(mc -> mc.setCameraEntity(mc.player));
		server.runCommand("kill @e[type=minecraft:armor_stand]");
		ctx.waitTicks(3);
	}

	private interface Step {
		void run() throws Exception;
	}

	private void step(ClientGameTestContext ctx, String name, Step step) {
		LOG.info("=== step {}", name);
		try {
			step.run();
			LOG.info("=== step {} OK", name);
		} catch (Throwable t) {
			LOG.error("=== step {} FAILED", name, t);
			failures.add(name + ": " + t);
			try {
				ctx.takeScreenshot("failed_" + name);
			} catch (Throwable ignored) {
				// nothing
			}
		}
	}

}
