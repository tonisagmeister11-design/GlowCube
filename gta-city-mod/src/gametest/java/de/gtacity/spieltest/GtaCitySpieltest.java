package de.gtacity.spieltest;

import de.gtacity.client.ClientInput;
import de.gtacity.client.screen.ShopScreen;
import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.entity.GrenadeEntity;
import de.gtacity.entity.NpcEntity;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.entity.RocketEntity;
import de.gtacity.gameplay.CitySpawns;
import de.gtacity.gameplay.Economy;
import de.gtacity.gameplay.PoliceDispatch;
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

    /** GTACITY_NUR=Haltung,Polizei runs only those checks (quick visual iterations). */
    private static final String ONLY = System.getenv("GTACITY_NUR");

    private static boolean selected(String name) {
        if (ONLY == null || ONLY.isBlank()) {
            return true;
        }
        for (String part : ONLY.split(",")) {
            if (name.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private ClientGameTestContext game;

    /** If an earlier check got the player killed, press "Respawn" so the next check starts alive. */
    private void ensureAlive() {
        if (game == null || !game.computeOnClient(mc -> mc.gui.screen() instanceof DeathScreen)) {
            return;
        }
        System.out.println("GTACITY-TEST Hinweis: Spieler war tot, wird wiederbelebt");
        game.waitTicks(30);
        game.clickScreenButton("deathScreen.respawn");
        game.waitFor(mc -> mc.gui.screen() == null && mc.player != null && mc.player.isAlive(), 200);
        game.waitTicks(20);
    }

    private void check(String name, Step step) {
        if (!selected(name)) {
            return;
        }
        try {
            ensureAlive();
            step.run();
        } catch (Throwable t) {
            fail(name + ": Ausnahme " + t);
            t.printStackTrace();
        }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        game = ctx;
        ctx.runOnClient(mc -> {
            mc.options.renderDistance().set(ONLY == null ? 6 : 4);
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
            check("Festnahme", () -> arrest(ctx, server, conn));
            check("Polizeibeschuss", () -> policeShooting(ctx, server, conn));
            check("Streifenwagen", () -> patrolCar(ctx, server, conn));
            check("SWAT", () -> swat(ctx, server, conn));
            check("Abhaengen", () -> loseCops(ctx, server, conn));
            check("Polizist", () -> attackCop(ctx, server, conn));
            check("Autoklau", () -> carJacking(ctx, server, conn));
            check("Autoschaden", () -> carDamage(ctx, server, conn));
            check("Fahren", () -> carHandling(ctx, server, conn));
            check("Autohaus", () -> carDealer(ctx, server, conn));
            check("Waffenarsenal", () -> allGuns(ctx, server, conn));
            check("Nahkampf", () -> melee(ctx, server, conn));
            check("Umgebung", () -> environment(ctx, server, conn));
            check("Tod", () -> death(ctx, server, conn));
            check("Haltung", () -> holding(ctx, server, conn));
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

    /** Full health and food, no stars, no officers or pedestrians around. */
    private static void reset(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            WantedSystem.setLevel(p, 0);
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.removeAllEffects();
            if (p.getVehicle() != null) {
                p.stopRiding();
            }
        });
        server.runCommand("kill @e[type=gtacity:police]");
        server.runCommand("kill @e[type=gtacity:pedestrian]");
        server.runCommand("kill @e[type=minecraft:item]");
    }

    /** A pedestrian that stands still, for target practice. Returns its entity id. */
    private static int dummy(TestServerContext server, double x, double y, double z, boolean gang) {
        return server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            NpcEntity npc = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.COMMAND);
            npc.snapTo(x, y, z, 180.0F, 0.0F);
            npc.randomizeLook(gang);
            npc.setPersistent(true);
            npc.setNoAi(true);
            level.addFreshEntity(npc);
            return npc.getId();
        });
    }

    private static float health(TestServerContext server, int id) {
        return server.computeOnServer(s -> s.overworld().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity l
                && l.isAlive() ? l.getHealth() : 0.0F);
    }

    /** Turns the camera towards a point. */
    private static void aim(ClientGameTestContext ctx, Vec3 target) {
        float[] rot = ctx.computeOnClient(mc -> {
            Vec3 d = target.subtract(mc.player.getEyePosition());
            double flat = Math.sqrt(d.x * d.x + d.z * d.z);
            float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
            return new float[]{yaw, pitch};
        });
        ctx.getInput().lookAt(rot[0], rot[1]);
        ctx.waitTicks(3);
    }

    /** Rounds in the magazine of the held gun plus matching ammunition in the inventory. */
    private static int rounds(ServerPlayer p) {
        if (!(p.getMainHandItem().getItem() instanceof GunItem gun)) {
            return 0;
        }
        return GunItem.ammo(p.getMainHandItem()) + de.gtacity.item.AmmoItem.count(p, gun.type.ammo);
    }

    private static int wanted(TestServerContext server) {
        return server.computeOnServer(s -> WantedSystem.level(player(s)));
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
        teleport(server, -40.5, 275, -40.5, -45.0F, 40.0F);
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
        // Cars that just left the simulated area are removed by the next clean-up round (every 2 s).
        List<Integer> stranded = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.CAR,
                car -> car.isStrandedTraffic(s.overworld())).stream().map(CarEntity::getId).toList());
        ctx.waitTicks(50);
        long left = server.computeOnServer(s -> stranded.stream()
                .filter(id -> s.overworld().getEntity(id) != null).count());
        expect(left == 0, "Eingefrorener Verkehr am Rand der Simulationsdistanz wird entfernt ("
                + stranded.size() + " gefunden, " + left + " übrig)");

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

    // ------------------------------------------------------------------ police

    private void arrest(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, 9.5, CityLayout.GROUND + 1.0, 9.5, 0.0F, 0.0F); // middle of the central crossing
        server.runOnServer(s -> {
            Economy.set(player(s), 2000);
            WantedSystem.setLevel(player(s), 1);
        });
        BlockPos station = CityPlaces.nearest(CityLayout.LotType.POLICE, 9, 9);
        boolean busted = false;
        for (int i = 0; i < 45 && !busted; i++) {
            ctx.waitTicks(20);
            busted = server.computeOnServer(s -> player(s).blockPosition().distSqr(station) < 16);
        }
        long money = server.computeOnServer(s -> Economy.get(player(s)));
        expect(busted, "1 Stern: Polizei nimmt fest, BUSTED, Abtransport zum Revier");
        expect(money < 2000, "Festnahme kostet Strafe ($2000 -> $" + money + ")");
        expect(wanted(server) == 0, "Nach der Festnahme keine Sterne mehr");
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        reset(server);
    }

    private void policeShooting(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        teleport(server, 9.5, CityLayout.GROUND + 1.0, 9.5, 0.0F, 0.0F);
        server.runOnServer(s -> WantedSystem.setLevel(player(s), 2));
        boolean shot = false, aiming = false;
        for (int i = 0; i < 40 && !shot; i++) {
            ctx.waitTicks(20);
            server.runOnServer(s -> WantedSystem.commit(player(s), 2)); // keep the stars even if they lose sight
            shot = server.computeOnServer(s -> player(s).getHealth() < player(s).getMaxHealth());
            if (!aiming) {
                aiming = ctx.computeOnClient(mc -> {
                    for (var e : mc.level.entitiesForRendering()) {
                        if (e instanceof PoliceEntity cop && cop.isAggressive()) {
                            return true;
                        }
                    }
                    return false;
                });
            }
        }
        expect(shot, "2 Sterne: Polizei schießt auf den Spieler");
        expect(aiming, "Polizisten gehen in den Zielanschlag (beim Client sichtbar)");
        ctx.takeScreenshot("gtacity-11-polizei-zielt");
        reset(server);
    }

    private void patrolCar(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode creative @a"); // the chase takes a while - officers must not shoot us dead
        teleport(server, 9.5, CityLayout.GROUND + 1.0, 9.5, 0.0F, 0.0F);
        server.runOnServer(s -> WantedSystem.setLevel(player(s), 2));
        int carId = server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            CitySpawns.Lane lane = null;
            for (int i = 0; i < 20 && lane == null; i++) {
                lane = CitySpawns.findLane(s.overworld(), p.blockPosition(), 30, 45, p.getRandom());
            }
            if (lane == null) {
                return -1;
            }
            PoliceDispatch.spawnPoliceCar(s.overworld(), lane, p, false);
            List<CarEntity> cars = s.overworld().getEntitiesOfClass(CarEntity.class,
                    AABB.ofSize(new Vec3(lane.x(), lane.y(), lane.z()), 4, 4, 4), CarEntity::isSirenOn);
            return cars.isEmpty() ? -1 : cars.getFirst().getId();
        });
        expect(carId >= 0, "Streifenwagen mit Sirene wird losgeschickt");
        if (carId < 0) {
            reset(server);
            return;
        }
        int crew = server.computeOnServer(s -> s.overworld().getEntity(carId).getPassengers().size());
        expect(crew == 2, "Zwei Beamte im Streifenwagen (" + crew + ")");
        Vec3 start = server.computeOnServer(s -> s.overworld().getEntity(carId).position());
        boolean unloaded = false;
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < 40 && !unloaded; i++) {
            ctx.waitTicks(20);
            server.runOnServer(s -> WantedSystem.commit(player(s), 2));
            double[] st = server.computeOnServer(s -> {
                var car = s.overworld().getEntity(carId);
                if (car == null) {
                    return new double[]{-1, 0};
                }
                return new double[]{car.getPassengers().size(), car.distanceTo(player(s))};
            });
            closest = Math.min(closest, st[1]);
            unloaded = st[0] == 0;
        }
        double moved = server.computeOnServer(s -> {
            var car = s.overworld().getEntity(carId);
            return car == null ? 0.0 : car.position().distanceTo(start);
        });
        expect(moved > 5.0, "Streifenwagen fährt los (" + String.format("%.1f", moved) + " Blöcke)");
        expect(unloaded, "Beamte steigen in der Nähe aus (nächster Abstand "
                + String.format("%.1f", closest) + ")");
        ctx.takeScreenshot("gtacity-12-streifenwagen");
        server.runOnServer(s -> {
            var car = s.overworld().getEntity(carId);
            if (car instanceof CarEntity c) {
                c.despawn();
            }
        });
        server.runCommand("gamemode survival @a");
        reset(server);
    }

    private void swat(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        teleport(server, 9.5, CityLayout.GROUND + 1.0, 9.5, 0.0F, 0.0F);
        server.runCommand("gamemode creative @a"); // survive the shooting
        server.runOnServer(s -> WantedSystem.setLevel(player(s), 4));
        boolean swat = false;
        int cops = 0;
        for (int i = 0; i < 30 && !swat; i++) {
            ctx.waitTicks(20);
            server.runOnServer(s -> WantedSystem.commit(player(s), 4));
            int[] st = server.computeOnServer(s -> {
                List<PoliceEntity> list = s.overworld().getEntitiesOfClass(PoliceEntity.class,
                        player(s).getBoundingBox().inflate(80));
                int n = 0;
                for (PoliceEntity c : list) {
                    if (c.isSwat() && c.getMainHandItem().is(ModItems.CARBINE) && c.getMaxHealth() >= 40) {
                        n++;
                    }
                }
                return new int[]{n, list.size()};
            });
            swat = st[0] > 0;
            cops = st[1];
        }
        expect(swat, "4 Sterne: SWAT mit Karabiner rückt an (" + cops + " Beamte insgesamt)");
        server.runCommand("gamemode survival @a");
        reset(server);
    }

    private void loseCops(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        // A closed stone box high above the street: no officer can see the player in there.
        int y = CityLayout.GROUND + 40;
        server.runCommand("fill 6 " + y + " 6 12 " + (y + 4) + " 12 minecraft:stone hollow");
        teleport(server, 9.5, y + 1.0, 9.5, 0.0F, 0.0F);
        server.runOnServer(s -> WantedSystem.setLevel(player(s), 1));
        boolean blinking = false, lost = false;
        for (int i = 0; i < 30 && !lost; i++) {
            ctx.waitTicks(20);
            Integer hidden = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.WANTED_HIDDEN));
            blinking |= hidden != null && hidden == 1;
            lost = wanted(server) == 0;
        }
        expect(blinking, "Außer Sicht der Polizei blinken die Sterne");
        expect(lost, "Cops abgehängt: Sterne verschwinden nach einer Weile");
        server.runCommand("fill 6 " + y + " 6 12 " + (y + 4) + " 12 minecraft:air");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        reset(server);
    }

    private void attackCop(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        // A patrolling officer ignores innocent citizens.
        int patrol = server.computeOnServer(s -> {
            PoliceEntity cop = PoliceDispatch.spawnCop(s.overworld(), spawn.south(3), false);
            cop.setPersistent(true);
            return cop.getId();
        });
        ctx.waitTicks(80);
        boolean calm = server.computeOnServer(s -> player(s).getHealth() == player(s).getMaxHealth()
                && ((PoliceEntity) s.overworld().getEntity(patrol)).getTarget() == null);
        expect(calm, "Streife lässt unbescholtene Bürger in Ruhe");
        server.runCommand("kill @e[type=gtacity:police]");
        server.runCommand("gamemode creative @a"); // stars count in creative too, and the backup cannot kill us

        int cop = server.computeOnServer(s -> {
            PoliceEntity c = PoliceDispatch.spawnCop(s.overworld(), spawn.south(5), false);
            c.setPersistent(true);
            c.setNoAi(true);
            return c.getId();
        });
        server.runCommand("item replace entity @a hotbar.0 with gtacity:pistol");
        server.runCommand("give @a gtacity:pistol_ammo 48");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
        conn.waitForClientboundEntityUpdates(ModEntities.POLICE);
        aim(ctx, new Vec3(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 5.5));
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(10);
        int afterHit = wanted(server);
        expect(afterHit >= 2, "Auf einen Polizisten schießen: mindestens 2 Sterne (" + afterHit + ")");
        for (int i = 0; i < 8 && health(server, cop) > 0; i++) {
            ctx.waitTicks(8);
            ctx.getInput().pressKey(o -> o.keyAttack);
        }
        ctx.waitTicks(10);
        int afterKill = wanted(server);
        expect(health(server, cop) == 0, "Polizist geht zu Boden");
        expect(afterKill >= 3, "Polizist getötet: mindestens 3 Sterne (" + afterKill + ")");
        server.runCommand("gamemode survival @a");
        reset(server);
    }

    // ------------------------------------------------------------------ cars

    private void carJacking(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        server.runCommand("item replace entity @a hotbar.1 with minecraft:air");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[1]);
        double x = 5.5, z = CityLayout.CORRIDOR + 20.5;
        teleport(server, x + 3.0, CityLayout.GROUND + 1.0, z, 90.0F, 10.0F);
        int[] ids = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.COMMAND);
            car.setVariant(CarVariant.SEDAN_RED);
            car.snapTo(x, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
            level.addFreshEntity(car);
            NpcEntity driver = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.COMMAND);
            driver.snapTo(x, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
            driver.randomizeLook(false);
            level.addFreshEntity(driver);
            driver.startRiding(car);
            return new int[]{car.getId(), driver.getId()};
        });
        conn.waitForClientboundEntityUpdates(ModEntities.CAR);
        ctx.waitTicks(10);
        aim(ctx, new Vec3(x, CityLayout.GROUND + 1.8, z));
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitFor(mc -> mc.player.getVehicle() instanceof CarEntity, 60);
        boolean inCar = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof CarEntity);
        boolean driverOut = server.computeOnServer(s -> {
            var d = s.overworld().getEntity(ids[1]);
            return d == null || d.getVehicle() == null;
        });
        expect(inCar, "Rechtsklick auf ein Auto mit Fahrer: Spieler sitzt am Steuer");
        expect(driverOut, "Der Fahrer wird aus dem Auto gezogen");
        expect(wanted(server) >= 1, "Autoklau bringt einen Stern");
        ctx.getInput().holdKeyFor(o -> o.keyShift, 5);
        ctx.waitFor(mc -> mc.player.getVehicle() == null, 60);
        reset(server);

        // Stealing a police car is worse.
        int police = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.COMMAND);
            car.setVariant(CarVariant.POLICE);
            car.snapTo(x, CityLayout.GROUND + 1.0, z + 12, 0.0F, 0.0F);
            level.addFreshEntity(car);
            car.interact(player(s), InteractionHand.MAIN_HAND, car.position());
            return car.getId();
        });
        ctx.waitTicks(5);
        expect(wanted(server) >= 2, "Polizeiauto klauen bringt zwei Sterne (" + wanted(server) + ")");
        server.runOnServer(s -> ((CarEntity) s.overworld().getEntity(police)).despawn());
        server.runOnServer(s -> ((CarEntity) s.overworld().getEntity(ids[0])).despawn());
        reset(server);
    }

    private void carDamage(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode creative @a");
        BlockPos spawn = CityPlaces.spawn();
        double x = 5.5, z = CityLayout.CORRIDOR + 12.5;
        teleport(server, x + 8.0, CityLayout.GROUND + 1.0, z, 90.0F, 0.0F);
        int car = server.computeOnServer(s -> {
            CarEntity c = ModEntities.CAR.create(s.overworld(), EntitySpawnReason.COMMAND);
            c.setVariant(CarVariant.SUV_BLACK);
            c.snapTo(x, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
            s.overworld().addFreshEntity(c);
            return c.getId();
        });
        server.runCommand("item replace entity @a hotbar.4 with gtacity:carbine");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[4]);
        conn.waitForClientboundEntityUpdates(ModEntities.CAR);
        ctx.waitTicks(10);
        aim(ctx, new Vec3(x, CityLayout.GROUND + 1.8, z));
        float before = server.computeOnServer(s -> ((CarEntity) s.overworld().getEntity(car)).getHealth());
        ctx.getInput().holdKeyFor(o -> o.keyAttack, 20);
        float mid = server.computeOnServer(s -> s.overworld().getEntity(car) instanceof CarEntity c ? c.getHealth() : 0F);
        expect(mid < before, "Schüsse beschädigen das Auto (" + before + " -> " + mid + ")");
        ctx.getInput().holdKeyFor(o -> o.keyAttack, 80);
        ctx.waitTicks(20);
        boolean gone = server.computeOnServer(s -> s.overworld().getEntity(car) == null);
        expect(gone, "Zerschossenes Auto explodiert");

        // A badly damaged car catches fire and blows up by itself.
        int burning = server.computeOnServer(s -> {
            CarEntity c = ModEntities.CAR.create(s.overworld(), EntitySpawnReason.COMMAND);
            c.setVariant(CarVariant.TAXI);
            c.snapTo(x, CityLayout.GROUND + 1.0, z + 10, 0.0F, 0.0F);
            s.overworld().addFreshEntity(c);
            c.damageCar(s.overworld(), null, c.getHealth() * 0.8F);
            return c.getId();
        });
        ctx.waitTicks(40);
        ctx.takeScreenshot("gtacity-13-auto-brennt");
        boolean stillThere = server.computeOnServer(s -> s.overworld().getEntity(burning) != null);
        ctx.waitTicks(120);
        boolean exploded = server.computeOnServer(s -> s.overworld().getEntity(burning) == null);
        expect(stillThere && exploded, "Schwer beschädigtes Auto brennt und explodiert nach ein paar Sekunden");
        server.runCommand("gamemode survival @a");
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        reset(server);
    }

    private void carHandling(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        double x = 5.5, z = CityLayout.CORRIDOR + 8.5;
        teleport(server, x + 2.0, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
        int car = server.computeOnServer(s -> {
            CarEntity c = ModEntities.CAR.create(s.overworld(), EntitySpawnReason.COMMAND);
            c.setVariant(CarVariant.SPORTS_YELLOW);
            c.snapTo(x, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
            s.overworld().addFreshEntity(c);
            c.interact(player(s), InteractionHand.MAIN_HAND, c.position());
            return c.getId();
        });
        int victim = dummy(server, x, CityLayout.GROUND + 1.0, z + 16, false);
        ctx.waitFor(mc -> mc.player.getVehicle() instanceof CarEntity, 60);
        ctx.getInput().pressKey(ClientInput.HORN);
        ctx.waitTicks(5);
        ok("Hupe (H) im Auto");
        ctx.getInput().holdKeyFor(o -> o.keyUp, 45);
        ctx.waitTicks(10);
        float victimHealth = health(server, victim);
        expect(victimHealth < 20.0F, "Überfahrener Passant wird verletzt (" + victimHealth + ")");
        // Steering: W + D turns the car.
        float yaw0 = server.computeOnServer(s -> s.overworld().getEntity(car).getYRot());
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.getInput().holdKeyFor(o -> o.keyRight, 25);
        ctx.getInput().releaseKey(o -> o.keyUp);
        ctx.waitTicks(30);
        float yaw1 = server.computeOnServer(s -> s.overworld().getEntity(car).getYRot());
        float turned = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw1 - yaw0));
        expect(turned > 15.0F, "Lenken mit A/D (" + String.format("%.0f", turned) + " Grad gedreht)");
        // Reverse: S drives backwards.
        Vec3 p0 = server.computeOnServer(s -> s.overworld().getEntity(car).position());
        Vec3 fwd = server.computeOnServer(s -> Vec3.directionFromRotation(0.0F, s.overworld().getEntity(car).getYRot()));
        ctx.getInput().holdKeyFor(o -> o.keyDown, 30);
        Vec3 p1 = server.computeOnServer(s -> s.overworld().getEntity(car).position());
        double along = p1.subtract(p0).dot(fwd);
        expect(along < -0.5, "Rückwärtsfahren mit S (" + String.format("%.1f", along) + " Blöcke)");
        ctx.getInput().holdKeyFor(o -> o.keyShift, 5);
        ctx.waitFor(mc -> mc.player.getVehicle() == null, 60);
        server.runOnServer(s -> ((CarEntity) s.overworld().getEntity(car)).despawn());
        reset(server);
    }

    private void carDealer(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        BlockPos counter = spawn.south(2).above();
        server.runOnServer(s -> {
            s.overworld().setBlockAndUpdate(counter, ModBlocks.CAR_COUNTER.defaultBlockState());
            Economy.set(player(s), 20000);
        });
        server.runCommand("item replace entity @a hotbar.1 with minecraft:air");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[1]);
        ctx.waitTicks(5);
        ctx.getInput().lookAt(counter);
        ctx.waitTicks(5);
        int carsBefore = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class,
                player(s).getBoundingBox().inflate(8)).size());
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitForScreen(ShopScreen.class);
        ctx.clickScreenButton("Kaufen");
        ctx.waitTicks(10);
        ctx.clickScreenButton("Schließen");
        ctx.waitFor(mc -> mc.gui.screen() == null, 40);
        int carsAfter = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class,
                player(s).getBoundingBox().inflate(8)).size());
        long money = server.computeOnServer(s -> Economy.get(player(s)));
        expect(carsAfter > carsBefore, "Autohaus: gekauftes Auto steht bereit");
        expect(money == 8000, "Autohaus: Limousine kostet $12000 ($20000 -> $" + money + ")");
        server.runOnServer(s -> s.overworld().removeBlock(counter, false));
        server.runOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class, player(s).getBoundingBox().inflate(8))
                .forEach(CarEntity::despawn));
        reset(server);
    }

    // ------------------------------------------------------------------ weapons

    private void allGuns(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        double px = spawn.getX() + 0.5, pz = spawn.getZ() + 0.5;
        String[][] guns = {
                {"pistol", "pistol_ammo", "Pistole"}, {"smg", "smg_ammo", "Micro-SMG"},
                {"carbine", "rifle_ammo", "Karabiner"}, {"shotgun", "shotgun_shells", "Pumpgun"},
                {"sniper", "sniper_ammo", "Scharfschützengewehr"}, {"minigun", "rifle_ammo", "Minigun"},
                {"rpg", "rocket", "Raketenwerfer"}};
        for (String[] gun : guns) {
            reset(server);
            teleport(server, px, spawn.getY(), pz, 0.0F, 0.0F);
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.0 with gtacity:" + gun[0]);
            server.runCommand("give @a gtacity:" + gun[1] + " 16");
            ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
            double dist = gun[0].equals("rpg") ? 10.5 : 5.5;
            int target = dummy(server, px, spawn.getY(), pz + dist, false);
            conn.waitForClientboundEntityUpdates(ModEntities.PEDESTRIAN);
            ctx.waitTicks(45); // longest fire delay
            boolean head = gun[0].equals("sniper");
            aim(ctx, new Vec3(px, spawn.getY() + (head ? 1.62 : 1.0), pz + dist));
            int rounds0 = server.computeOnServer(s -> rounds(player(s)));
            boolean automatic = gun[0].equals("smg") || gun[0].equals("carbine") || gun[0].equals("minigun");
            if (automatic) {
                ctx.getInput().holdKeyFor(o -> o.keyAttack, 12);
            } else {
                ctx.getInput().pressKey(o -> o.keyAttack);
            }
            ctx.waitTicks(gun[0].equals("rpg") ? 30 : 8);
            int rounds1 = server.computeOnServer(s -> rounds(player(s)));
            float hp = health(server, target);
            String detail = gun[2] + ": " + (rounds0 - rounds1) + " Schuss, Ziel " + hp + " HP";
            boolean fired = rounds1 < rounds0;
            if (head || gun[0].equals("rpg")) {
                expect(fired && hp == 0, detail + (head ? " (Kopfschuss tödlich)" : " (Explosion tödlich)"));
            } else if (automatic) {
                expect(rounds0 - rounds1 >= 3 && hp < 20, detail + " (Dauerfeuer)");
            } else {
                expect(fired && hp < 20, detail);
            }
        }
        // Empty gun without ammunition does nothing.
        reset(server);
        server.runCommand("clear @a");
        server.runCommand("item replace entity @a hotbar.0 with gtacity:pistol");
        server.runOnServer(s -> player(s).getMainHandItem().set(de.gtacity.registry.ModComponents.AMMO, 0));
        int target = dummy(server, px, spawn.getY(), pz + 5.5, false);
        ctx.waitTicks(20);
        aim(ctx, new Vec3(px, spawn.getY() + 1.0, pz + 5.5));
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(10);
        expect(health(server, target) == 20.0F, "Leeres Magazin ohne Munition: kein Schuss");
        server.runCommand("give @a gtacity:pistol_ammo 48");
        server.runCommand("give @a gtacity:pistol 1");
        reset(server);
    }

    private void melee(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        double px = spawn.getX() + 0.5, pz = spawn.getZ() + 0.5;
        for (String weapon : new String[]{"knife", "baseball_bat"}) {
            reset(server);
            teleport(server, px, spawn.getY(), pz, 0.0F, 0.0F);
            server.runCommand("item replace entity @a hotbar.0 with gtacity:" + weapon);
            ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
            int target = dummy(server, px, spawn.getY(), pz + 2.0, false);
            conn.waitForClientboundEntityUpdates(ModEntities.PEDESTRIAN);
            ctx.waitTicks(25);
            aim(ctx, new Vec3(px, spawn.getY() + 1.2, pz + 2.0));
            ctx.getInput().pressKey(o -> o.keyAttack);
            ctx.waitTicks(10);
            float hp = health(server, target);
            expect(hp < 20.0F, (weapon.equals("knife") ? "Messer" : "Baseballschläger") + " trifft (" + hp + " HP)");
        }
        // Gang members fight back.
        reset(server);
        teleport(server, px, spawn.getY(), pz, 0.0F, 0.0F);
        server.runCommand("item replace entity @a hotbar.0 with gtacity:pistol");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
        int gang = server.computeOnServer(s -> {
            NpcEntity npc = ModEntities.PEDESTRIAN.create(s.overworld(), EntitySpawnReason.COMMAND);
            npc.snapTo(px, spawn.getY(), pz + 5.5, 180.0F, 0.0F);
            npc.randomizeLook(true);
            npc.setPersistent(true);
            s.overworld().addFreshEntity(npc);
            return npc.getId();
        });
        conn.waitForClientboundEntityUpdates(ModEntities.PEDESTRIAN);
        ctx.waitTicks(20);
        aim(ctx, new Vec3(px, spawn.getY() + 1.0, pz + 5.5));
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(30);
        boolean fights = server.computeOnServer(s -> s.overworld().getEntity(gang) instanceof NpcEntity n
                && (n.getTarget() == player(s) || !n.isAlive()));
        expect(fights, "Gang-Mitglied wehrt sich, wenn man auf es schießt");
        reset(server);
    }

    private void environment(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode creative @a");
        BlockPos spawn = CityPlaces.spawn();
        double px = spawn.getX() + 0.5, pz = spawn.getZ() + 0.5;
        teleport(server, px, spawn.getY(), pz, 0.0F, 0.0F);
        server.runCommand("item replace entity @a hotbar.0 with gtacity:pistol");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
        BlockPos glass = spawn.south(4).above();
        BlockPos pump = spawn.south(9).above();
        server.runOnServer(s -> {
            s.overworld().setBlockAndUpdate(glass, net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
            s.overworld().setBlockAndUpdate(pump, ModBlocks.GAS_PUMP.defaultBlockState());
        });
        ctx.waitTicks(20);
        aim(ctx, Vec3.atCenterOf(glass));
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(10);
        expect(server.computeOnServer(s -> s.overworld().getBlockState(glass).isAir()), "Glas zerspringt bei Treffern");
        ctx.waitTicks(10);
        aim(ctx, Vec3.atCenterOf(pump));
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(10);
        expect(server.computeOnServer(s -> !s.overworld().getBlockState(pump).is(ModBlocks.GAS_PUMP)),
                "Beschossene Zapfsäule explodiert");
        // Grenade next to a pedestrian.
        server.runCommand("gamemode survival @a");
        int target = dummy(server, px, spawn.getY(), pz + 6.5, false);
        server.runCommand("item replace entity @a hotbar.3 with gtacity:grenade 4");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[3]);
        ctx.waitTicks(10);
        aim(ctx, new Vec3(px, spawn.getY() + 2.5, pz + 6.5));
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(90);
        float hp = health(server, target);
        expect(hp < 20.0F, "Granate verletzt Passanten in der Nähe (" + hp + " HP)");
        server.runOnServer(s -> {
            s.overworld().removeBlock(glass, false);
            s.overworld().removeBlock(pump, false);
        });
        reset(server);
    }

    private void holding(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        BlockPos spawn = CityPlaces.spawn();
        server.runCommand("time set 6000");
        server.runCommand("gamemode creative @a");
        server.runCommand("kill @e[type=gtacity:pedestrian]");
        server.runCommand("kill @e[type=gtacity:police]");
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        server.runCommand("item replace entity @a hotbar.0 with gtacity:pistol");
        server.runCommand("item replace entity @a hotbar.1 with gtacity:carbine");
        server.runCommand("item replace entity @a hotbar.2 with gtacity:rpg");
        settle(ctx, conn);
        String[] names = {"pistole", "karabiner", "rpg"};
        for (int i = 0; i < names.length; i++) {
            final int slot = i;
            ctx.getInput().pressKey(o -> o.keyHotbarSlots[slot]);
            ctx.waitTicks(15);
            ctx.takeScreenshot("gtacity-10-haltung-" + names[i]);
        }
        // Two officers seen from their right side: one relaxed, one aiming.
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            for (int i = 0; i < 2; i++) {
                PoliceEntity cop = ModEntities.POLICE.create(level, EntitySpawnReason.COMMAND);
                cop.snapTo(spawn.getX() - 0.5 + i * 2.5, spawn.getY(), spawn.getZ() + 4.5, 90.0F, 0.0F);
                cop.setYHeadRot(90.0F);
                cop.setYBodyRot(90.0F);
                cop.setSwat(false);
                cop.setLeftHanded(false);
                cop.setPersistent(true);
                cop.setNoAi(true);
                cop.setAggressive(i == 1);
                level.addFreshEntity(cop);
            }
        });
        conn.waitForClientboundEntityUpdates(ModEntities.POLICE);
        ctx.waitTicks(20);
        ctx.takeScreenshot("gtacity-10-haltung-polizei");
        // The player from the front, holding the pistol.
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.waitTicks(15);
        ctx.takeScreenshot("gtacity-10-haltung-spieler");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("kill @e[type=gtacity:police]");
        server.runCommand("gamemode survival @a");
        ok("Waffenhaltung aufgenommen");
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
