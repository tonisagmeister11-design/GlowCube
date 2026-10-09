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
					ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
					ctx.waitTicks(3);
					ctx.takeScreenshot("item_" + it[1] + "_third_person");
					ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				}
				// shield raised
				ctx.getInput().holdKey(o -> o.keyUse);
				ctx.waitTicks(8);
				ctx.takeScreenshot("item_shield_blocking_first_person");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				ctx.waitTicks(3);
				ctx.takeScreenshot("item_shield_blocking_third_person");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				String blocking = ctx.computeOnClient(mc -> "using=" + mc.player.isUsingItem() + " blocking=" + mc.player.isBlocking());
				LOG.info("shield: {}", blocking);
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
				// full speed through a wall
				server.runCommand("fill -2 -60 30 2 -54 31 minecraft:stone");
				ctx.getInput().lookAt(0, 0);
				ctx.getInput().holdKey(o -> o.keySprint);
				ctx.getInput().holdKey(o -> o.keyUp);
				ctx.waitTicks(14);
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				ctx.waitTicks(6);
				ctx.takeScreenshot("homelander_smash");
				ctx.waitTicks(20);
				ctx.getInput().releaseKey(o -> o.keyUp);
				ctx.getInput().releaseKey(o -> o.keySprint);
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				int wall = server.computeOnServer(s -> {
					int n = 0;
					for (BlockPos p : BlockPos.betweenClosed(-2, -60, 30, 2, -54, 31)) if (!s.overworld().getBlockState(p).isAir()) n++;
					return n;
				});
				double z = ctx.computeOnClient(mc -> mc.player.getZ());
				LOG.info("homelander smash: wall blocks left={} of 70, player z={}", wall, z);
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				server.runCommand("kill @e[type=!player]");
				server.runCommand("tp @a 0 -60 0 0 0");
				ctx.waitTicks(20);
				if (wall > 60 || z < 31) throw new AssertionError("Homelander did not smash through the wall (left=" + wall + ", z=" + z + ")");
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
				// things that happen now must be undone by the Time Jump:
				// a dropped item, a placed block, a mob that walks away
				server.runCommand("tp @a 0 -60 0 0 0");
				server.runCommand("summon minecraft:husk 6 -60 6 {NoAI:1b,PersistenceRequired:1b,CustomName:\"Rewind\",Tags:[\"rewind\"]}");
				ctx.waitTicks(10);
				server.runCommand("give @a minecraft:diamond 1");
				ctx.waitTicks(4);
				ctx.getInput().pressKey(o -> o.keyDrop);
				server.runCommand("setblock 4 -60 -4 minecraft:gold_block");
				for (int i = 1; i <= 6; i++) {
					server.runCommand("tp @e[tag=rewind] 6 -60 " + (6 + i * 2));
					ctx.waitTicks(4);
				}
				ctx.waitTicks(6);
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				ctx.getInput().holdKey(o -> o.keyUp);
				ctx.waitTicks(25);
				ctx.takeScreenshot("a_train_time_jump");
				ctx.getInput().releaseKey(o -> o.keyUp);
				ctx.waitTicks(100);
				// at 1000 km/h he ends up hundreds of blocks away and the test area unloads: go back before checking
				server.runCommand("tp @a 0 -60 0 0 0");
				ctx.waitTicks(30);
				boolean hasDiamond = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) > 0);
				String gold = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(4, -60, -4)).toString());
				double zombieZ = server.computeOnServer(s -> {
					for (var e : s.overworld().getAllEntities()) {
						if (e.getType() == net.minecraft.world.entity.EntityTypes.HUSK) return e.getZ();
					}
					return -999.0;
				});
				LOG.info("time jump: diamond back={} gold block now={} zombie z={}", hasDiamond, gold, zombieZ);
				if (!hasDiamond) throw new AssertionError("dropped diamond did not come back");
				if (gold.contains("gold_block")) throw new AssertionError("placed block was not undone");
				if (zombieZ < -900) throw new AssertionError("rewound mob is gone");
				if (zombieZ > 8.5) throw new AssertionError("zombie did not walk back, z=" + zombieZ);
				ctx.getInput().pressKey(Keys.ABILITY[0]);
				server.runCommand("kill @e[type=!player]");
				ctx.waitTicks(10);
			});

			step(ctx, "butcher", () -> {
				server.runCommand("tp @a 0 -60 0 0 0");
				server.runCommand("theboys power set @a butcher");
				server.runCommand("fill -12 -64 -12 12 -64 30 minecraft:bedrock");
				server.runCommand("fill -12 -63 -12 12 -61 30 minecraft:grass_block");
				server.runCommand("fill -12 -60 -12 12 -50 30 minecraft:air");
				server.runCommand("tp @a 0 -60 0 0 0");
				server.runCommand("summon minecraft:pig 0 -60 4 {NoAI:1b}");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(0, -60, 4));
				ctx.waitTicks(2);
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				ctx.waitTicks(12);
				sideView(ctx, server, 5.5, -58.4, 2.2, 90, 10);
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
				ctx.waitTicks(30);
			});

			step(ctx, "butcher_torn", () -> {
				server.runCommand("kill @e[type=!player]");
				server.runCommand("tp @a 0 -60 0 0 15");
				server.runCommand("fill -12 -64 -12 12 -64 30 minecraft:bedrock");
				server.runCommand("fill -12 -63 -12 12 -61 30 minecraft:grass_block");
				server.runCommand("fill -12 -60 -12 12 -50 30 minecraft:air");
				server.runCommand("theboys power set @a butcher");
				server.runCommand("tp @a 0 -60 0 0 0");
				server.runCommand("summon minecraft:zombie 0 -60 4 {NoAI:1b}");
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(0, -59, 4));
				ctx.getInput().pressKey(Keys.ABILITY[2]);
				sideView(ctx, server, 6, -58, 4, 90, 15);
				ctx.waitTicks(38);
				ctx.takeScreenshot("butcher_tear_1");
				ctx.waitTicks(4);
				ctx.takeScreenshot("butcher_tear_2");
				ctx.waitTicks(14);
				ctx.takeScreenshot("butcher_tear_3");
				playerView(ctx, server);
			});

			step(ctx, "tear_every_mob", () -> {
				String[][] batches = {
						{"zombie", "skeleton", "creeper", "spider", "cow", "pig", "sheep", "chicken", "wolf", "villager", "iron_golem", "enderman"},
						{"slime{Size:2}", "horse", "zombie{IsBaby:1b}", "cat", "witch", "piglin", "fox", "goat", "llama", "polar_bear", "frog", "camel"}};
				int total = 0;
				for (int batch = 0; batch < batches.length; batch++) {
					server.runCommand("kill @e[type=!player]");
					server.runCommand("tp @a 0 -60 -3 0 0");
					String[] mobs = batches[batch];
					for (int i = 0; i < mobs.length; i++) {
						String id = mobs[i];
						String nbt = "{NoAI:1b,Silent:1b,Tags:[\"cut\"]}";
						if (id.contains("{")) {
							nbt = "{NoAI:1b,Silent:1b,Tags:[\"cut\"]," + id.substring(id.indexOf('{') + 1);
							id = id.substring(0, id.indexOf('{'));
						}
						double x = -11 + i * 2.0;
						server.runCommand(String.format(java.util.Locale.ROOT, "summon minecraft:%s %.1f -60 6 %s", id, x, nbt));
					}
					ctx.waitTicks(20);
					int[] before = ctx.computeOnClient(mc -> de.theboys.client.render.TornBodies.STATS.clone());
					sideView(ctx, server, 0, -55.5, -9, 0, 22);
					int torn = server.computeOnServer(s -> {
						var player = s.getPlayerList().getPlayers().get(0);
						List<net.minecraft.world.entity.Entity> victims = new ArrayList<>();
						for (var e : s.overworld().getAllEntities()) if (e instanceof net.minecraft.world.entity.Mob && e.isAlive()) victims.add(e);
						for (var e : victims) de.theboys.power.Butcher.tearApart(player, e, 90f);
						return victims.size();
					});
					ctx.waitTicks(5);
					ctx.takeScreenshot("tear_every_mob_" + batch + "_a");
					ctx.waitTicks(25);
					ctx.takeScreenshot("tear_every_mob_" + batch + "_b");
					int[] after = ctx.computeOnClient(mc -> de.theboys.client.render.TornBodies.STATS.clone());
					List<String> log = ctx.computeOnClient(mc -> new ArrayList<>(de.theboys.client.render.TornBodies.LOG));
					for (String l : log.subList(Math.max(0, log.size() - torn), log.size())) LOG.info("torn: {}", l);
					playerView(ctx, server);
					LOG.info("tear batch {}: victims={} cut={} fallback={}", batch, torn, after[0] - before[0], after[1] - before[1]);
					if (torn != mobs.length) throw new AssertionError("only " + torn + " of " + mobs.length + " mobs spawned");
					if (after[0] - before[0] != torn) {
						throw new AssertionError("only " + (after[0] - before[0]) + " of " + torn + " mobs were cut through their model");
					}
					total += torn;
				}
				LOG.info("every mob torn through its model: {}", total);
			});

			step(ctx, "tear_closeup", () -> {
				String[] mobs = {"zombie", "cow", "spider"};
				for (String mob : mobs) {
					server.runCommand("kill @e[type=!player]");
					server.runCommand("tp @a 0 -60 -8 0 0");
					ctx.runOnClient(mc -> de.theboys.client.render.TornBodies.clear());
					server.runCommand("summon minecraft:" + mob + " 0 -60 4 {NoAI:1b,Silent:1b,Rotation:[" + (mob.equals("zombie") ? "180" : "90") + "f,0f]}");
					ctx.waitTicks(20);
					sideView(ctx, server, 0, -57.6, 0.8, 0, 32);
					ctx.takeScreenshot("tear_closeup_" + mob + "_0");
					server.runOnServer(s -> {
						var player = s.getPlayerList().getPlayers().get(0);
						List<net.minecraft.world.entity.Entity> victims = new ArrayList<>();
						for (var e : s.overworld().getAllEntities()) if (e instanceof net.minecraft.world.entity.Mob && e.isAlive()) victims.add(e);
						for (var e : victims) de.theboys.power.Butcher.tearApart(player, e, 90f);
					});
					ctx.waitTicks(4);
					ctx.takeScreenshot("tear_closeup_" + mob + "_1");
					ctx.waitTicks(70);
					ctx.takeScreenshot("tear_closeup_" + mob + "_2");
					playerView(ctx, server);
				}
			});

			step(ctx, "super_cancer", () -> {
				server.runCommand("kill @e[type=!player]");
				server.runCommand("tp @a 0 -60 0 0 0");
				server.runCommand("summon minecraft:zombie 4 -60 3 {NoAI:1b,Tags:[\"threat\"]}");
				server.runCommand("summon minecraft:skeleton -4 -60 3 {NoAI:1b,Tags:[\"threat\"]}");
				server.runCommand("summon minecraft:zombie 0 -60 6 {NoAI:1b,Tags:[\"threat\"]}");
				server.runCommand("summon minecraft:cow 0 -60 -5");
				ctx.waitTicks(30);
				server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setHealth(5.0f));
				sideView(ctx, server, 9, -57, -5, 50, 20);
				ctx.waitTicks(14);
				ctx.takeScreenshot("super_cancer_1");
				ctx.waitTicks(16);
				ctx.takeScreenshot("super_cancer_2");
				ctx.waitTicks(12);
				ctx.takeScreenshot("super_cancer_3");
				playerView(ctx, server);
				int threats = server.computeOnServer(s -> {
					int n = 0;
					for (var e : s.overworld().getAllEntities()) if ((e.getType() == net.minecraft.world.entity.EntityTypes.ZOMBIE || e.getType() == net.minecraft.world.entity.EntityTypes.SKELETON) && e.isAlive()) n++;
					return n;
				});
				boolean cowAlive = server.computeOnServer(s -> {
					for (var e : s.overworld().getAllEntities()) if (e.getType() == net.minecraft.world.entity.EntityTypes.COW && e.isAlive()) return true;
					return false;
				});
				LOG.info("super cancer: threats left={} cow alive={}", threats, cowAlive);
				if (threats > 0) throw new AssertionError(threats + " monsters survived the Super Cancer");
				// it has to come again, every time
				for (int round = 2; round <= 3; round++) {
					server.runCommand("summon minecraft:zombie 3 -60 4 {NoAI:1b}");
					server.runCommand("summon minecraft:husk -3 -60 4 {NoAI:1b}");
					ctx.waitTicks(15);
					server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setHealth(5.0f));
					ctx.waitTicks(50);
					int left = server.computeOnServer(s -> {
						int n = 0;
						for (var e : s.overworld().getAllEntities()) if ((e.getType() == net.minecraft.world.entity.EntityTypes.ZOMBIE || e.getType() == net.minecraft.world.entity.EntityTypes.HUSK) && e.isAlive()) n++;
						return n;
					});
					LOG.info("super cancer round {}: monsters left={}", round, left);
					if (left > 0) throw new AssertionError("Super Cancer did not come again in round " + round);
				}
				server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setHealth(20.0f));
			});

			step(ctx, "cancer_walk", () -> {
				server.runCommand("kill @e[type=!player]");
				server.runCommand("tp @a 0 -60 0 0 0");
				server.runCommand("fill -12 -64 -12 12 -64 30 minecraft:bedrock");
				server.runCommand("fill -12 -63 -12 12 -61 30 minecraft:dirt");
				server.runCommand("fill -12 -60 -12 12 -50 30 minecraft:air");
				server.runCommand("fill -2 -60 8 2 -56 9 minecraft:stone");
				ctx.waitTicks(10);
				ctx.getInput().lookAt(0, 10);
				server.runCommand("theboys power set @a butcher");
				ctx.waitTicks(5);
				ctx.getInput().pressKey(Keys.ABILITY[3]);
				for (int i = 0; i < 6; i++) {
					ctx.waitTicks(5);
					String c = ctx.computeOnClient(mc -> "client y=" + mc.player.getY() + " v=" + mc.player.getDeltaMovement() + " onGround=" + mc.player.onGround()
							+ " flag=" + PowerAttachments.active(mc.player).has(de.theboys.power.ActiveState.CANCER_WALK));
					String sv = server.computeOnServer(s -> "server y=" + s.getPlayerList().getPlayers().get(0).getY());
					LOG.info("cancer walk lift {}: {} | {}", i, c, sv);
				}
				double y = ctx.computeOnClient(mc -> mc.player.getY() - de.theboys.client.SupeMovement.groundBelow(mc.level, mc.player.position()));
				LOG.info("cancer walk height above ground: {}", y);
				sideView(ctx, server, 7, -57, 2, 70, 10);
				ctx.takeScreenshot("cancer_walk_1");
				playerView(ctx, server);
				ctx.getInput().holdKey(o -> o.keyUp);
				ctx.waitTicks(10);
				sideView(ctx, server, 7, -57, 8, 90, 10);
				ctx.waitTicks(14);
				ctx.takeScreenshot("cancer_walk_2");
				playerView(ctx, server);
				ctx.getInput().releaseKey(o -> o.keyUp);
				int stone = server.computeOnServer(s -> {
					int n = 0;
					for (BlockPos p : BlockPos.betweenClosed(-2, -60, 8, 2, -56, 9)) if (!s.overworld().getBlockState(p).isAir()) n++;
					return n;
				});
				LOG.info("cancer walk: wall blocks left={} of 50", stone);
				ctx.getInput().pressKey(Keys.ABILITY[3]);
				ctx.waitTicks(20);
				if (y < 3.5) throw new AssertionError("Cancer Walk did not lift him, height=" + y);
				if (stone > 40) throw new AssertionError("Cancer Walk did not crush the wall, left=" + stone);
				ctx.waitTicks(20);
			});

			step(ctx, "minimaus", () -> {
				List<String> problems = new ArrayList<>();
				server.runCommand("theboys power clear @a");
				server.runCommand("kill @e[type=!player]");
				server.runCommand("fill -12 -64 -12 12 -64 30 minecraft:bedrock");
				server.runCommand("fill -12 -63 -12 12 -61 30 minecraft:grass_block");
				server.runCommand("fill -12 -60 -12 12 -40 30 minecraft:air");
				server.runCommand("tp @a 0 -60 0 0 0");
				// the new syringe
				server.runCommand("clear @a");
				server.runCommand("give @a theboys:mini_v");
				ctx.waitTicks(10);
				ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				ctx.waitTicks(4);
				ctx.takeScreenshot("minimaus_hold_mini_v");
				ctx.getInput().pressKey(Keys.INJECT);
				ctx.waitTicks(55);
				Power got = server.computeOnServer(s -> PowerAttachments.powerOf(s.getPlayerList().getPlayers().get(0)));
				LOG.info("Mini V gave: {}", got);
				if (got != Power.MINIMAUS) throw new AssertionError("Mini V gave " + got);
				double speed = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
				LOG.info("minimaus speed attribute={}", speed);
				if (speed < 0.19) throw new AssertionError("MiniMaus is not twice as fast: " + speed);

				// the mouse skin with ears and tail
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				ctx.waitTicks(6);
				ctx.takeScreenshot("minimaus_skin_front");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				ctx.waitTicks(4);
				ctx.takeScreenshot("minimaus_skin_back");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				sideView(ctx, server, 2.6, -58.6, 0.5, 90, 10);
				ctx.takeScreenshot("minimaus_skin_side");
				playerView(ctx, server);

				// Poison Bite
				server.runCommand("summon minecraft:cow 0.5 -60 2.3 {NoAI:1b,Rotation:[180f,0f]}");
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(0, -60, 2));
				ctx.getInput().pressKey(Keys.ABILITY[0]);
				ctx.waitTicks(3);
				sideView(ctx, server, 3.2, -58.7, 1.4, 90, 10);
				ctx.takeScreenshot("minimaus_bite_ready");
				// aiming goes through the camera, so bite with the player's own view, then look from the side
				ctx.runOnClient(mc -> mc.setCameraEntity(mc.player));
				ctx.waitTicks(1);
				ctx.getInput().pressKey(o -> o.keyAttack);
				standCamera(ctx);
				ctx.waitTicks(1);
				ctx.takeScreenshot("minimaus_bite");
				ctx.waitTicks(3);
				ctx.takeScreenshot("minimaus_bite_2");
				playerView(ctx, server);
				boolean poisoned = server.computeOnServer(s -> {
					for (var e : s.overworld().getAllEntities()) {
						if (e.getType() == net.minecraft.world.entity.EntityTypes.COW && e instanceof net.minecraft.world.entity.LivingEntity c) return c.hasEffect(net.minecraft.world.effect.MobEffects.POISON);
					}
					return false;
				});
				LOG.info("poison bite: cow poisoned={}", poisoned);
				if (!poisoned) problems.add("Poison Bite did not poison the cow");
				server.runCommand("kill @e[type=!player]");
				// let the dying cow disappear, or the next punch lands on its body
				ctx.waitTicks(30);

				// To the Moon
				server.runCommand("summon minecraft:cow 0.5 -60 2.3 {NoAI:1b,CustomName:\"Moo\"}");
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(0, -60, 2));
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				sideView(ctx, server, 4.5, -58.5, 1.2, 90, 0);
				ctx.runOnClient(mc -> mc.setCameraEntity(mc.player));
				ctx.waitTicks(1);
				ctx.getInput().pressKey(o -> o.keyAttack);
				standCamera(ctx);
				ctx.waitTicks(2);
				ctx.takeScreenshot("minimaus_moon_windup");
				ctx.waitTicks(5);
				ctx.takeScreenshot("minimaus_moon_hit");
				ctx.waitTicks(4);
				ctx.takeScreenshot("minimaus_moon_launch");
				playerView(ctx, server);
				double maxY = -60;
				for (int i = 0; i < 14; i++) {
					ctx.waitTicks(5);
					double y = server.computeOnServer(s -> {
						for (var e : s.overworld().getAllEntities()) if (e.getType() == net.minecraft.world.entity.EntityTypes.COW) return e.getY();
						return -999.0;
					});
					LOG.info("to the moon: cow y={}", y);
					maxY = Math.max(maxY, y);
				}
				LOG.info("to the moon: cow flew up to y={} ({} blocks)", maxY, maxY + 60);
				if (maxY + 60 < 100) problems.add("To the Moon only reached " + (maxY + 60) + " blocks");
				server.runCommand("kill @e[type=!player]");

				// Multi Smash, full size, on an iron golem
				server.runCommand("summon minecraft:iron_golem 0.5 -60 2.6 {NoAI:1b}");
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(0, -59, 2));
				sideView(ctx, server, 0.5, -57.9, 6.8, 180, 14);
				ctx.getInput().pressKey(Keys.ABILITY[2]);
				int[] shots = {6, 12, 17, 23, 30, 37, 44, 52};
				int done = 0;
				for (int at : shots) {
					ctx.waitTicks(at - done);
					done = at;
					ctx.takeScreenshot("minimaus_smash_" + at);
				}
				float golem = server.computeOnServer(s -> {
					for (var e : s.overworld().getAllEntities()) if (e.getType() == net.minecraft.world.entity.EntityTypes.IRON_GOLEM && e instanceof net.minecraft.world.entity.LivingEntity g) return g.getHealth();
					return -1f;
				});
				playerView(ctx, server);
				LOG.info("multi smash: golem health after={}", golem);
				if (golem > 80f) problems.add("Multi Smash did not hurt the golem: " + golem);
				server.runCommand("kill @e[type=!player]");
				ctx.waitTicks(230);

				// shrink to a pixel
				server.runCommand("tp @a 0.5 -60 0.3 0 0");
				ctx.waitTicks(3);
				ctx.getInput().pressKey(Keys.ABILITY[3]);
				ctx.waitTicks(20);
				double scale = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE));
				float height = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getBbHeight());
				double smallSpeed = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
				LOG.info("minimaus small: scale={} height={} speed={}", scale, height, smallSpeed);
				if (smallSpeed > 0.02) problems.add("tiny MiniMaus is still too fast: " + smallSpeed);
				if (scale > 0.07 || height > 0.2f) problems.add("MiniMaus did not shrink: " + scale);
				sideView(ctx, server, 1.3, -59.85, 0.5, 90, 12);
				ctx.takeScreenshot("minimaus_tiny");
				playerView(ctx, server);

				// nibble pixels out of a block by holding the attack key
				server.runCommand("setblock 0 -60 1 minecraft:oak_planks");
				server.runCommand("setblock 1 -60 1 minecraft:stone");
				ctx.waitTicks(5);
				ctx.getInput().lookAt(0, 0);
				ctx.waitTicks(2);
				ctx.takeScreenshot("minimaus_tiny_view");
				ctx.getInput().holdKey(o -> o.keyAttack);
				ctx.waitTicks(40);
				ctx.getInput().releaseKey(o -> o.keyAttack);
				ctx.waitTicks(5);
				int left = server.computeOnServer(s -> s.overworld().getBlockEntity(new BlockPos(0, -60, 1)) instanceof de.theboys.block.CarvedBlockEntity be ? be.remaining() : -1);
				LOG.info("pixel mining: pixels left in the plank block={}", left);
				if (left < 0 || left >= 4096) problems.add("no pixels were mined: " + left);
				ctx.takeScreenshot("minimaus_tiny_mined");
				// carve a little mouse hole into the stone so the pixels show
				server.runOnServer(s -> {
					var level = s.overworld();
					BlockPos pos = new BlockPos(1, -60, 1);
					for (int y = 0; y < 9; y++) for (int x = 4; x < 12; x++) {
						double dx = (x + 0.5 - 8) / 4.0, dy = (y + 0.5) / 9.0;
						if (dx * dx + dy * dy * 0.9 > 1.0) continue;
						for (int d = 0; d < 10; d++) {
							de.theboys.block.PixelCarving.carve(level, pos, new net.minecraft.world.phys.Vec3(1 + (x + 0.5) / 16.0, -60 + (y + 0.5) / 16.0, 1.0),
									net.minecraft.core.Direction.NORTH, true);
						}
					}
				});
				ctx.waitTicks(5);
				sideView(ctx, server, 1.0, -59.5, -0.6, -20, 25);
				ctx.takeScreenshot("minimaus_mouse_hole");
				playerView(ctx, server);

				// still strong when tiny: Multi Smash on a zombie
				server.runCommand("tp @a 0.5 -60 0.3 0 0");
				server.runCommand("setblock 0 -60 1 minecraft:air");
				server.runCommand("setblock 1 -60 1 minecraft:air");
				server.runCommand("summon minecraft:husk 0.5 -60 1.0 {NoAI:1b,Silent:1b}");
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(0, -60, 0));
				ctx.getInput().lookAt(0, 0);
				ctx.getInput().pressKey(Keys.ABILITY[2]);
				sideView(ctx, server, 0.5, -59.3, 4.5, 180, 8);
				ctx.waitTicks(16);
				ctx.takeScreenshot("minimaus_tiny_smash_1");
				ctx.waitTicks(8);
				ctx.takeScreenshot("minimaus_tiny_smash_2");
				playerView(ctx, server);
				ctx.getInput().pressKey(Keys.ABILITY[3]);
				ctx.waitTicks(20);
				// full size again: blocks are mined normally, not pixel by pixel
				server.runCommand("kill @e[type=!player]");
				server.runCommand("tp @a 0.5 -60 0.5 0 30");
				server.runCommand("setblock 0 -60 2 minecraft:dirt");
				ctx.waitTicks(5);
				ctx.getInput().lookAt(new BlockPos(0, -60, 2));
				ctx.getInput().holdKey(o -> o.keyAttack);
				ctx.waitTicks(40);
				ctx.getInput().releaseKey(o -> o.keyAttack);
				ctx.waitTicks(3);
				String big = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(0, -60, 2)).toString());
				LOG.info("full size mining: block now={}", big);
				if (!big.contains("air")) problems.add("full-size MiniMaus did not mine the dirt normally: " + big);
				server.runCommand("theboys power clear @a");
				if (!problems.isEmpty()) throw new AssertionError(String.join("; ", problems));
			});

			step(ctx, "minimaus_sneak_moves", () -> {
				server.runCommand("theboys power clear @a");
				server.runCommand("kill @e[type=!player]");
				server.runCommand("fill -12 -64 -12 12 -64 30 minecraft:bedrock");
				server.runCommand("fill -12 -63 -12 12 -61 30 minecraft:grass_block");
				server.runCommand("fill -12 -60 -12 12 -40 30 minecraft:air");
				server.runCommand("theboys power set @a minimaus");
				server.runCommand("tp @a 0.5 -60 0.5 0 0");
				ctx.waitTicks(20);
				ctx.getInput().lookAt(0, 0);
				List<String> problems = new ArrayList<>();
				// Rat Flood
				for (int i = 0; i < 4; i++) {
					server.runCommand(String.format(java.util.Locale.ROOT, "summon minecraft:husk %.1f -60 %.1f {NoAI:1b,Silent:1b,Tags:[\"rat\"]}", -1.5 + i, 6.5 + i % 2));
				}
				ctx.waitTicks(10);
				sideView(ctx, server, 5.5, -58.0, 2.0, 60, 18);
				ctx.getInput().holdKey(o -> o.keyShift);
				ctx.waitTicks(2);
				ctx.getInput().pressKey(Keys.ABILITY[0]);
				ctx.waitTicks(2);
				ctx.getInput().releaseKey(o -> o.keyShift);
				ctx.waitTicks(4);
				ctx.takeScreenshot("minimaus_rats_1");
				ctx.waitTicks(6);
				ctx.takeScreenshot("minimaus_rats_2");
				ctx.waitTicks(16);
				int hurt = server.computeOnServer(s -> {
					int n = 0;
					for (var e : s.overworld().getAllEntities()) {
						if (e.getType() == net.minecraft.world.entity.EntityTypes.HUSK && e instanceof net.minecraft.world.entity.LivingEntity l && l.getHealth() < l.getMaxHealth()) n++;
					}
					return n;
				});
				LOG.info("rat flood: {} of 4 husks hit", hurt);
				if (hurt < 3) problems.add("Rat Flood only hit " + hurt + " husks");
				playerView(ctx, server);
				server.runCommand("kill @e[type=!player]");
				ctx.waitTicks(25);
				// Squeak
				server.runCommand("summon minecraft:husk 2.5 -60 2.5 {NoAI:1b,Silent:1b}");
				server.runCommand("summon minecraft:husk -1.5 -60 1.5 {NoAI:1b,Silent:1b}");
				ctx.waitTicks(10);
				sideView(ctx, server, 0.5, -57.5, 7.5, 180, 20);
				ctx.getInput().holdKey(o -> o.keyShift);
				ctx.waitTicks(2);
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				ctx.waitTicks(2);
				ctx.getInput().releaseKey(o -> o.keyShift);
				ctx.waitTicks(2);
				ctx.takeScreenshot("minimaus_squeak");
				ctx.waitTicks(10);
				int nauseous = server.computeOnServer(s -> {
					int n = 0;
					for (var e : s.overworld().getAllEntities()) {
						if (e instanceof net.minecraft.world.entity.LivingEntity l && !(e instanceof net.minecraft.world.entity.player.Player)
								&& l.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA)) n++;
					}
					return n;
				});
				LOG.info("squeak: {} of 2 husks reeling", nauseous);
				if (nauseous < 2) problems.add("Squeak hit only " + nauseous + " husks");
				playerView(ctx, server);
				server.runCommand("kill @e[type=!player]");
				// Giant Mouse
				ctx.getInput().holdKey(o -> o.keyShift);
				ctx.waitTicks(2);
				ctx.getInput().pressKey(Keys.ABILITY[2]);
				ctx.waitTicks(2);
				ctx.getInput().releaseKey(o -> o.keyShift);
				ctx.waitTicks(25);
				double scale = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE));
				LOG.info("giant mouse: scale={}", scale);
				if (scale < 2.3) problems.add("Giant Mouse did not grow: " + scale);
				sideView(ctx, server, 0.5, -55.0, 9.5, 180, 12);
				ctx.takeScreenshot("minimaus_giant");
				playerView(ctx, server);
				server.runCommand("theboys power clear @a");
				ctx.waitTicks(5);
				if (!problems.isEmpty()) throw new AssertionError(String.join("; ", problems));
			});

			step(ctx, "minimaus_hideout", () -> {
				// a tunnel into a stone wall and a hollow room inside: tiny MiniMaus walks in and hides
				server.runCommand("theboys power clear @a");
				server.runCommand("kill @e[type=!player]");
				server.runCommand("fill -12 -64 -12 12 -64 30 minecraft:bedrock");
				server.runCommand("fill -12 -63 -12 12 -61 30 minecraft:grass_block");
				server.runCommand("fill -12 -60 -12 12 -40 30 minecraft:air");
				server.runCommand("fill -1 -60 0 1 -58 1 minecraft:stone");
				server.runCommand("theboys power set @a minimaus");
				server.runCommand("tp @a 0.5 -60 -0.5 0 0");
				ctx.waitTicks(20);
				ctx.getInput().pressKey(Keys.ABILITY[3]);
				ctx.waitTicks(25);
				server.runOnServer(s -> {
					var level = s.overworld();
					BlockPos front = new BlockPos(0, -60, 0), back = new BlockPos(0, -60, 1);
					// tunnel: 4 pixels wide, 3 high, right through the front block
					for (int x = 6; x < 10; x++) for (int y = 0; y < 3; y++) for (int d = 0; d < 16; d++) {
						de.theboys.block.PixelCarving.carve(level, front, new net.minecraft.world.phys.Vec3((x + 0.5) / 16.0, -60 + (y + 0.5) / 16.0, 0.0),
								net.minecraft.core.Direction.NORTH, true);
					}
					// a room inside the back block
					for (int x = 3; x < 13; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 12; z++) {
						de.theboys.block.PixelCarving.carve(level, back, new net.minecraft.world.phys.Vec3((x + 0.5) / 16.0, -60 + (y + 0.5) / 16.0, 1 + (z + 0.5) / 16.0),
								net.minecraft.core.Direction.NORTH, true);
					}
				});
				ctx.waitTicks(5);
				sideView(ctx, server, 0.9, -59.6, -0.9, 20, 18);
				ctx.takeScreenshot("hideout_entrance");
				playerView(ctx, server);
				ctx.getInput().lookAt(0, 0);
				// tiny, she walks at a speed that fits her size: about two blocks in four seconds
				ctx.getInput().holdKey(o -> o.keyUp);
				ctx.waitTicks(80);
				ctx.getInput().releaseKey(o -> o.keyUp);
				ctx.waitTicks(5);
				ctx.takeScreenshot("hideout_inside_first_person");
				ctx.getInput().lookAt(180, 0);
				ctx.waitTicks(3);
				ctx.takeScreenshot("hideout_inside_looking_out");
				double[] pos = ctx.computeOnClient(mc -> new double[] {mc.player.getX(), mc.player.getY(), mc.player.getZ()});
				float hurt = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getHealth());
				LOG.info("hideout: player at x={} y={} z={} health={}", pos[0], pos[1], pos[2], hurt);
				sideView(ctx, server, 2.6, -58.9, 0.4, 90, 20);
				ctx.takeScreenshot("hideout_from_outside");
				playerView(ctx, server);
				ctx.getInput().pressKey(Keys.ABILITY[3]);
				ctx.waitTicks(10);
				server.runCommand("tp @a 0.5 -60 -3 0 0");
				server.runCommand("theboys power clear @a");
				if (pos[2] < 1.0) throw new AssertionError("MiniMaus did not get into the hollow block: z=" + pos[2]);
			});

			step(ctx, "video_multi_smash", () -> {
				// a short film: MiniMaus smashes a polar bear five times into the ground and throws it away
				server.runCommand("theboys power clear @a");
				server.runCommand("kill @e[type=!player]");
				server.runCommand("fill -14 -64 -14 14 -64 30 minecraft:bedrock");
				server.runCommand("fill -14 -63 -14 14 -61 30 minecraft:grass_block");
				server.runCommand("fill -14 -60 -14 14 -40 30 minecraft:air");
				server.runCommand("clear @a");
				server.runCommand("time set noon");
				server.runCommand("theboys power set @a minimaus");
				server.runCommand("tp @a 0.5 -60 0.5 0 0");
				ctx.waitTicks(40);
				server.runCommand("summon minecraft:polar_bear 0.5 -60 2.7 {NoAI:1b,Silent:1b,Health:100f,Rotation:[180f,0f],"
						+ "attributes:[{id:\"minecraft:max_health\",base:100d}]}");
				ctx.waitTicks(20);
				ctx.getInput().lookAt(new BlockPos(0, -59, 2));
				de.theboys.client.ClientState.hideHud = true;
				double cx = 4.4, cy = -58.3, cz = 3.9, tx = 0.5, ty = -58.6, tz = 1.4;
				double dx = tx - cx, dy = ty - cy, dz = tz - cz;
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
				sideView(ctx, server, cx, cy, cz, yaw, pitch);
				ctx.waitTicks(5);
				int frame = 0;
				for (int i = 0; i < 10; i++) {
					ctx.takeScreenshot(String.format("video_%03d", frame++));
					ctx.waitTicks(1);
				}
				ctx.getInput().pressKey(Keys.ABILITY[2]);
				for (int i = 0; i < 78; i++) {
					ctx.waitTicks(1);
					ctx.takeScreenshot(String.format("video_%03d", frame++));
				}
				float bear = server.computeOnServer(s -> {
					for (var e : s.overworld().getAllEntities()) {
						if (e.getType() == net.minecraft.world.entity.EntityTypes.POLAR_BEAR && e instanceof net.minecraft.world.entity.LivingEntity l) return l.getHealth();
					}
					return -1f;
				});
				LOG.info("video: {} frames, polar bear health after the smash={}", frame, bear);
				de.theboys.client.ClientState.hideHud = false;
				playerView(ctx, server);
				server.runCommand("kill @e[type=!player]");
				server.runCommand("theboys power clear @a");
			});

			step(ctx, "animations", () -> {
				server.runCommand("kill @e[type=!player]");
				server.runCommand("theboys power clear @a");
				server.runCommand("tp @a 0 -60 0 0 0");
				server.runCommand("summon minecraft:husk 0.5 -60 2.8 {NoAI:1b,Invulnerable:1b,Silent:1b,Rotation:[180f,0f]}");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(0, -59, 2));
				String[][] weapons = {{"netherite_sword", "4"}, {"netherite_axe", "2"}, {"air", "4"}};
				for (String[] w : weapons) {
					server.runCommand("item replace entity @a weapon.mainhand with minecraft:" + w[0]);
					ctx.waitTicks(30);
					sideView(ctx, server, 4.8, -58.7, 1.2, 90, 8);
					LOG.info("fight camera: player={} camera={} invisible={}", ctx.computeOnClient(mc -> mc.player.position().toString()),
							ctx.computeOnClient(mc -> mc.getCameraEntity().toString()), ctx.computeOnClient(mc -> mc.player.isInvisible()));
					int swings = Integer.parseInt(w[1]);
					for (int i = 0; i < swings; i++) {
						ctx.getInput().pressKey(o -> o.keyAttack);
						ctx.waitTicks(2);
						ctx.takeScreenshot("fight_" + w[0] + "_" + i);
						ctx.waitTicks(6);
					}
					// the same moves seen from the front (F5)
					playerView(ctx, server);
					server.runCommand("kill @e[type=minecraft:husk]");
					ctx.getInput().lookAt(0, 0);
					ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
					ctx.waitTicks(30);
					for (int i = 0; i < swings; i++) {
						ctx.getInput().pressKey(o -> o.keyAttack);
						ctx.waitTicks(2);
						ctx.takeScreenshot("fight_front_" + w[0] + "_" + i);
						ctx.waitTicks(6);
					}
					ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
					server.runCommand("summon minecraft:husk 0.5 -60 2.8 {NoAI:1b,Invulnerable:1b,Silent:1b,Rotation:[180f,0f]}");
					ctx.getInput().lookAt(new BlockPos(0, -59, 2));
					int combo = ctx.computeOnClient(mc -> {
						var sw = de.theboys.client.CombatAnim.get(mc.player.getId());
						return sw == null ? -1 : sw.combo;
					});
					LOG.info("fight {}: combo={}", w[0], combo);
					playerView(ctx, server);
					if (combo != swings - 1) throw new AssertionError(w[0] + ": combo " + combo + " after " + swings + " swings");
				}

				// Homelander lies into his flight, seen from the side
				server.runCommand("kill @e[type=!player]");
				server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
				server.runCommand("theboys power set @a homelander");
				server.runCommand("tp @a 0 -45 -30 0 0");
				ctx.waitTicks(10);
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				ctx.waitTicks(4);
				ctx.getInput().lookAt(0, 0);
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				ctx.getInput().holdKey(o -> o.keyUp);
				ctx.getInput().holdKey(o -> o.keySprint);
				ctx.waitTicks(20);
				float lean = ctx.computeOnClient(mc -> de.theboys.client.BodyLean.current(mc.player));
				ctx.takeScreenshot("fight_homelander_flight_back");
				// a camera beside his flight path for one frame
				for (int i = 0; i < 3; i++) {
					// a camera that flies along beside him
					ctx.runOnClient(mc -> de.theboys.client.ClientState.cameraOverride =
							new double[] {mc.player.getX() + 5.5, mc.player.getY() + 0.6, mc.player.getZ() + 0.6, 90, 4});
					ctx.waitTicks(1);
				}
				ctx.takeScreenshot("fight_homelander_flight_side");
				playerView(ctx, server);
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				ctx.getInput().lookAt(0, -75);
				ctx.waitTicks(16);
				float leanUp = ctx.computeOnClient(mc -> de.theboys.client.BodyLean.current(mc.player));
				ctx.takeScreenshot("fight_homelander_flight_up");
				ctx.getInput().releaseKey(o -> o.keyUp);
				ctx.getInput().releaseKey(o -> o.keySprint);
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				LOG.info("homelander flight lean up={}", leanUp);
				playerView(ctx, server);
				ctx.getInput().pressKey(Keys.ABILITY[1]);
				server.runCommand("theboys power clear @a");
				server.runCommand("tp @a 0 -60 0 0 0");
				LOG.info("homelander flight lean={}", lean);
				if (lean < 45f) throw new AssertionError("Homelander does not lean into his flight: " + lean);
				ctx.waitTicks(10);
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

	/** Looks at the player from a fixed camera beside him (the player stays the camera entity, so he is drawn). */
	private static void sideView(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
			double x, double y, double z, float yaw, float pitch) {
		de.theboys.client.ClientState.cameraOverride = new double[] {x, y, z, yaw, pitch};
		ctx.runOnClient(mc -> {
			mc.setCameraEntity(mc.player);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		ctx.waitTicks(2);
	}

	/** Kept for older steps: the fixed camera needs no switching any more. */
	private static void standCamera(ClientGameTestContext ctx) {
	}

	private static void playerView(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		de.theboys.client.ClientState.cameraOverride = null;
		ctx.runOnClient(mc -> {
			mc.setCameraEntity(mc.player);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
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
