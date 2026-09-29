package de.gtacity.spieltest;

import de.gtacity.client.ClientInput;
import de.gtacity.client.map.Waypoint;
import de.gtacity.client.screen.CityMapScreen;
import de.gtacity.client.screen.ShopScreen;
import de.gtacity.client.screen.JobBoardScreen;
import de.gtacity.gameplay.Clerks;
import de.gtacity.gameplay.Crew;
import de.gtacity.gameplay.Jobs;
import de.gtacity.network.Payloads;
import de.gtacity.world.CityMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import de.gtacity.registry.ModComponents;
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
        Jobs.autoStory = false; // the checks start their chapters themselves; "Story" switches it on briefly
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
            check("Story", () -> storyStart(ctx, server));
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
            check("Garage", () -> garage(ctx, server, conn));
            check("Supersportwagen", () -> superCar(ctx, server, conn));
            check("Tueren", () -> doors(ctx, server, conn));
            check("Karte", () -> map(ctx, server, conn));
            check("Navi", () -> navi(ctx, server, conn));
            check("Laeden", () -> shops(ctx, server, conn));
            check("Alle Verkaeufer", () -> allClerks(ctx, server, conn));
            check("Jobs", () -> jobs(ctx, server, conn));
            check("Crew", () -> crew(ctx, server, conn));
            check("Gangauto", () -> gangCar(ctx, server, conn));
            check("Villa", () -> villa(ctx, server, conn));
            check("Taschendiebstahl", () -> pickpocket(ctx, server, conn));
            check("Helikopter", () -> helicopter(ctx, server, conn));
            check("Waffenarsenal", () -> allGuns(ctx, server, conn));
            check("Nahkampf", () -> melee(ctx, server, conn));
            check("Umgebung", () -> environment(ctx, server, conn));
            check("Tod", () -> death(ctx, server, conn));
            check("Haltung", () -> holding(ctx, server, conn));
            check("Nacht", () -> night(ctx, server, conn));
            check("Eigenes Auto", () -> ownCar(ctx, server, conn));
            check("Fotos", () -> photos(ctx, server, conn));
            check("Werbung", () -> billboards(ctx, server, conn));
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
        // Pedestrians go, the shop and job clerks stay.
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.PEDESTRIAN, n -> n.role().isEmpty())
                .forEach(net.minecraft.world.entity.Entity::discard));
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
        // Cars that left the simulated area are removed after five seconds out there.
        List<Integer> stranded = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.CAR,
                car -> car.isOutsideSimulation(s.overworld())).stream().map(CarEntity::getId).toList());
        ctx.waitTicks(160);
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
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof CarEntity),
                "Kurz Shift (Handbremse) wirft nicht aus dem Auto");
        ctx.getInput().pressKey(o -> o.keySwapOffhand);
        ctx.waitFor(mc -> mc.player.getVehicle() == null, 60);
        expect(ctx.computeOnClient(mc -> mc.player.getVehicle() == null), "Mit F ausgestiegen");
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> mc.options.getCameraType() == CameraType.FIRST_PERSON),
                "Kamera wieder in der ersten Person");
    }

    private void shooting(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.PEDESTRIAN, n -> n.role().isEmpty())
                .forEach(net.minecraft.world.entity.Entity::discard));
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
        expect(wanted == 0, "Ein Schuss auf einen Passanten bringt noch keine Sterne (" + wanted + ")");
        // Three dead pedestrians are worth one star.
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            for (int i = 0; i < 3; i++) {
                NpcEntity victim = ModEntities.PEDESTRIAN.create(s.overworld(), EntitySpawnReason.COMMAND);
                victim.snapTo(p.getX() + 3, p.getY(), p.getZ() - 3 - i, 0.0F, 0.0F);
                s.overworld().addFreshEntity(victim);
                victim.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(p), 1000.0F);
            }
        });
        ctx.waitTicks(5);
        wanted = server.computeOnServer(s -> WantedSystem.level(player(s)));
        expect(wanted >= 1, "Drei getötete Passanten: ein Stern (" + wanted + ")");
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
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.PEDESTRIAN, n -> n.role().isEmpty())
                .forEach(net.minecraft.world.entity.Entity::discard));
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
        expect(robWanted >= 1, "Überfall bringt Fahndungssterne (" + robWanted + ")");
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
        for (int i = 0; i < 70 && !busted; i++) {
            ctx.waitTicks(20);
            busted = server.computeOnServer(s -> player(s).blockPosition().distSqr(station) < 16);
            if (i % 5 == 0) {
                System.out.println("GTACITY-TEST Diagnose Festnahme t=" + i + "s " + server.computeOnServer(s -> {
                    ServerPlayer p = player(s);
                    StringBuilder b = new StringBuilder("sterne=" + WantedSystem.level(p));
                    for (PoliceEntity c : s.overworld().getEntitiesOfClass(PoliceEntity.class,
                            p.getBoundingBox().inflate(150))) {
                        b.append(String.format(" cop[%.0f %s ziel=%s]", c.distanceTo(p),
                                c.getVehicle() != null ? "faehrt" : "zuFuss", c.getTarget() == p));
                    }
                    return b.toString();
                }));
            }
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
        for (int i = 0; i < 50 && !lost; i++) {
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
        expect(afterHit >= 1, "Auf einen Polizisten schießen: mindestens 1 Stern (" + afterHit + ")");
        for (int i = 0; i < 8 && health(server, cop) > 0; i++) {
            ctx.waitTicks(8);
            ctx.getInput().pressKey(o -> o.keyAttack);
        }
        ctx.waitTicks(10);
        int afterKill = wanted(server);
        expect(health(server, cop) == 0, "Polizist geht zu Boden");
        expect(afterKill >= 2, "Polizist getötet: mindestens 2 Sterne (" + afterKill + ")");
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
        server.runCommand("kill @e[type=gtacity:police]"); // no witnesses from the earlier checks
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
        expect(wanted(server) == 0, "Autoklau ohne Polizei in Sichtweite: keine Sterne (" + wanted(server) + ")");
        ctx.getInput().pressKey(o -> o.keySwapOffhand);
        ctx.waitFor(mc -> mc.player.getVehicle() == null, 60);
        ctx.waitTicks(10);
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
        // Short bursts, aiming again in between: the recoil pulls the gun up.
        for (int i = 0; i < 10 && server.computeOnServer(s -> s.overworld().getEntity(car) != null); i++) {
            aim(ctx, new Vec3(x, CityLayout.GROUND + 1.5, z));
            ctx.getInput().holdKeyFor(o -> o.keyAttack, 10);
            ctx.waitTicks(5);
        }
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

        // Drift: full speed, then Shift + D - the car slides sideways through the corner.
        // A fresh car: the old one took a beating above and may already be on fire.
        ctx.getInput().pressKey(o -> o.keySwapOffhand);
        ctx.waitFor(mc -> mc.player.getVehicle() == null, 60);
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            if (s.overworld().getEntity(car) instanceof CarEntity old) {
                old.despawn();
            }
        });
        server.runCommand("gamemode creative @a");
        teleport(server, x + 2.0, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
        int driftCar = server.computeOnServer(s -> {
            CarEntity c = ModEntities.CAR.create(s.overworld(), EntitySpawnReason.COMMAND);
            c.setVariant(CarVariant.SPORTS_RED);
            c.snapTo(x, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
            s.overworld().addFreshEntity(c);
            c.interact(player(s), InteractionHand.MAIN_HAND, c.position());
            return c.getId();
        });
        ctx.waitFor(mc -> mc.player.getVehicle() instanceof CarEntity, 60);
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(30);
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.getInput().holdKey(o -> o.keyRight);
        boolean drifting = false, clientSkid = false;
        for (int i = 0; i < 12; i++) {
            ctx.waitTicks(2);
            drifting |= server.computeOnServer(s -> s.overworld().getEntity(driftCar) instanceof CarEntity c
                    && c.isDrifting());
            System.out.println("GTACITY-TEST Diagnose Drift " + server.computeOnServer(s ->
                    s.overworld().getEntity(driftCar) instanceof CarEntity c ? "shift=" + player(s).isShiftKeyDown()
                            + " gemessen=" + c.measuredSpeed + " fahrer=" + (c.getControllingPassenger() != null)
                            : "kein Auto") + " client: shift=" + ctx.computeOnClient(mc -> mc.player.isShiftKeyDown())
                    + " speed=" + ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof CarEntity c ? c.speed : -1));
            clientSkid |= ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof CarEntity c && c.isDrifting());
            if (i == 6) {
                ctx.takeScreenshot("gtacity-14b-drift");
            }
        }
        ctx.getInput().releaseKey(o -> o.keyRight);
        ctx.getInput().releaseKey(o -> o.keyShift);
        ctx.getInput().releaseKey(o -> o.keyUp);
        ctx.waitTicks(20);
        expect(drifting && clientSkid, "Shift beim Fahren: Auto driftet (Reifenqualm und Quietschen)");
        expect(ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof CarEntity), "Nach dem Drift noch im Auto");
        ctx.getInput().pressKey(o -> o.keySwapOffhand);
        ctx.waitFor(mc -> mc.player.getVehicle() == null, 60);
        server.runOnServer(s -> {
            if (s.overworld().getEntity(driftCar) instanceof CarEntity c) {
                c.despawn();
            }
        });
        server.runCommand("gamemode survival @a");
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

    private void garage(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        ctx.waitTicks(10);
        List<Integer> owned = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.GARAGE));
        expect(owned != null && !owned.isEmpty(), "Gekauftes Auto steht in der Garage ("
                + (owned == null ? 0 : owned.size()) + ")");
        int before = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class,
                player(s).getBoundingBox().inflate(45), c -> c.isOwnedBy(player(s))).size());
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.CALL_CAR, 0)));
        ctx.waitTicks(10);
        int after = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class,
                player(s).getBoundingBox().inflate(45), c -> c.isOwnedBy(player(s))).size());
        expect(after >= 1 && after >= before, "Garage: eigenes Auto wird an die Straße geliefert");
        ctx.takeScreenshot("gtacity-15-garage");
        server.runOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class,
                player(s).getBoundingBox().inflate(45), c -> c.isOwnedBy(player(s))).forEach(CarEntity::despawn));
        reset(server);
    }

    /** Car doors: open for a moment when someone gets in or out, sneak + right click holds them open. */
    private void doors(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode creative @a");
        double x = 5.5, z = CityLayout.CORRIDOR + 8.5;
        server.runOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class,
                new AABB(x - 30, CityLayout.GROUND - 5, z - 30, x + 30, CityLayout.GROUND + 10, z + 30))
                .forEach(CarEntity::despawn));
        teleport(server, x + 7.0, CityLayout.GROUND + 1.0, z + 4.0, 90.0F, 15.0F);
        int[] ids = server.computeOnServer(s -> {
            CarEntity sedan = ModEntities.CAR.create(s.overworld(), EntitySpawnReason.COMMAND);
            sedan.setVariant(CarVariant.TAXI);
            sedan.snapTo(x, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
            s.overworld().addFreshEntity(sedan);
            CarEntity supercar = ModEntities.CAR.create(s.overworld(), EntitySpawnReason.COMMAND);
            supercar.setVariant(CarVariant.SUPER_LIME);
            supercar.snapTo(x, CityLayout.GROUND + 1.0, z + 8.0, 0.0F, 0.0F);
            s.overworld().addFreshEntity(supercar);
            return new int[]{sedan.getId(), supercar.getId()};
        });
        ctx.waitTicks(10);
        server.runOnServer(s -> {
            for (int id : ids) {
                if (s.overworld().getEntity(id) instanceof CarEntity c) {
                    c.toggleDoors();
                }
            }
        });
        ctx.waitTicks(10);
        float open = ctx.computeOnClient(mc -> mc.level.getEntity(ids[0]) instanceof CarEntity c
                ? c.doorOpen(true, 0.0F) + c.doorOpen(false, 0.0F) : 0.0F);
        expect(open >= 2.0F, "Autotüren: Schleichen + Rechtsklick öffnet beide Türen");
        ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
        ctx.takeScreenshot("gtacity-16c-tueren-offen");
        // Getting in (doors still held open): the driver's door swings, then all doors are closed again.
        server.runOnServer(s -> {
            if (s.overworld().getEntity(ids[1]) instanceof CarEntity c) {
                c.toggleDoors();
            }
            if (s.overworld().getEntity(ids[0]) instanceof CarEntity c) {
                c.interact(player(s), InteractionHand.MAIN_HAND, c.position());
            }
        });
        ctx.waitTicks(5);
        float driver = ctx.computeOnClient(mc -> mc.level.getEntity(ids[0]) instanceof CarEntity c
                ? c.doorOpen(true, 0.0F) : 0.0F);
        ctx.waitTicks(40);
        float later = ctx.computeOnClient(mc -> mc.level.getEntity(ids[0]) instanceof CarEntity c
                ? c.doorOpen(true, 0.0F) + c.doorOpen(false, 0.0F) : 1.0F);
        expect(driver > 0.5F && later == 0.0F, "Autotür: beim Einsteigen auf und wieder zu (" + driver + " / "
                + later + ")");
        server.runOnServer(s -> {
            player(s).stopRiding();
            for (int id : ids) {
                if (s.overworld().getEntity(id) instanceof CarEntity c) {
                    c.despawn();
                }
            }
        });
    }

    private void superCar(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode creative @a");
        double x = 5.5, z = CityLayout.CORRIDOR + 8.5;
        server.runOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class,
                new AABB(x - 20, CityLayout.GROUND - 5, z - 10, x + 20, CityLayout.GROUND + 10, z + 900))
                .forEach(CarEntity::despawn));
        teleport(server, x + 2.0, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
        int car = server.computeOnServer(s -> {
            CarEntity c = ModEntities.CAR.create(s.overworld(), EntitySpawnReason.COMMAND);
            c.setVariant(CarVariant.SUPER_RED);
            c.snapTo(x, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
            s.overworld().addFreshEntity(c);
            c.interact(player(s), InteractionHand.MAIN_HAND, c.position());
            return c.getId();
        });
        ctx.waitFor(mc -> mc.player.getVehicle() instanceof CarEntity, 60);
        // Clear the track: traffic and patrol cars left over from the police checks.
        server.runOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class,
                new AABB(x - 30, CityLayout.GROUND - 5, z - 30, x + 30, CityLayout.GROUND + 10, z + 900),
                c -> c.getId() != car).forEach(CarEntity::despawn));
        server.runCommand("kill @e[type=gtacity:police]");
        ctx.waitTicks(10);
        ctx.takeScreenshot("gtacity-16-supersportwagen");
        ctx.getInput().holdKey(o -> o.keyUp);
        float top = 0.0F;
        for (int i = 0; i < 16; i++) {
            ctx.waitTicks(10);
            top = Math.max(top, ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof CarEntity c
                    ? Math.abs(c.speed) : 0.0F));
            System.out.println("GTACITY-TEST Diagnose Super " + ctx.computeOnClient(mc ->
                    mc.player.getVehicle() instanceof CarEntity c ? String.format("speed=%.2f pos=%.0f,%.0f zustand=%.0f%%",
                            c.speed, c.getX(), c.getZ(), c.healthFraction() * 100) : "nicht im Auto"));
            if (i == 13) {
                ctx.takeScreenshot("gtacity-16b-vollgas");
            }
        }
        ctx.getInput().releaseKey(o -> o.keyUp);
        int kmh = Math.round(top * 72.0F);
        expect(kmh >= 280, "Supersportwagen schafft fast 300 km/h (" + kmh + " km/h)");
        ctx.getInput().holdKeyFor(o -> o.keyDown, 60);
        ctx.getInput().pressKey(o -> o.keySwapOffhand);
        ctx.waitFor(mc -> mc.player.getVehicle() == null, 60);
        server.runOnServer(s -> {
            if (s.overworld().getEntity(car) instanceof CarEntity c) {
                c.despawn();
            }
        });
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        settle(ctx, conn);
        reset(server);
    }

    private void map(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        ctx.runOnClient(mc -> Waypoint.clear());
        expect(CityMap.places().size() > 50, "Karte kennt die Orte der Stadt (" + CityMap.places().size() + ")");
        ctx.getInput().pressKey(ClientInput.MAP);
        ctx.waitForScreen(CityMapScreen.class);
        ok("M öffnet die große Karte");
        ctx.waitTicks(60); // the map texture is painted in the background
        ctx.takeScreenshot("gtacity-17-karte");
        // Click somewhere east of the player: sets the waypoint.
        ctx.runOnClient(mc -> {
            var screen = mc.gui.screen();
            double cx = (124 + screen.width) / 2.0 + 60, cy = (26 + screen.height) / 2.0;
            var button = new net.minecraft.client.input.MouseButtonEvent(cx, cy,
                    new net.minecraft.client.input.MouseButtonInfo(1, 0));
            screen.mouseClicked(button, false);
            screen.mouseReleased(button);
        });
        ctx.waitTicks(5);
        double[] waypoint = ctx.computeOnClient(mc -> Waypoint.get());
        expect(waypoint != null, "Klick auf die Karte setzt ein Ziel");
        ctx.takeScreenshot("gtacity-17b-karte-ziel");
        ctx.getInput().pressKey(ClientInput.MAP);
        ctx.waitFor(mc -> mc.gui.screen() == null, 40);
        ctx.waitTicks(10);
        ctx.takeScreenshot("gtacity-17c-minimap-route");
        if (waypoint != null) {
            List<double[]> route = CityMap.route(spawn.getX(), spawn.getZ(), waypoint[0], waypoint[1]);
            boolean onStreets = true;
            for (int i = 1; i + 1 < route.size() - 1; i++) {
                double[] a = route.get(i), b = route.get(i + 1);
                double mx = (a[0] + b[0]) / 2, mz = (a[1] + b[1]) / 2;
                onStreets &= CityLayout.isCorridor((int) Math.floor(mx), (int) Math.floor(mz));
            }
            expect(onStreets, "Navi-Route führt über die Straßen (" + route.size() + " Punkte)");
        }
        ctx.runOnClient(mc -> Waypoint.clear());
    }

    private void navi(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        // Routes only over streets, corners at crossings, and no jumping while moving along a street.
        double[][] goals = {{600, 400}, {-800, 900}, {50, -700}, {1200, -300}};
        boolean allOnStreets = true, noBackwards = true;
        for (double[] goal : goals) {
            for (double sx = 12; sx < 200; sx += 7) {
                List<double[]> route = CityMap.route(sx, 30, goal[0], goal[1]);
                for (int i = 1; i + 1 < route.size() - 1; i++) {
                    double[] a = route.get(i), b = route.get(i + 1);
                    boolean straight = Math.abs(a[0] - b[0]) < 0.6 || Math.abs(a[1] - b[1]) < 0.6;
                    double mx = (a[0] + b[0]) / 2, mz = (a[1] + b[1]) / 2;
                    allOnStreets &= straight && CityLayout.isCorridor((int) Math.floor(mx), (int) Math.floor(mz));
                }
                // The route must not be much longer than the direct way over the streets.
                double length = 0;
                for (int i = 0; i + 1 < route.size(); i++) {
                    length += Math.hypot(route.get(i + 1)[0] - route.get(i)[0], route.get(i + 1)[1] - route.get(i)[1]);
                }
                double manhattan = Math.abs(goal[0] - sx) + Math.abs(goal[1] - 30);
                noBackwards &= length < manhattan + 260;
            }
        }
        expect(allOnStreets, "Navi: Route führt nur über Straßen und biegt nur an Kreuzungen ab");
        expect(noBackwards, "Navi: keine Umwege oder Sprünge entlang einer Straße");
        CityMap.Turn turn = CityMap.nextTurn(CityMap.route(9, 300, 300, 500));
        expect(turn != null && turn.distance() > 0, "Navi: nächste Abbiegung wird erkannt");
    }

    /** Stands in front of the clerk of the nearest place of a kind, looks at him and right clicks. */
    private void talkToClerk(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn,
                             CityMap.Kind kind, String what) {
        BlockPos spawn = CityPlaces.spawn();
        CityMap.Place place = CityMap.nearest(kind, spawn.getX(), spawn.getZ());
        Clerks.Spot spot = Clerks.spot(place);
        net.minecraft.core.Direction out = net.minecraft.core.Direction.fromYRot(spot.yaw());
        teleport(server, spot.x() + out.getStepX() * 2.0, spot.y(), spot.z() + out.getStepZ() * 2.0,
                spot.yaw() + 180.0F, 0.0F);
        settle(ctx, conn);
        // The clerk is put there while a player is near.
        int clerk = -1;
        for (int i = 0; i < 12 && clerk < 0; i++) {
            ctx.waitTicks(20);
            clerk = server.computeOnServer(s -> {
                var list = s.overworld().getEntitiesOfClass(NpcEntity.class, AABB.ofSize(new Vec3(spot.x(),
                        spot.y() + 1, spot.z()), 4, 4, 4), n -> spot.role().equals(n.role()));
                return list.isEmpty() ? -1 : list.getFirst().getId();
            });
        }
        expect(clerk >= 0, what + ": Mitarbeiter steht hinter der Theke (" + spot.name() + ")");
        if (clerk < 0) {
            System.out.println("GTACITY-TEST Diagnose Mitarbeiter: place=" + place.x() + "," + place.z() + " kind="
                    + place.kind() + " lot=" + place.lot().type + " spot=" + spot + " " + server.computeOnServer(s -> {
                BlockPos c = BlockPos.containing(spot.x(), spot.y(), spot.z());
                StringBuilder b = new StringBuilder("block=" + s.overworld().getBlockState(c).getBlock()
                        + " above=" + s.overworld().getBlockState(c.above()).getBlock() + " below="
                        + s.overworld().getBlockState(c.below()).getBlock() + " chunk="
                        + s.overworld().hasChunkAt(c) + " ticking=" + s.overworld().isPositionEntityTicking(c)
                        + " player=" + player(s).position());
                for (NpcEntity n : s.overworld().getEntitiesOfClass(NpcEntity.class, AABB.ofSize(new Vec3(spot.x(),
                        spot.y(), spot.z()), 30, 30, 30), n -> !n.role().isEmpty())) {
                    b.append(" [").append(n.role()).append(" ").append(n.position()).append("]");
                }
                return b.toString();
            }));
            return;
        }
        int light = server.computeOnServer(s -> s.overworld().getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                BlockPos.containing(spot.x(), spot.y() + 1, spot.z())));
        expect(light >= 8, what + ": drinnen ist es hell (Lichtstärke " + light + ")");
        boolean counter = server.computeOnServer(s -> {
            BlockPos c = BlockPos.containing(spot.x(), spot.y(), spot.z());
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-2, 0, -2), c.offset(2, 1, 2))) {
                var b = s.overworld().getBlockState(q).getBlock();
                if (b instanceof de.gtacity.block.ShopCounterBlock || b instanceof de.gtacity.block.JobDeskBlock) {
                    return true;
                }
            }
            return false;
        });
        expect(counter, what + ": Theke steht direkt vor dem Mitarbeiter");
        aim(ctx, new Vec3(spot.x(), spot.y() + 1.6, spot.z()));
        ctx.getInput().pressKey(o -> o.keyUse);
    }

    private void shops(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        server.runCommand("item replace entity @a hotbar.1 with minecraft:air");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[1]);
        server.runOnServer(s -> Economy.set(player(s), 5000));
        // Supermarket: talk to the clerk, buy a burger.
        talkToClerk(ctx, server, conn, CityMap.Kind.STORE, "Supermarkt");
        ctx.waitForScreen(ShopScreen.class);
        ok("Supermarkt: Ansprechen des Verkäufers öffnet den Laden");
        ctx.takeScreenshot("gtacity-22-supermarkt");
        int burgers = server.computeOnServer(s -> player(s).getInventory().countItem(ModItems.BURGER));
        ctx.clickScreenButton("Kaufen");
        ctx.waitTicks(10);
        int after = server.computeOnServer(s -> player(s).getInventory().countItem(ModItems.BURGER));
        expect(after > burgers, "Supermarkt: Burger gekauft (" + burgers + " -> " + after + ")");
        ctx.clickScreenButton("Schließen");
        ctx.waitFor(mc -> mc.gui.screen() == null, 40);
        talkToClerk(ctx, server, conn, CityMap.Kind.AMMU_NATION, "Waffenladen");
        ctx.waitForScreen(ShopScreen.class);
        ok("Waffenladen: Ansprechen des Händlers öffnet den Laden");
        ctx.takeScreenshot("gtacity-23-waffenladen");
        ctx.clickScreenButton("Schließen");
        ctx.waitFor(mc -> mc.gui.screen() == null, 40);
        talkToClerk(ctx, server, conn, CityMap.Kind.CAR_DEALER, "Autohaus");
        ctx.waitForScreen(ShopScreen.class);
        ok("Autohaus: Ansprechen des Verkäufers öffnet den Laden");
        ctx.clickScreenButton("Schließen");
        ctx.waitFor(mc -> mc.gui.screen() == null, 40);
        // Gas station shop
        talkToClerk(ctx, server, conn, CityMap.Kind.GAS_STATION, "Tankstelle");
        ctx.waitForScreen(ShopScreen.class);
        ok("Tankstelle: Ansprechen des Verkäufers öffnet den Laden");
        ctx.clickScreenButton("Schließen");
        ctx.waitFor(mc -> mc.gui.screen() == null, 40);
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        settle(ctx, conn);
        reset(server);
    }

    /** Walks every shop and job station near the spawn: clerk there, bright inside, counter in front, sells. */
    private void allClerks(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode creative @a");
        BlockPos spawn = CityPlaces.spawn();
        List<CityMap.Place> places = new ArrayList<>(Clerks.placesWithClerks().stream()
                .filter(p -> Math.hypot(p.x() - spawn.getX(), p.z() - spawn.getZ()) < 700).toList());
        int ok = 0, total = 0;
        List<String> bad = new ArrayList<>();
        for (CityMap.Place place : places) {
            total++;
            Clerks.Spot spot = Clerks.spot(place);
            teleport(server, spot.x(), spot.y() + 3, spot.z() + 5, 180.0F, 30.0F);
            int clerk = -1;
            for (int i = 0; i < 15 && clerk < 0; i++) {
                ctx.waitTicks(20);
                clerk = server.computeOnServer(s -> {
                    var list = s.overworld().getEntitiesOfClass(NpcEntity.class, AABB.ofSize(new Vec3(spot.x(),
                            spot.y() + 1, spot.z()), 4, 4, 4), n -> spot.role().equals(n.role()));
                    return list.isEmpty() ? -1 : list.getFirst().getId();
                });
            }
            int light = server.computeOnServer(s -> s.overworld().getBrightness(
                    net.minecraft.world.level.LightLayer.BLOCK, BlockPos.containing(spot.x(), spot.y() + 1, spot.z())));
            boolean counter = server.computeOnServer(s -> Clerks.counterNear(s.overworld(), spot));
            if (clerk >= 0 && light >= 8 && counter) {
                ok++;
            } else {
                bad.add(place.kind() + "@" + place.x() + "," + place.z() + " (Mitarbeiter=" + (clerk >= 0)
                        + ", Licht=" + light + ", Theke=" + counter + ")");
            }
        }
        expect(ok == total, "Alle " + total + " Läden und Jobstationen im Umkreis: Mitarbeiter da, hell, Theke ("
                + ok + " in Ordnung" + (bad.isEmpty() ? "" : ", Probleme: " + String.join("; ", bad)) + ")");
        // Every shop sells: buy the first offer of every catalogue on the server (in survival, creative is free).
        server.runCommand("gamemode survival @a");
        ctx.waitTicks(5);
        server.runOnServer(s -> Economy.set(player(s), 1_000_000));
        boolean allSell = server.computeOnServer(s -> {
            for (de.gtacity.shop.ShopType type : de.gtacity.shop.ShopType.values()) {
                long before = Economy.get(player(s));
                de.gtacity.shop.ShopCatalog.buy(player(s), type, 0);
                if (Economy.get(player(s)) >= before) {
                    return false;
                }
            }
            return true;
        });
        expect(allSell, "Jeder Laden verkauft (24/7, Ammu-Nation, Autohaus)");
        server.runOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class, player(s).getBoundingBox()
                .inflate(20)).forEach(CarEntity::despawn));
        server.runCommand("gamemode survival @a");
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        settle(ctx, conn);
        reset(server);
    }

    private static void closeScreen(ClientGameTestContext ctx) {
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(2);
    }

    /** The story needs no job board: a few seconds after joining the first chapter phones the player. */
    private void storyStart(ClientGameTestContext ctx, TestServerContext server) {
        // All recorded voice lines (Marco, Tony, police, gang, pedestrians) are known to the sound system.
        String[] voices = {"marco_chapter1", "marco_chapter2", "marco_chapter3", "marco_courier", "marco_taxi",
                "marco_ambulance", "marco_bounty", "marco_gang_war", "marco_job_done", "tony_chapter4", "tony_chapter5",
                "tony_gun_running", "tony_car_theft", "tony_gang_car", "tony_protection", "tony_street_race",
                "tony_crew_heist", "tony_job_done", "police_surrender", "gang_threat", "pedestrian_angry"};
        List<String> missing = ctx.computeOnClient(mc -> java.util.Arrays.stream(voices)
                .filter(v -> mc.getSoundManager().getSoundEvent(de.gtacity.GtaCity.id("voice_" + v)) == null)
                .toList());
        expect(missing.isEmpty(), "Sprachausgabe: alle " + voices.length + " Sprachdateien geladen"
                + (missing.isEmpty() ? "" : ", es fehlen " + missing));
        Jobs.autoStory = true;
        try {
            var mission = (ModAttachments.Mission) null;
            for (int i = 0; i < 40 && mission == null; i++) {
                ctx.waitTicks(10);
                mission = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
            }
            expect(mission != null, "Story startet von selbst nach dem Betreten der Welt ("
                    + (mission == null ? "-" : mission.label()) + ")");
            ctx.takeScreenshot("gtacity-02b-story-start");
        } finally {
            Jobs.autoStory = false;
            server.runOnServer(s -> {
                Jobs.cancel(player(s), "Test");
                Jobs.forget(player(s));
            });
        }
    }

    /**
     * Multiplayer with a second (simulated) player: crew invitation, both on the map, a partner mission where the
     * mate's work pays both, a crew-only mission and the new gang war.
     */
    private void crew(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        server.runOnServer(s -> {
            ServerPlayer me = player(s);
            Jobs.cancel(me, "Test");
            ServerLevel level = s.overworld();
            var mate = net.fabricmc.fabric.api.entity.FakePlayer.get(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.fromString("0000c0de-0000-4000-8000-00000000c0de"),
                            "Kumpel"));
            mate.snapTo(me.getX() + 40, me.getY(), me.getZ() + 25, 90.0F, 0.0F);
            level.addNewPlayer(mate);
            Crew.invite(me, mate);
            Crew.accept(mate);
            Economy.set(me, 1000);
            Economy.set(mate, 1000);
        });
        expect(server.computeOnServer(s -> Crew.inCrew(player(s))), "Crew: Einladung angenommen, zwei Spieler in der Crew");
        ctx.waitTicks(25);
        var dots = ctx.computeOnClient(mc -> de.gtacity.client.map.OtherPlayers.all());
        expect(dots.size() == 1 && dots.getFirst().crew() && dots.getFirst().name().equals("Kumpel"),
                "Crew: der Mitspieler kommt beim Client an (" + dots + ")");
        ctx.waitTicks(10);
        ctx.takeScreenshot("gtacity-19-radar-crew");
        ctx.runOnClient(mc -> {
            CityMapScreen.view(spawn.getX() + 20, spawn.getZ() + 12, 0.5);
            mc.gui.setScreen(new CityMapScreen());
        });
        ctx.waitForScreen(CityMapScreen.class);
        ctx.waitTicks(20);
        ctx.takeScreenshot("gtacity-19b-karte-crew");
        ctx.clickScreenButton("Crew");
        ctx.waitTicks(10);
        ctx.takeScreenshot("gtacity-19c-crew-tab");
        closeScreen(ctx);

        // Partner mission: I take a courier job, my mate is in it too - and his delivery pays both of us.
        server.runOnServer(s -> {
            Jobs.openBoard(player(s), Jobs.Station.JOBCENTER);
            Jobs.start(player(s), Jobs.Type.COURIER);
        });
        closeScreen(ctx);
        expect(server.computeOnServer(s -> Jobs.active(player(s)) && Jobs.active(Crew.mates(player(s)).getFirst())),
                "Partnermission: der Job läuft für beide");
        long before = server.computeOnServer(s -> Economy.get(player(s)));
        long mateBefore = server.computeOnServer(s -> Economy.get(Crew.mates(player(s)).getFirst()));
        var goal = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        server.runOnServer(s -> {
            ServerPlayer mate = Crew.mates(player(s)).getFirst();
            mate.teleportTo(s.overworld(), goal.x() + 0.5, CityLayout.GROUND + 2.0, goal.z() + 0.5, java.util.Set.of(),
                    0.0F, 0.0F, true);
        });
        ctx.waitTicks(30);
        long after = server.computeOnServer(s -> Economy.get(player(s)));
        long mateAfter = server.computeOnServer(s -> Economy.get(Crew.mates(player(s)).getFirst()));
        var next = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        expect(after > before && mateAfter > mateBefore && next != null && next.label().startsWith("Paket 2"),
                "Partnermission: Lieferung vom Mitspieler zahlt beiden (+$" + (after - before) + " / +$"
                        + (mateAfter - mateBefore) + "), weiter mit " + (next == null ? "-" : next.label()));
        server.runOnServer(s -> {
            Jobs.cancel(Crew.mates(player(s)).getFirst(), "Test");
        });
        expect(server.computeOnServer(s -> Jobs.active(player(s))), "Partnermission: steigt einer aus, macht der "
                + "andere weiter");
        server.runOnServer(s -> Jobs.cancel(player(s), "Test"));

        // Team job from the board: the other player gets a question in the chat, [Ja] teleports him to me.
        server.runOnServer(s -> {
            ServerPlayer mate = Crew.mates(player(s)).getFirst();
            Crew.leave(mate, false);
            mate.teleportTo(s.overworld(), player(s).getX() + 300, CityLayout.GROUND + 2.0, player(s).getZ(),
                    java.util.Set.of(), 0.0F, 0.0F, true);
            Jobs.openBoard(player(s), Jobs.Station.SHADY);
        });
        ctx.waitForScreen(JobBoardScreen.class);
        ctx.clickScreenButton("Team-Jobs (3)");
        ctx.waitTicks(5);
        ctx.takeScreenshot("gtacity-19e-team-jobs");
        closeScreen(ctx);
        server.runOnServer(s -> {
            Jobs.openBoard(player(s), Jobs.Station.SHADY);
            Jobs.startTeam(player(s), Jobs.Type.CREW_HEIST);
        });
        closeScreen(ctx);
        expect(server.computeOnServer(s -> Jobs.active(player(s))), "Team-Job: Bankraub im Team startet");
        String answer = server.computeOnServer(s -> {
            ServerPlayer mate = s.overworld().players().stream()
                    .filter(p -> p instanceof net.fabricmc.fabric.api.entity.FakePlayer).findFirst().orElseThrow();
            Jobs.acceptTeam(mate);
            return Jobs.active(mate) + " " + (int) mate.distanceTo(player(s));
        });
        expect(answer.startsWith("true") && Integer.parseInt(answer.split(" ")[1]) < 5,
                "Team-Job: Ja teleportiert den Mitspieler her und er macht mit (" + answer + ")");
        server.runOnServer(s -> {
            for (ServerPlayer p : List.copyOf(s.overworld().players())) {
                Jobs.cancel(p, "Test");
                if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) {
                    Crew.leave(p, false);
                }
            }
        });

        // Money for operators: /geld geben
        long cash = server.computeOnServer(s -> Economy.get(player(s)));
        server.runCommand("geld geben @a 5000");
        expect(server.computeOnServer(s -> Economy.get(player(s))) == cash + 5000, "/geld geben: +$5.000");

        // Gang war (alone): four gang members at the hideout, clear them all.
        server.runOnServer(s -> {
            Economy.set(player(s), 1000);
            Jobs.openBoard(player(s), Jobs.Station.JOBCENTER);
            Jobs.start(player(s), Jobs.Type.GANG_WAR);
        });
        closeScreen(ctx);
        var gang = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        expect(gang != null && gang.label().contains("Gang"), "Bandenkrieg startet (" + (gang == null ? "-"
                : gang.label()) + ")");
        if (gang != null) {
            server.runCommand("gamemode creative @a");
            teleport(server, gang.x() + 0.5, CityLayout.GROUND + 2.0, gang.z() + 8.5, 180.0F, 10.0F);
            ctx.waitTicks(40);
            int gangsters = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.PEDESTRIAN,
                    n -> n.isAlive() && "bounty".equals(n.role())).size());
            expect(gangsters == 4, "Bandenkrieg: vier Gangster im Versteck (" + gangsters + ")");
            ctx.takeScreenshot("gtacity-19d-bandenkrieg");
            server.runOnServer(s -> s.overworld().getEntities(ModEntities.PEDESTRIAN,
                    n -> n.isAlive() && "bounty".equals(n.role())).forEach(n -> n.hurtServer(s.overworld(),
                    s.overworld().damageSources().playerAttack(player(s)), 1000.0F)));
            ctx.waitTicks(30);
            long money = server.computeOnServer(s -> Economy.get(player(s)));
            expect(!server.computeOnServer(s -> Jobs.active(player(s))) && money >= 7000,
                    "Bandenkrieg: Versteck ausgeräumt, $" + money);
            // The "Weitermachen" window: next order, one level harder (one gangster more).
            ctx.waitForScreen(de.gtacity.client.screen.JobDoneScreen.class);
            ctx.takeScreenshot("gtacity-19f-weitermachen");
            ctx.clickScreenButton("Weitermachen (Stufe 2)");
            ctx.waitTicks(10);
            var level2 = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
            expect(level2 != null && level2.label().contains("5 Gangster"),
                    "Weitermachen: Stufe 2 ist schwerer (" + (level2 == null ? "-" : level2.label()) + ")");
        }
        server.runOnServer(s -> {
            Jobs.cancel(player(s), "Test");
            for (ServerPlayer p : List.copyOf(s.overworld().players())) {
                if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer fake) {
                    Crew.forget(fake);
                    Jobs.forget(fake);
                    s.overworld().removePlayerImmediately(fake, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            }
        });
        server.runCommand("kill @e[type=minecraft:item]");
    }

    /** New illegal job: steal the gang's car (they shout and attack), and the police call to give up. */
    private void gangCar(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        server.runOnServer(s -> {
            Jobs.cancel(player(s), "Test");
            Economy.set(player(s), 1000);
            player(s).setHealth(player(s).getMaxHealth());
            Jobs.openBoard(player(s), Jobs.Station.SHADY);
            Jobs.start(player(s), Jobs.Type.GANG_CAR);
        });
        closeScreen(ctx);
        var goal = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        expect(goal != null && goal.label().contains("Wagen der Gang"), "Gang-Auto: Job startet ("
                + (goal == null ? "-" : goal.label()) + ")");
        if (goal == null) {
            return;
        }
        server.runCommand("effect give @a minecraft:resistance 60 4 true");
        teleport(server, goal.x() + 0.5, CityLayout.GROUND + 2.0, goal.z() + 40.5, 180.0F, 10.0F);
        ctx.waitTicks(30);
        teleport(server, goal.x() + 0.5, CityLayout.GROUND + 2.0, goal.z() + 9.5, 180.0F, 10.0F);
        ctx.waitTicks(30);
        boolean attacked = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.PEDESTRIAN,
                n -> "bounty".equals(n.role()) && n.getTarget() == player(s)).size() > 0);
        expect(attacked, "Gang-Auto: die Gang entdeckt dich, droht und greift an");
        ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
        ctx.takeScreenshot("gtacity-20-gangauto");
        boolean inCar = server.computeOnServer(s -> {
            var cars = s.overworld().getEntitiesOfClass(CarEntity.class, player(s).getBoundingBox().inflate(20),
                    c -> c.getPassengers().isEmpty());
            cars.sort(java.util.Comparator.comparingDouble(c -> c.distanceToSqr(goal.x(), c.getY(), goal.z())));
            return !cars.isEmpty() && player(s).startRiding(cars.getFirst());
        });
        ctx.waitTicks(20);
        var deliver = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        expect(inCar && deliver != null && deliver.label().contains("Käufer"), "Gang-Auto: geklaut, jetzt zum "
                + "Käufer (" + (deliver == null ? "-" : deliver.label()) + ")");
        server.runOnServer(s -> Jobs.moveGoalForTest(player(s), player(s).blockPosition()));
        ctx.waitTicks(20);
        long money = server.computeOnServer(s -> Economy.get(player(s)));
        expect(money >= 9000, "Gang-Auto: beim Käufer abgeliefert ($" + money + ")");
        server.runOnServer(s -> {
            Jobs.cancel(player(s), "Test");
            player(s).stopRiding();
        });
        ctx.runOnClient(mc -> mc.gui.setScreen(null));

        // Two stars: the first officer calls on you to give up before they shoot.
        reset(server);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            WantedSystem.setLevel(p, 2);
            PoliceEntity cop = ModEntities.POLICE.create(s.overworld(), EntitySpawnReason.COMMAND);
            cop.randomizeLook(false);
            cop.snapTo(p.getX() + 6, p.getY(), p.getZ(), 90.0F, 0.0F);
            s.overworld().addFreshEntity(cop);
            cop.setTarget(p);
        });
        ctx.waitTicks(20);
        boolean warned = server.computeOnServer(s -> !PoliceEntity.warningOver(player(s))
                && s.overworld().getEntities(ModEntities.POLICE, c -> c.gestureKind() == NpcEntity.POINT).size() > 0);
        expect(warned, "Polizei: der erste Polizist fordert dich zum Aufgeben auf, bevor geschossen wird");
        reset(server);
        server.runCommand("effect clear @a");
    }

    /** Position (x, y, z) and facing (dx, dz) of the billboard nearest to the spawn on lots of the given types. */
    private static int[] findBillboard(TestServerContext server, CityLayout.LotType... types) {
        return server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            BlockPos spawn = CityPlaces.spawn();
            int cx = CityLayout.cell(spawn.getX()), cz = CityLayout.cell(spawn.getZ());
            int[] best = null;
            double bestDist = Double.MAX_VALUE;
            for (int gx = cx - 3; gx <= cx + 3; gx++) {
                for (int gz = cz - 3; gz <= cz + 3; gz++) {
                    if (CityLayout.isParkCell(gx, gz)) {
                        continue;
                    }
                    int n = CityLayout.lotsPerSide(gx, gz);
                    for (int qx = 0; qx < n; qx++) {
                        for (int qz = 0; qz < n; qz++) {
                            de.gtacity.world.Lot lot = new de.gtacity.world.Lot(gx, gz, qx, qz, n);
                            if (!java.util.List.of(types).contains(lot.type)) {
                                continue;
                            }
                            for (int x = lot.x0; x <= lot.x1; x++) {
                                for (int z = lot.z0; z <= lot.z1; z++) {
                                    for (int y = CityLayout.GROUND; y < CityLayout.GROUND + 140; y++) {
                                        BlockPos pos = new BlockPos(x, y, z);
                                        var state = level.getBlockState(pos);
                                        if (state.getBlock() instanceof de.gtacity.block.BillboardBlock
                                                && state.getValue(de.gtacity.block.BillboardBlock.TILE)
                                                == de.gtacity.block.BillboardBlock.SIZE * 4 + 3) {
                                            double d = pos.distSqr(spawn);
                                            if (d < bestDist) {
                                                bestDist = d;
                                                var f = state.getValue(de.gtacity.block.BillboardBlock.FACING);
                                                best = new int[]{x, y, z, f.getStepX(), f.getStepZ()};
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return best;
        });
    }

    /** Looks at a billboard from {@code distance} blocks in front of it, {@code dy} blocks above/below its middle. */
    private void viewBillboard(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn,
                               int[] b, double distance, double dy, float pitch) {
        double vx = b[0] + 0.5 + b[3] * distance, vz = b[2] + 0.5 + b[4] * distance;
        float yaw = (float) Math.toDegrees(Math.atan2(-b[3], b[4])) + 180.0F;
        teleport(server, vx, b[1] - 1.0 + dy, vz, yaw, pitch);
        settle(ctx, conn);
        ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
    }

    /** GlowCube billboards: on some office roofs and skyscraper fronts, on stilts in car parks and parks. */
    private void billboards(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode creative @a");
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        });
        int[] high = findBillboard(server, CityLayout.LotType.OFFICE, CityLayout.LotType.SKYSCRAPER);
        expect(high != null, "Werbung: GlowCube-Tafeln an Hochhäusern / auf Dächern"
                + (high == null ? "" : " (" + high[0] + ", " + high[1] + ", " + high[2] + ")"));
        if (high != null) {
            viewBillboard(ctx, server, conn, high, 22, 0, 0.0F);
            ctx.takeScreenshot("gtacity-25-werbung-tag");
            server.runCommand("time set 18000");
            ctx.waitTicks(20);
            ctx.takeScreenshot("gtacity-25b-werbung-nacht");
            server.runCommand("time set 6000");
        }
        int[] ground = findBillboard(server, CityLayout.LotType.PARKING, CityLayout.LotType.POCKET_PARK);
        expect(ground != null, "Werbung: GlowCube-Tafeln auf Stelzen am Boden"
                + (ground == null ? "" : " (" + ground[0] + ", " + ground[1] + ", " + ground[2] + ")"));
        if (ground != null) {
            // Stilts: the picture starts 6 blocks above the ground, nothing stands in the street.
            // Under the middle of the picture there are five free blocks - a car fits through.
            boolean free = server.computeOnServer(s -> {
                for (int y = CityLayout.GROUND + 1; y <= CityLayout.GROUND + 5; y++) {
                    if (!s.overworld().getBlockState(new BlockPos(ground[0], y, ground[2])).isAir()) {
                        return false;
                    }
                }
                return true;
            });
            expect(ground[1] >= CityLayout.GROUND + 6 && free, "Werbung: Stelzen-Tafel steht hoch, darunter passt "
                    + "ein Auto durch");
            viewBillboard(ctx, server, conn, ground, 16, -3, -12.0F);
            ctx.takeScreenshot("gtacity-25c-werbung-stelzen");
        }
    }

    /** Runs the current mission: teleports to every goal until the job is done. */
    private void finishMission(ClientGameTestContext ctx, TestServerContext server, int maxSteps) {
        for (int i = 0; i < maxSteps; i++) {
            var goal = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
            if (goal == null) {
                return;
            }
            server.runOnServer(s -> WantedSystem.setLevel(player(s), 0));
            teleport(server, goal.x() + 0.5, CityLayout.GROUND + 2.0, goal.z() + 0.5, 0.0F, 0.0F);
            ctx.waitTicks(25);
        }
    }

    private void jobs(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode creative @a");
        server.runOnServer(s -> {
            Economy.set(player(s), 1000);
            player(s).setAttached(ModAttachments.STORY, 0);
            player(s).setAttached(ModAttachments.JOBS_DONE, 0);
        });
        // The job centre next to the spawn: talk to Marco.
        talkToClerk(ctx, server, conn, CityMap.Kind.JOB, "Jobcenter");
        ctx.waitForScreen(JobBoardScreen.class);
        ok("Jobcenter: Ansprechen des Mitarbeiters öffnet das Job-Board");
        ctx.waitTicks(10);
        ctx.takeScreenshot("gtacity-18-job-board");
        ctx.clickScreenButton("Führung starten");
        ctx.waitTicks(10);
        var mission = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        expect(mission != null, "Story Kapitel 1 startet die Führung ("
                + (mission == null ? "-" : mission.label()) + ")");
        ctx.takeScreenshot("gtacity-18b-fuehrung");
        if (mission != null) {
            // Marco waits at the first stop and waves, then points at the building when you get there.
            double gx = mission.x() + 2.0, gz = mission.z() + 2.0;
            teleport(server, gx, CityLayout.GROUND + 2.0, gz + 12, 180.0F, 5.0F);
            ctx.waitTicks(50);
            boolean guide = server.computeOnServer(s -> !s.overworld().getEntities(ModEntities.PEDESTRIAN,
                    n -> "guide".equals(n.role())).isEmpty());
            expect(guide, "Führung: Marco wartet an der Station");
            ctx.takeScreenshot("gtacity-18d-fuehrer-winkt");
            teleport(server, gx, CityLayout.GROUND + 2.0, gz + 5, 180.0F, 5.0F);
            ctx.waitTicks(14);
            boolean points = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.PEDESTRIAN,
                    n -> "guide".equals(n.role()) && n.gestureKind() == NpcEntity.POINT).size() > 0);
            expect(points, "Führung: Marco zeigt mit der Hand auf das Gebäude");
            ctx.takeScreenshot("gtacity-18e-fuehrer-zeigt");
        }
        finishMission(ctx, server, 10);
        long money = server.computeOnServer(s -> Economy.get(player(s)));
        int story = server.computeOnServer(s -> Jobs.chapter(player(s)));
        expect(story == 1 && money > 2000, "Führung beendet: Kapitel 1 geschafft, Belohnung kassiert ($" + money + ")");

        // Kapitel 3 darf man nicht überspringen
        server.runOnServer(s -> {
            Jobs.openBoard(player(s), Jobs.Station.JOBCENTER);
            Jobs.startStory(player(s), 3);
        });
        closeScreen(ctx);
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION) == null),
                "Kapitel lassen sich nicht überspringen");

        // Courier: three deliveries, paid.
        long before = server.computeOnServer(s -> Economy.get(player(s)));
        server.runOnServer(s -> {
            Jobs.openBoard(player(s), Jobs.Station.JOBCENTER);
            Jobs.start(player(s), Jobs.Type.COURIER);
        });
        closeScreen(ctx);
        ctx.waitTicks(5);
        var courier = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        expect(courier != null, "Kurierfahrer: Job startet mit Ziel auf der Karte ("
                + (courier == null ? "-" : courier.label()) + ")");
        finishMission(ctx, server, 8);
        long after = server.computeOnServer(s -> Economy.get(player(s)));
        expect(after - before > 1000, "Kurierfahrer: drei Pakete zugestellt, Lohn kassiert (+$" + (after - before) + ")");

        // Bounty: the target glows, dies, pays.
        before = after;
        server.runOnServer(s -> {
            Jobs.openBoard(player(s), Jobs.Station.JOBCENTER);
            Jobs.start(player(s), Jobs.Type.BOUNTY);
        });
        closeScreen(ctx);
        ctx.waitTicks(5);
        var bounty = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        if (bounty != null) {
            teleport(server, bounty.x() + 0.5, CityLayout.GROUND + 2.0, bounty.z() + 0.5, 0.0F, 0.0F);
            ctx.waitTicks(60);
            boolean killed = server.computeOnServer(s -> {
                var list = s.overworld().getEntitiesOfClass(NpcEntity.class, player(s).getBoundingBox().inflate(120),
                        n -> "bounty".equals(n.role()));
                if (list.isEmpty()) {
                    return false;
                }
                list.getFirst().hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player(s)),
                        1000.0F);
                return true;
            });
            ctx.waitTicks(40);
            long paid = server.computeOnServer(s -> Economy.get(player(s))) - before;
            expect(killed && paid >= 3500, "Kopfgeldjäger: Gesuchter aufgespürt und ausgeschaltet (+$" + paid + ")");
        } else {
            fail("Kopfgeldjäger: Job startet nicht");
        }
        expect(server.computeOnServer(s -> WantedSystem.level(player(s))) == 0,
                "Kopfgeldjäger: Töten des Gesuchten bringt keine Fahndung");

        // Taxi: passenger boards the car and is delivered.
        before = server.computeOnServer(s -> Economy.get(player(s)));
        server.runOnServer(s -> {
            Jobs.openBoard(player(s), Jobs.Station.JOBCENTER);
            Jobs.start(player(s), Jobs.Type.TAXI);
        });
        closeScreen(ctx);
        ctx.waitTicks(5);
        var pickup = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        boolean rode = false;
        if (pickup != null) {
            teleport(server, pickup.x() + 0.5, CityLayout.GROUND + 2.0, pickup.z() + 0.5, 0.0F, 0.0F);
            ctx.waitTicks(60);
            server.runOnServer(s -> {
                CarEntity c = ModEntities.CAR.create(s.overworld(), EntitySpawnReason.COMMAND);
                c.setVariant(CarVariant.SEDAN_WHITE);
                c.setPersistentCar(true);
                c.snapTo(pickup.x() + 0.5, CityLayout.GROUND + 2.0, pickup.z() + 0.5, 0.0F, 0.0F);
                s.overworld().addFreshEntity(c);
                c.interact(player(s), InteractionHand.MAIN_HAND, c.position());
            });
            ctx.waitTicks(40);
            rode = server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(NpcEntity.class,
                    player(s).getBoundingBox().inflate(20), n -> "passenger".equals(n.role()) && n.isPassenger())
                    .isEmpty());
        }
        expect(rode, "Taxi: Fahrgast steigt in das Auto ein");
        // Driving across the city is what the car tests do; here the drop-off is moved right next to the taxi.
        server.runOnServer(s -> Jobs.moveGoalForTest(player(s), player(s).blockPosition().offset(3, 0, 0)));
        ctx.waitTicks(40);
        after = server.computeOnServer(s -> Economy.get(player(s)));
        expect(after - before > 200, "Taxi: Fahrgast abgeliefert, Fahrpreis kassiert (+$" + (after - before) + ")");
        server.runOnServer(s -> {
            Jobs.cancel(player(s), "Test");
            s.overworld().getEntitiesOfClass(CarEntity.class, player(s).getBoundingBox().inflate(50))
                    .forEach(CarEntity::despawn);
        });

        // Dirty work at the harbour office: chapter 4 (gun running) and 5 (the bank job).
        server.runOnServer(s -> player(s).setAttached(ModAttachments.STORY, 3));
        talkToClerk(ctx, server, conn, CityMap.Kind.DOCKS, "Hafenbüro");
        ctx.waitForScreen(JobBoardScreen.class);
        ok("Hafenbüro: Ansprechen von Tony öffnet das Job-Board");
        ctx.takeScreenshot("gtacity-18c-hafenbuero");
        System.out.println("GTACITY-TEST Diagnose Kapitel: aktiv=" + server.computeOnServer(s -> Jobs.active(player(s)))
                + " kapitel=" + server.computeOnServer(s -> Jobs.chapter(player(s))) + " clientKapitel="
                + ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.STORY)) + " clientMission="
                + ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION)));
        ctx.clickScreenButton("Kapitel starten");
        ctx.waitTicks(10);
        expect(ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION) != null),
                "Kapitel 4 (Waffenkiste) startet im Hafenbüro");
        finishMission(ctx, server, 6);
        expect(server.computeOnServer(s -> Jobs.chapter(player(s))) == 4, "Kapitel 4 geschafft");
        server.runOnServer(s -> {
            Jobs.openBoard(player(s), Jobs.Station.SHADY);
            Jobs.startStory(player(s), 5);
        });
        closeScreen(ctx);
        ctx.waitTicks(20);
        var drillGoal = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        expect(drillGoal != null && drillGoal.label().contains("Thermobohrer"),
                "Kapitel 5: erst den Thermobohrer kaufen");
        server.runCommand("give @a gtacity:thermal_drill");
        ctx.waitTicks(30);
        var bankGoal = ctx.computeOnClient(mc -> mc.player.getAttached(ModAttachments.MISSION));
        expect(bankGoal != null && bankGoal.label().contains("Bank"), "Kapitel 5: weiter zur Bank");
        finishMission(ctx, server, 1); // reach the bank
        server.runOnServer(s -> Jobs.onVaultOpened(player(s)));
        ctx.waitTicks(30);
        finishMission(ctx, server, 3);
        expect(server.computeOnServer(s -> Jobs.chapter(player(s))) == 5
                && server.computeOnServer(s -> Economy.get(player(s))) > 30000,
                "Kapitel 5 geschafft: der große Coup bringt $30.000");
        BlockPos spawn = CityPlaces.spawn();
        server.runCommand("gamemode survival @a");
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        settle(ctx, conn);
        reset(server);
    }

    private void villa(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        CityMap.Place villa = CityMap.of(CityMap.Kind.VILLA).getFirst();
        int price = CityMap.villaPrice(villa);
        server.runOnServer(s -> Economy.set(player(s), price + 1000L));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.BUY_VILLA, villa.id())));
        ctx.waitTicks(10);
        long money = server.computeOnServer(s -> Economy.get(player(s)));
        boolean owned = ctx.computeOnClient(mc -> {
            List<Long> list = mc.player.getAttached(ModAttachments.VILLAS);
            return list != null && list.contains(villa.id());
        });
        expect(owned && money == 1000, "Villa gekauft für " + Economy.format(price) + " (Rest $" + money + ")");
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.VILLA_TELEPORT,
                villa.id())));
        ctx.waitTicks(20);
        double dist = server.computeOnServer(s -> Math.sqrt(player(s).blockPosition().distSqr(villa.entrance())));
        expect(dist < 4, "Per Klick zur Villa teleportiert (Abstand " + String.format("%.1f", dist) + ")");
        settle(ctx, conn);
        ctx.takeScreenshot("gtacity-19-villa");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        settle(ctx, conn);
        reset(server);
    }

    private void pickpocket(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        server.runCommand("item replace entity @a hotbar.1 with minecraft:air");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[1]);
        server.runOnServer(s -> Economy.set(player(s), 100));
        // Standing 2 blocks south, facing south (away from the player).
        int victim = dummy(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 2.5, false);
        server.runOnServer(s -> {
            var npc = s.overworld().getEntity(victim);
            npc.setYRot(0.0F);
            npc.setYHeadRot(0.0F);
        });
        conn.waitForClientboundEntityUpdates(ModEntities.PEDESTRIAN);
        ctx.waitTicks(5);
        aim(ctx, new Vec3(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 2.5));
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(5);
        ctx.getInput().releaseKey(o -> o.keyShift);
        long money = server.computeOnServer(s -> Economy.get(player(s)));
        expect(money > 100, "Taschendiebstahl: Geldbörse geklaut ($100 -> $" + money + ")");
        reset(server);
    }

    private void helicopter(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode creative @a");
        teleport(server, 9.5, CityLayout.GROUND + 1.0, 9.5, 0.0F, -20.0F);
        server.runOnServer(s -> WantedSystem.setLevel(player(s), 5));
        boolean heli = false;
        for (int i = 0; i < 20 && !heli; i++) {
            ctx.waitTicks(20);
            server.runOnServer(s -> WantedSystem.commit(player(s), 5));
            heli = server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(
                    de.gtacity.entity.HelicopterEntity.class, player(s).getBoundingBox().inflate(160)).isEmpty());
        }
        expect(heli, "5 Sterne: Polizeihubschrauber kommt");
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < 15 && heli; i++) {
            ctx.waitTicks(20);
            server.runOnServer(s -> WantedSystem.commit(player(s), 5));
            closest = Math.min(closest, server.computeOnServer(s -> s.overworld().getEntitiesOfClass(
                    de.gtacity.entity.HelicopterEntity.class, player(s).getBoundingBox().inflate(300)).stream()
                    .mapToDouble(h -> Math.hypot(h.getX() - player(s).getX(), h.getZ() - player(s).getZ()))
                    .min().orElse(999.0)));
        }
        // Horizontal distance: over downtown it has to stay above the skyscrapers.
        expect(closest < 30, "Hubschrauber kreist über dem Spieler (seitlicher Abstand "
                + String.format("%.0f", closest) + ")");
        ctx.getInput().lookAt(0.0F, -45.0F);
        ctx.waitTicks(5);
        ctx.takeScreenshot("gtacity-20-hubschrauber");
        server.runOnServer(s -> {
            WantedSystem.setLevel(player(s), 0);
            s.overworld().getEntitiesOfClass(de.gtacity.entity.HelicopterEntity.class,
                    player(s).getBoundingBox().inflate(200)).forEach(h -> h.damage(s.overworld(), 1000.0F));
        });
        server.runCommand("gamemode survival @a");
        reset(server);
    }

    private void ownCar(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode survival @a");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        double x = 5.5, z = CityLayout.CORRIDOR + 10.5;
        int car = server.computeOnServer(s -> {
            CarEntity c = ModEntities.CAR.create(s.overworld(), EntitySpawnReason.COMMAND);
            c.setVariant(CarVariant.SUV_NAVY);
            c.snapTo(x, CityLayout.GROUND + 1.0, z, 0.0F, 0.0F);
            s.overworld().addFreshEntity(c);
            c.interact(player(s), InteractionHand.MAIN_HAND, c.position());
            return c.getId();
        });
        ctx.waitFor(mc -> mc.player.getVehicle() instanceof CarEntity, 60);
        int before = server.computeOnServer(s -> de.gtacity.gameplay.Garage.cars(player(s)).size());
        ctx.getInput().pressKey(ClientInput.CLAIM_CAR);
        ctx.waitTicks(10);
        boolean owned = server.computeOnServer(s -> s.overworld().getEntity(car) instanceof CarEntity c
                && c.isOwnedBy(player(s)));
        int after = server.computeOnServer(s -> de.gtacity.gameplay.Garage.cars(player(s)).size());
        expect(owned && after == before + 1, "G im Auto: das Auto gehört jetzt dir und steht in der Garage");
        ctx.getInput().pressKey(o -> o.keySwapOffhand);
        ctx.waitFor(mc -> mc.player.getVehicle() == null, 60);
        // Walk away two blocks of the city, then press B.
        teleport(server, CityLayout.PITCH + 9.5, CityLayout.GROUND + 1.0, 2 * CityLayout.PITCH + 40.5, 0.0F, 0.0F);
        settle(ctx, conn);
        ctx.getInput().pressKey(ClientInput.BRING_CAR);
        ctx.waitTicks(20);
        // Far away the old car is not loaded any more - then the mechanic brings it from the garage.
        double dist = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(CarEntity.class,
                player(s).getBoundingBox().inflate(60), c -> c.isOwnedBy(player(s))).stream()
                .mapToDouble(c -> c.distanceTo(player(s))).min().orElse(999.0));
        expect(dist < 45, "B holt das eigene Auto zu dir (Abstand " + String.format("%.0f", dist) + ")");
        ctx.takeScreenshot("gtacity-21-eigenes-auto");
        server.runOnServer(s -> {
            if (s.overworld().getEntity(car) instanceof CarEntity c) {
                c.despawn();
            }
            s.overworld().getEntitiesOfClass(CarEntity.class, player(s).getBoundingBox().inflate(60),
                    c -> c.isOwnedBy(player(s))).forEach(CarEntity::despawn);
        });
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        settle(ctx, conn);
        reset(server);
    }

    /** Pictures for the README: the map at several zoom levels and the sports cars in daylight. */
    private void photos(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        reset(server);
        server.runCommand("gamemode creative @a");
        server.runCommand("time set 5000");
        server.runCommand("weather clear");
        BlockPos spawn = CityPlaces.spawn();
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        // Let the map paint itself.
        ctx.getInput().pressKey(ClientInput.MAP);
        ctx.waitForScreen(CityMapScreen.class);
        for (int i = 0; i < 180 && ctx.computeOnClient(mc -> de.gtacity.client.map.MapTiles.progress()) < 0.35F; i++) {
            ctx.waitTicks(20);
        }
        ctx.runOnClient(mc -> {
            CityMapScreen.view(spawn.getX() + 40, spawn.getZ() + 20, 0.5);
            mc.gui.setScreen(new CityMapScreen());
        });
        ctx.waitTicks(40);
        ctx.takeScreenshot("foto-01-karte-nah");
        ctx.runOnClient(mc -> {
            CityMapScreen.view(spawn.getX() + 60, spawn.getZ() + 60, 1.2);
            mc.gui.setScreen(new CityMapScreen());
        });
        ctx.waitTicks(40);
        ctx.takeScreenshot("foto-02-karte-mittel");
        ctx.runOnClient(mc -> {
            CityMapScreen.view(spawn.getX(), spawn.getZ(), 4.0);
            mc.gui.setScreen(new CityMapScreen());
        });
        ctx.waitTicks(40);
        ctx.takeScreenshot("foto-03-karte-weit");
        ctx.getInput().pressKey(ClientInput.MAP);
        ctx.waitFor(mc -> mc.gui.screen() == null, 40);
        float progress = ctx.computeOnClient(mc -> de.gtacity.client.map.MapTiles.progress());
        expect(progress > 0.3F, "Exakte Karte wird gezeichnet (" + Math.round(progress * 100) + " %)");

        // Sports cars on a quiet street in the Hills, standing free in the middle of the road.
        double x = 9.0, z = -12 * CityLayout.PITCH + 30.0;
        float yaw = 35.0F;
        CarVariant[] row = {CarVariant.SUPER_RED, CarVariant.SUPER_CARBON, CarVariant.SUPER_LIME,
                CarVariant.SUPER_ORANGE, CarVariant.SUPER_PEARL, CarVariant.SUPER_MAGENTA, CarVariant.SPORTS_YELLOW,
                CarVariant.SPORTS_BLUE};
        teleport(server, x, CityLayout.GROUND + 1.0, z - 6, 0.0F, 10.0F);
        settle(ctx, conn);
        List<Integer> ids = server.computeOnServer(s -> {
            s.overworld().getEntitiesOfClass(CarEntity.class, new AABB(x - 40, CityLayout.GROUND - 5, z - 60,
                    x + 40, CityLayout.GROUND + 10, z + 120)).forEach(CarEntity::despawn);
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < row.length; i++) {
                list.add(photoCar(s.overworld(), row[i], x, z + i * 10.0, yaw));
            }
            return list;
        });
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.PEDESTRIAN, n -> n.role().isEmpty())
                .forEach(net.minecraft.world.entity.Entity::discard));
        ctx.runOnClient(mc -> mc.gui.hud.toggle());
        ctx.waitTicks(30);
        String[] names = {"supersportwagen-rot", "supersportwagen-carbon", "supersportwagen-gruen",
                "supersportwagen-orange", "supersportwagen-perlweiss", "supersportwagen-magenta", "sportwagen-gelb",
                "sportwagen-blau"};
        Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
        for (int i = 0; i < row.length; i++) {
            Vec3 car = new Vec3(x, CityLayout.GROUND + 1.0, z + i * 10.0);
            // Front left, three quarters - and for two of them the rear with the wing.
            boolean rear = i == 2 || i == 5;
            Vec3 dir = rear ? forward.scale(-1).yRot((float) Math.toRadians(-35)) : forward.yRot((float) Math.toRadians(40));
            Vec3 eye = car.add(dir.scale(4.4));
            teleport(server, eye.x, CityLayout.GROUND + 1.0, eye.z, 0.0F, 0.0F);
            ctx.waitTicks(8);
            aim(ctx, car.add(0, 0.7, 0));
            ctx.waitTicks(12);
            ctx.takeScreenshot(String.format("foto-%02d-%s%s", 4 + i, names[i], rear ? "-heck" : ""));
        }
        // The whole row from above.
        teleport(server, x + 5.0, CityLayout.GROUND + 7.0, z - 9.0, 0.0F, 0.0F);
        ctx.waitTicks(8);
        aim(ctx, new Vec3(x, CityLayout.GROUND + 1.0, z + 22));
        ctx.waitTicks(15);
        ctx.takeScreenshot("foto-12-reihe");
        ctx.runOnClient(mc -> mc.gui.hud.toggle());
        // Minimap in the Hills.
        teleport(server, x + 7.5, CityLayout.GROUND + 1.0, z - 12, 180.0F, 10.0F);
        ctx.waitTicks(40);
        ctx.takeScreenshot("foto-13-radar");
        ok("Fotos von Karte und Sportwagen aufgenommen");
        server.runOnServer(s -> ids.forEach(id -> {
            if (s.overworld().getEntity(id) instanceof CarEntity c) {
                c.despawn();
            }
        }));
        server.runCommand("gamemode survival @a");
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        reset(server);
    }

    private static int photoCar(ServerLevel level, CarVariant variant, double x, double z, float yaw) {
        CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.COMMAND);
        car.setVariant(variant);
        car.setPersistentCar(true);
        car.snapTo(x, CityLayout.GROUND + 1.0, z, yaw, 0.0F);
        level.addFreshEntity(car);
        return car.getId();
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
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.PEDESTRIAN, n -> n.role().isEmpty())
                .forEach(net.minecraft.world.entity.Entity::discard));
        server.runCommand("kill @e[type=gtacity:police]");
        teleport(server, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        String[] guns = {"pistol", "deagle", "smg", "shotgun", "ak47", "carbine", "sniper", "minigun", "rpg"};
        for (int i = 0; i < guns.length; i++) {
            server.runCommand("item replace entity @a hotbar." + i + " with gtacity:" + guns[i]);
        }
        settle(ctx, conn);
        ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false)); // the chat would cover the guns
        for (int i = 0; i < guns.length; i++) {
            final int slot = i;
            ctx.getInput().pressKey(o -> o.keyHotbarSlots[slot]);
            ctx.waitTicks(15);
            ctx.takeScreenshot("gtacity-10-haltung-" + guns[i]);
        }
        // Shoot and reload animations, frozen mid-way (/tick freeze) for the screenshots.
        String[][] anims = {{"0", "pistol", "schuss"}, {"4", "ak47", "schuss"}, {"6", "sniper", "schuss"},
                {"0", "pistol", "nachladen"}, {"4", "ak47", "nachladen"}, {"3", "shotgun", "nachladen"},
                {"7", "minigun", "schuss"}};
        for (String[] a : anims) {
            final int slot = Integer.parseInt(a[0]);
            final boolean reload = a[2].equals("nachladen");
            ctx.getInput().pressKey(o -> o.keyHotbarSlots[slot]);
            ctx.waitTicks(10);
            server.runCommand("tick freeze");
            ctx.waitTicks(3);
            server.runOnServer(s -> {
                ItemStack gun = player(s).getMainHandItem();
                int now = (int) s.overworld().getGameTime();
                gun.set(ModComponents.GUN_ANIM, reload ? new ModComponents.GunAnim(-100000, now - 20, 40)
                        : new ModComponents.GunAnim(now, -100000, 0));
            });
            ctx.waitTicks(5);
            boolean animated = ctx.computeOnClient(mc -> mc.player.getMainHandItem().has(ModComponents.GUN_ANIM));
            ctx.takeScreenshot("gtacity-10-anim-" + a[1] + "-" + a[2]);
            server.runCommand("tick unfreeze");
            expect(animated, "Waffenanimation " + a[1] + " (" + a[2] + ") kommt beim Client an");
            ctx.waitTicks(45);
        }

        // Third person from behind with the AK.
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[4]);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(15);
        ctx.takeScreenshot("gtacity-10-haltung-ak47-hinten");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        // The inventory with all nine guns.
        ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(
                mc.player, mc.player.connection.enabledFeatures(), false)));
        ctx.waitTicks(15);
        ctx.takeScreenshot("gtacity-10-haltung-inventar");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
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
