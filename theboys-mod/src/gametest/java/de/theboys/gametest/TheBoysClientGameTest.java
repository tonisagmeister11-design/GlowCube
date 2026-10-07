package de.theboys.gametest;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.theboys.client.Keys;
import de.theboys.client.render.EffectRenderer;
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

			step(ctx, "inject", () -> {
				server.runCommand("clear @a");
				server.runCommand("give @a theboys:compound_v");
				ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
				ctx.waitTicks(5);
				ctx.getInput().pressKey(Keys.INJECT);
				ctx.waitTicks(14);
				ctx.takeScreenshot("inject_first_person");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				ctx.waitTicks(4);
				ctx.takeScreenshot("inject_third_person");
				ctx.waitTicks(30);
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				Power got = server.computeOnServer(s -> PowerAttachments.powerOf(s.getPlayerList().getPlayers().get(0)));
				LOG.info("Compound V gave: {}", got);
				if (got != Power.A_TRAIN && got != Power.BUTCHER) throw new AssertionError("Compound V gave " + got);
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
				ctx.runOnClient(mc -> EffectRenderer.debugBoxes = true);
				ctx.waitTicks(3);
				ctx.takeScreenshot("debug_boxes");
				ctx.runOnClient(mc -> EffectRenderer.debugBoxes = false);
				for (int combo = 0; combo < 3; combo++) {
					int ph = combo % 3;
					int type = combo / 3;
					ctx.runOnClient(mc -> {
						EffectRenderer.phase = ph;
						EffectRenderer.glowType = type;
					});
					ctx.waitTicks(3);
					ctx.takeScreenshot("laser_phase_" + ph + "_type_" + type);
					String info = ctx.computeOnClient(mc -> "flags=" + PowerAttachments.active(mc.player).flags()
							+ " calls=" + EffectRenderer.debugCalls + " glows=" + EffectRenderer.debugGlows);
					LOG.info("laser render phase {}: {}", ph, info);
				}
				ctx.runOnClient(mc -> {
					EffectRenderer.phase = 0;
					EffectRenderer.glowType = 0;
				});
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
				ctx.getInput().holdKey(Keys.ABILITY[0]);
				ctx.waitTicks(12);
				ctx.takeScreenshot("soldier_boy_beam");
				ctx.getInput().releaseKey(Keys.ABILITY[0]);
				ctx.getInput().holdKey(Keys.ABILITY[1]);
				ctx.waitTicks(50);
				ctx.takeScreenshot("soldier_boy_nuke_charge");
				ctx.getInput().releaseKey(Keys.ABILITY[1]);
				ctx.waitTicks(6);
				ctx.takeScreenshot("soldier_boy_nuke");
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
				ctx.takeScreenshot("butcher_grab");
				ctx.getInput().pressKey(Keys.ABILITY[2]);
				ctx.waitTicks(25);
				ctx.takeScreenshot("butcher_rip");
				ctx.waitTicks(30);
				ctx.getInput().lookAt(0, 30);
				ctx.getInput().pressKey(Keys.ABILITY[0]);
				ctx.waitTicks(4);
				ctx.takeScreenshot("butcher_lash");
				ctx.waitTicks(20);
			});

			step(ctx, "remove", () -> {
				server.runCommand("clear @a");
				server.runCommand("give @a theboys:uranium_injector");
				ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				String direct = ctx.computeOnClient(mc -> "power(client)=" + PowerAttachments.powerOf(mc.player) + " use=" + mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND) + " using=" + mc.player.isUsingItem());
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
