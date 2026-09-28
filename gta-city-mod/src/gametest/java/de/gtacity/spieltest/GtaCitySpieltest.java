package de.gtacity.spieltest;

import de.gtacity.client.ClientInput;
import de.gtacity.client.screen.ShopScreen;
import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.entity.GrenadeEntity;
import de.gtacity.entity.NpcEntity;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.entity.RocketEntity;
import de.gtacity.gameplay.Economy;
import de.gtacity.gameplay.WantedSystem;
import de.gtacity.item.GunItem;
import de.gtacity.registry.ModAttachments;
import de.gtacity.registry.ModBlocks;
import de.gtacity.registry.ModEntities;
import de.gtacity.registry.ModItems;
import de.gtacity.world.CityChunkGenerator;
import de.gtacity.world.CityLayout;
import de.gtacity.world.CityPlaces;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays the mod in the real game: creates a city world, walks, drives, shoots, gets wanted, shops and dies, and
 * takes a screenshot of every step. Each check logs one line "GTACITY-TEST OK|FEHLER ..."; a failing check does
 * not stop the others. Run with {@code xvfb-run gradle runClientGameTest -Pspieltest}.
 */
public final class GtaCitySpieltest implements FabricClientGameTest {
    private final List<String> results = new ArrayList<>();
    private int failures;

    private interface Step {
        void run() throws Exception;
    }

    private void ok(String what) {
        String line = "GTACITY-TEST OK      " + what;
        results.add(line);
        System.out.println(line);
    }

    private void fail(String what) {
        failures++;
        String line = "GTACITY-TEST FEHLER  " + what;
        results.add(line);
        System.out.println(line);
    }

    private void expect(boolean condition, String what) {
        if (condition) {
            ok(what);
        } else {
            fail(what);
        }
    }

    private void check(String name, Step step) {
        try {
            step.run();
        } catch (Throwable t) {
            fail(name + ": Ausnahme " + t);
            t.printStackTrace();
        }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            mc.options.renderDistance().set(6);
            mc.options.simulationDistance().set(6);
            mc.options.framerateLimit().set(30);
            mc.options.enableVsync().set(false);
        });
        // Like a player: new world, world type "Standard" (which the mod replaces with the city).
        // The consistent test settings would switch to a flat world.
        var builder = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(ui -> ui.setSeed("1"));
        try (TestSingleplayerContext world = builder.create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            waitChunks(conn);
            server.runCommand("time set 6000");
            server.runCommand("weather clear");

            check("Welt", () -> world(ctx, server));
            check("Start", () -> starterKit(ctx, conn));
            check("Strasse", () -> street(ctx, server, conn));
            check("Skyline", () -> skyline(ctx, server, conn));
            check("Verkehr", () -> traffic(ctx, server, conn));
            check("Auto", () -> driving(ctx, server, conn));
            check("Waffen", () -> shooting(ctx, server, conn));
            check("Polizei", () -> police(ctx, server, conn));
            check("Laden", () -> shop(ctx, server, conn));
            check("Sprengstoff", () -> explosives(ctx, server, conn));
            check("Geldautomat und Aufzug", () -> blocks(ctx, server, conn));
            check("Tod", () -> death(ctx, server, conn));
            check("Nacht", () -> night(ctx, server, conn));
        } catch (Throwable t) {
            fail("Test abgebrochen: " + t);
            t.printStackTrace();
        }
        System.out.println("GTACITY-TEST ===== Zusammenfassung =====");
        results.forEach(System.out::println);
        System.out.println("GTACITY-TEST ===== " + (results.size() - failures) + " OK, " + failures + " FEHLER =====");
    }

    // ------------------------------------------------------------------ helpers

    private static void teleport(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
        server.runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            p.teleportTo(p.level(), x, y, z, java.util.Set.of(), yaw, pitch, true);
        });
    }

    private static ServerPlayer player(net.minecraft.server.MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static void settle(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.waitTicks(10);
        waitChunks(conn);
        ctx.waitTicks(10);
    }

    /** Waits until the chunks around the camera are drawn. Software rendering is slow, so be patient. */
    private static void waitChunks(TestServerConnection conn) {
        try {
            conn.waitForChunksRender(20 * 60 * 5);
        } catch (AssertionError slow) {
            System.out.println("GTACITY-TEST Hinweis: Chunks nach 5 Minuten noch nicht fertig gezeichnet");
        }
    }

    // ------------------------------------------------------------------ checks

    private void world(ClientGameTestContext ctx, TestServerContext server) {
        boolean city = server.computeOnServer(s -> s.overworld().getChunkSource().getGenerator()
                instanceof CityChunkGenerator);
        expect(city, "Overworld nutzt den Stadt-Generator");
        double border = server.computeOnServer(s -> s.overworld().getWorldBorder().getSize());
        expect(border == CityLayout.BORDER_SIZE, "Weltgrenze um die Stadt (" + border + ")");
        String version = ctx.computeOnClient(mc -> mc.getLaunchedVersion());
        ok("Spiel läuft: Minecraft " + version);
    }

    private void starterKit(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.waitTicks(20);
        boolean pistol = ctx.computeOnClient(mc -> mc.player.getInventory().contains(new ItemStack(ModItems.PISTOL)));
        expect(pistol, "Startausrüstung mit Pistole");
        Long money = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MONEY));
        expect(money != null && money == 500L, "Startgeld $500 beim Client angekommen (" + money + ")");
        BlockPos spawn = CityPlaces.spawn();
        double dist = ctx.computeOnClient(mc -> mc.player.position().distanceTo(Vec3.atBottomCenterOf(spawn)));
        expect(dist < 3.0, "Spieler steht am Spawnpunkt (Abstand " + String.format("%.1f", dist) + ")");
    }

    private void street(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, -20.0F, 5.0F);
        settle(ctx, conn);
        ctx.takeScreenshot("gtacity-01-strasse");
        boolean road = server.computeOnServer(s -> !s.overworld().getBlockState(
                new BlockPos(9, CityLayout.GROUND, 40)).isAir());
        expect(road, "Straße liegt auf Bodenhöhe");
    }

    private void skyline(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("gamemode spectator @a");
        ctx.runOnClient(mc -> mc.options.renderDistance().set(8));
        teleport(server, -90.5, 170, -90.5, -45.0F, 20.0F);
        settle(ctx, conn);
        ctx.takeScreenshot("gtacity-02-skyline");
        ctx.runOnClient(mc -> mc.options.renderDistance().set(6));
        int tallest = server.computeOnServer(s -> {
            int max = 0;
            ServerLevel level = s.overworld();
            for (int x = -40; x < 120; x += 4) {
                for (int z = -40; z < 120; z += 4) {
                    max = Math.max(max, level.getChunk(x >> 4, z >> 4).getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x & 15, z & 15));
                }
            }
            return max;
        });
        expect(tallest > 120, "Hochhäuser in Downtown (höchster Punkt y=" + tallest + ")");
        server.runCommand("gamemode survival @a");
    }

    private void traffic(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 5.0F);
        ctx.waitTicks(300);
        int[] counts = server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            AABB box = p.getBoundingBox().inflate(100, 40, 100);
            int peds = p.level().getEntitiesOfClass(NpcEntity.class, box, n -> !(n instanceof PoliceEntity)).size();
            int cars = p.level().getEntitiesOfClass(CarEntity.class, box, CarEntity::isAiDriving).size();
            return new int[]{peds, cars};
        });
        expect(counts[0] > 3, "Passanten auf den Gehwegen (" + counts[0] + ")");
        expect(counts[1] > 0, "KI-Verkehr auf den Straßen (" + counts[1] + ")");

        // Every AI car near the player should be driving (or waiting behind another car).
        java.util.Map<Integer, Vec3> before = server.computeOnServer(s -> {
            java.util.Map<Integer, Vec3> map = new java.util.HashMap<>();
            ServerPlayer p = player(s);
            for (CarEntity car : p.level().getEntitiesOfClass(CarEntity.class, p.getBoundingBox().inflate(100, 40, 100),
                    CarEntity::isAiDriving)) {
                map.put(car.getId(), car.position());
            }
            return map;
        });
        java.util.Map<Integer, Integer> ticksBefore = server.computeOnServer(s -> {
            java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
            before.keySet().forEach(id -> {
                var e = s.overworld().getEntity(id);
                map.put(id, e == null ? -1 : e.tickCount);
            });
            return map;
        });
        ctx.waitTicks(60);
        int[] moving = server.computeOnServer(s -> {
            int movingCars = 0, ticking = 0;
            for (var entry : before.entrySet()) {
                var e = s.overworld().getEntity(entry.getKey());
                if (!(e instanceof CarEntity car)) {
                    System.out.println("GTACITY-TEST Diagnose Auto " + entry.getKey() + ": weg");
                    continue;
                }
                double moved = car.position().distanceTo(entry.getValue());
                int ticks = car.tickCount - ticksBefore.get(entry.getKey());
                if (ticks > 0) {
                    ticking++;
                }
                if (moved > 1.0) {
                    movingCars++;
                }
                System.out.println(String.format(java.util.Locale.ROOT,
                        "GTACITY-TEST Diagnose Auto %d: %.1f Bloecke, speed=%.3f, ticks=%d, pos=%s, fahrer=%s",
                        car.getId(), moved, car.speed, ticks, car.blockPosition().toShortString(),
                        car.getFirstPassenger() == null ? "-" : car.getFirstPassenger().getType().toShortString()));
            }
            return new int[]{movingCars, ticking, before.size()};
        });
        expect(moving[2] > 0 && moving[0] * 2 >= moving[1],
                "KI-Autos fahren (" + moving[0] + " von " + moving[1] + " tickenden Autos in 3 s bewegt, "
                        + moving[2] + " insgesamt)");

        // A car and a pedestrian right in front of the camera for the screenshot.
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.COMMAND);
            car.setVariant(CarVariant.SPORTS_RED);
            car.setPersistentCar(true);
            car.snapTo(spawn.getX() - 7.5, CityLayout.GROUND + 1.0, spawn.getZ() + 8.5, 90.0F, 0.0F);
            level.addFreshEntity(car);
            NpcEntity npc = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.COMMAND);
            npc.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 4.5, 180.0F, 0.0F);
            npc.randomizeLook(false);
            npc.setPersistent(true);
            npc.setNoAi(true);
            level.addFreshEntity(npc);
        });
        conn.waitForClientboundEntityUpdates(ModEntities.CAR, ModEntities.PEDESTRIAN);
        ctx.waitTicks(20);
        ctx.takeScreenshot("gtacity-03-verkehr");
        int seen = ctx.computeOnClient(mc -> {
            int n = 0;
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof CarEntity || e instanceof NpcEntity) {
                    n++;
                }
            }
            return n;
        });
        expect(seen > 2, "Client sieht Autos und Passanten (" + seen + ")");
    }

    private void driving(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        BlockPos spawn = CityPlaces.spawn();
        // Right lane of the north-south road, heading south.
        double x = 5.5, z = CityLayout.CORRIDOR + 12.5;
        teleport(server, x + 2.0, CityLayout.GROUND + 1.0, z, 0.0F, 10.0F);
        int carId = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.COMMAND);
            car.setVariant(CarVariant.SEDAN_BLUE);
            car.snapTo(x, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
            level.addFreshEntity(car);
            // Same path as a right click on the car.
            car.interact(player(s), InteractionHand.MAIN_HAND, car.position());
            return car.getId();
        });
        ctx.waitFor(mc -> mc.player.getVehicle() instanceof CarEntity, 100);
        expect(ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof CarEntity), "Eingestiegen");
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK),
                "Kamera wechselt im Auto in die dritte Person");
        Vec3 start = server.computeOnServer(s -> s.overworld().getEntity(carId).position());
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(50);
        ctx.takeScreenshot("gtacity-04-auto");
        ctx.waitTicks(10);
        ctx.getInput().releaseKey(o -> o.keyUp);
        ctx.waitTicks(40);
        Vec3 end = server.computeOnServer(s -> s.overworld().getEntity(carId).position());
        double driven = end.distanceTo(start);
        expect(driven > 8.0, "Auto fährt mit W (" + String.format("%.1f", driven) + " Blöcke)");
        ctx.getInput().holdKeyFor(o -> o.keyShift, 5);
        ctx.waitFor(mc -> mc.player.getVehicle() == null, 60);
        expect(ctx.computeOnClient(mc -> mc.player.getVehicle() == null), "Mit Shift ausgestiegen");
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> mc.options.getCameraType() == CameraType.FIRST_PERSON),
                "Kamera wieder in der ersten Person");
    }

    private void shooting(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        server.runCommand("kill @e[type=gtacity:pedestrian]");
        server.runCommand("item replace entity @a hotbar.0 with gtacity:pistol");
        server.runCommand("give @a gtacity:pistol_ammo 48");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
        int npcId = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            NpcEntity npc = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.COMMAND);
            npc.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 5.5, 180.0F, 0.0F);
            npc.randomizeLook(false);
            npc.setPersistent(true);
            npc.setNoAi(true);
            level.addFreshEntity(npc);
            return npc.getId();
        });
        conn.waitForClientboundEntityUpdates(ModEntities.PEDESTRIAN);
        ctx.waitTicks(10);
        ctx.getInput().lookAt(new BlockPos(spawn.getX(), spawn.getY() + 1, spawn.getZ() + 5));
        ctx.waitTicks(5);
        float healthBefore = server.computeOnServer(s -> s.overworld().getEntity(npcId) instanceof NpcEntity n
                ? n.getHealth() : -1.0F);
        expect(healthBefore > 0, "Ziel-Passant steht vor der Kamera");
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(10);
        int ammo = server.computeOnServer(s -> GunItem.ammo(player(s).getMainHandItem()));
        expect(ammo == 11, "Linksklick schießt (Magazin 12 -> " + ammo + ")");
        float healthAfter = server.computeOnServer(s -> {
            var e = s.overworld().getEntity(npcId);
            return e instanceof NpcEntity n ? n.getHealth() : 0.0F;
        });
        expect(healthAfter < healthBefore, "Treffer macht Schaden (" + healthBefore + " -> " + healthAfter + ")");
        int wanted = server.computeOnServer(s -> WantedSystem.level(player(s)));
        expect(wanted >= 1, "Fahndungslevel steigt (" + wanted + " Sterne)");
        ctx.waitTicks(5);
        Integer clientWanted = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.WANTED));
        expect(clientWanted != null && clientWanted >= 1, "Sterne kommen im HUD an");
        boolean blockIntact = server.computeOnServer(s -> !s.overworld().getBlockState(
                new BlockPos(spawn.getX(), CityLayout.GROUND + 1, spawn.getZ() + 2)).isAir());
        expect(blockIntact, "Linksklick mit Waffe baut keinen Block ab");
        ctx.getInput().holdKey(o -> o.keyUse);
        ctx.waitTicks(5);
        ctx.takeScreenshot("gtacity-05-schiessen");
        ctx.getInput().releaseKey(o -> o.keyUse);

        // Empty the magazine, then reload with R.
        server.runOnServer(s -> player(s).getMainHandItem().set(de.gtacity.registry.ModComponents.AMMO, 2));
        ctx.getInput().pressKey(ClientInput.RELOAD);
        ctx.waitTicks(40);
        int reloaded = server.computeOnServer(s -> GunItem.ammo(player(s).getMainHandItem()));
        expect(reloaded == 12, "R lädt nach (Magazin " + reloaded + ")");
    }

    private void police(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> WantedSystem.setLevel(player(s), 2));
        int cops = 0;
        for (int i = 0; i < 20 && cops == 0; i++) {
            ctx.waitTicks(20);
            cops = server.computeOnServer(s -> player(s).level().getEntitiesOfClass(PoliceEntity.class,
                    player(s).getBoundingBox().inflate(120)).size());
        }
        expect(cops > 0, "Polizei rückt an (" + cops + " Beamte)");
        ctx.waitTicks(60);
        ctx.takeScreenshot("gtacity-06-polizei");
        server.runOnServer(s -> {
            WantedSystem.setLevel(player(s), 0);
            player(s).setHealth(player(s).getMaxHealth());
        });
        server.runCommand("kill @e[type=gtacity:police]");
        server.runCommand("kill @e[type=gtacity:pedestrian]");
        ctx.waitTicks(10);
    }

    private void shop(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        BlockPos counter = spawn.south(2).above();
        server.runOnServer(s -> s.overworld().setBlockAndUpdate(counter, ModBlocks.WEAPON_COUNTER.defaultBlockState()));
        server.runCommand("item replace entity @a hotbar.1 with minecraft:air");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[1]);
        server.runOnServer(s -> Economy.set(player(s), 5000));
        ctx.waitTicks(5);
        ctx.getInput().lookAt(counter);
        ctx.waitTicks(5);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitForScreen(ShopScreen.class);
        ok("Rechtsklick auf die Theke öffnet Ammu-Nation");
        ctx.waitTicks(5);
        ctx.takeScreenshot("gtacity-07-laden");
        ctx.clickScreenButton("Kaufen");
        ctx.waitTicks(10);
        long money = server.computeOnServer(s -> Economy.get(player(s)));
        expect(money < 5000, "Kaufen kostet Geld ($5000 -> $" + money + ")");
        ctx.clickScreenButton("Schließen");
        ctx.waitFor(mc -> mc.gui.screen() == null, 40);

        // Robbery: sneak + right click with a gun.
        server.runOnServer(s -> s.overworld().setBlockAndUpdate(counter, ModBlocks.STORE_COUNTER.defaultBlockState()));
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
        ctx.waitTicks(3);
        int cashBefore = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new AABB(counter).inflate(3),
                i -> i.getItem().is(ModItems.CASH)).size());
        ctx.getInput().holdShift();
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(10);
        ctx.getInput().releaseKey(o -> o.keyShift);
        ctx.getInput().releaseShift();
        int cashAfter = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new AABB(counter).inflate(3),
                i -> i.getItem().is(ModItems.CASH)).size());
        int robWanted = server.computeOnServer(s -> WantedSystem.level(player(s)));
        expect(cashAfter > cashBefore, "Überfall: Beute liegt auf der Theke");
        expect(robWanted >= 2, "Überfall bringt Fahndungssterne (" + robWanted + ")");
        ctx.takeScreenshot("gtacity-07b-ueberfall");
        server.runOnServer(s -> {
            WantedSystem.setLevel(player(s), 0);
            s.overworld().removeBlock(counter, false);
        });
    }

    private void explosives(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("item replace entity @a hotbar.2 with gtacity:rpg");
        server.runCommand("give @a gtacity:rocket 4");
        server.runCommand("item replace entity @a hotbar.3 with gtacity:grenade 4");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[2]);
        ctx.getInput().lookAt(0.0F, -5.0F);
        ctx.waitTicks(5);
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(3);
        int rockets = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(RocketEntity.class,
                player(s).getBoundingBox().inflate(80)).size());
        expect(rockets > 0, "Raketenwerfer feuert eine Rakete");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[3]);
        ctx.getInput().lookAt(0.0F, -30.0F);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(3);
        int grenades = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(GrenadeEntity.class,
                player(s).getBoundingBox().inflate(30)).size());
        expect(grenades > 0, "Granate fliegt");
        ctx.waitTicks(80);
        int left = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(GrenadeEntity.class,
                player(s).getBoundingBox().inflate(60)).size());
        expect(left == 0, "Granate ist explodiert");
        server.runOnServer(s -> {
            WantedSystem.setLevel(player(s), 0);
            player(s).setHealth(player(s).getMaxHealth());
        });
    }

    private void blocks(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        BlockPos atm = spawn.south(2).above();
        server.runOnServer(s -> s.overworld().setBlockAndUpdate(atm, ModBlocks.ATM.defaultBlockState()));
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[1]);
        ctx.getInput().lookAt(atm);
        ctx.waitTicks(5);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(10);
        ok("Geldautomat benutzt");

        // Elevator: two elevator blocks, the lower one takes you up.
        BlockPos low = spawn.east(2);
        BlockPos high = low.above(20);
        server.runOnServer(s -> {
            s.overworld().setBlockAndUpdate(low, ModBlocks.ELEVATOR.defaultBlockState());
            s.overworld().setBlockAndUpdate(high, ModBlocks.ELEVATOR.defaultBlockState());
            s.overworld().removeBlock(atm, false);
        });
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(low), Direction.UP, low, false);
            s.overworld().getBlockState(low).useWithoutItem(s.overworld(), p, hit);
        });
        ctx.waitTicks(5);
        double y = server.computeOnServer(s -> player(s).getY());
        expect(y > high.getY(), "Aufzug fährt nach oben (y=" + y + ")");
        server.runOnServer(s -> {
            s.overworld().removeBlock(low, false);
            s.overworld().removeBlock(high, false);
        });
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
    }

    private void death(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> Economy.set(player(s), 1000));
        server.runCommand("kill @a");
        ctx.waitForScreen(DeathScreen.class);
        ctx.waitTicks(30);
        ctx.clickScreenButton("deathScreen.respawn");
        ctx.waitFor(mc -> mc.gui.screen() == null && mc.player != null && mc.player.isAlive(), 200);
        ctx.waitTicks(20);
        ctx.takeScreenshot("gtacity-08-wasted");
        long money = server.computeOnServer(s -> Economy.get(player(s)));
        expect(money < 1000, "Krankenhausrechnung bezahlt ($1000 -> $" + money + ")");
        BlockPos pos = server.computeOnServer(s -> player(s).blockPosition());
        BlockPos hospital = server.computeOnServer(s -> CityPlaces.nearest(CityLayout.LotType.HOSPITAL, 16, 16));
        double dist = Math.sqrt(pos.distSqr(hospital));
        expect(dist < 6, "Aufwachen am Krankenhaus (Abstand " + String.format("%.1f", dist) + ")");
        settle(ctx, conn);
    }

    private void night(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("time set 18000");
        server.runCommand("gamemode spectator @a");
        teleport(server, -40.5, 110, -40.5, -45.0F, 20.0F);
        settle(ctx, conn);
        ctx.takeScreenshot("gtacity-09-nacht");
        ok("Nachtbild aufgenommen");
    }
}
