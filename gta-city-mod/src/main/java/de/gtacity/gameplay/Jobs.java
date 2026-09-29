package de.gtacity.gameplay;

import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.entity.NpcEntity;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.registry.ModAttachments;
import de.gtacity.registry.ModItems;
import de.gtacity.registry.ModSounds;
import de.gtacity.world.CityLayout;
import de.gtacity.world.CityMap;
import de.gtacity.world.CityPlaces;
import de.gtacity.world.Lot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Jobs from the phone (map screen): delivery driver (legal), gun running and car theft for the chop shop (illegal).
 * Plus small street crime: pickpocketing pedestrians and shoplifting at the 24/7.
 */
public final class Jobs {
    private Jobs() {
    }

    public enum Type {
        DELIVERY("Lieferant", "Pakete vom Depot zu den Kunden fahren. Legal, sicheres Geld.", false),
        GUN_RUNNING("Waffendealer", "Eine Kiste Waffen im Hafen abholen und dem Käufer bringen. Die Polizei darf "
                + "dich bei der Übergabe nicht verfolgen.", true),
        CAR_THEFT("Autodieb", "Einen Sportwagen oder Supersportwagen klauen und zum Schrottplatz im Hafen bringen.",
                true);

        public final String label;
        public final String description;
        public final boolean illegal;

        Type(String label, String description, boolean illegal) {
            this.label = label;
            this.description = description;
            this.illegal = illegal;
        }
    }

    private static final int DELIVERIES = 3;
    private static final long LEG_TIME = 20L * 60 * 5;
    private static final double ARRIVE = 6.0;

    private static final class Job {
        final Type type;
        final List<BlockPos> targets = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        int step;
        long deadline;
        long earned;

        Job(Type type) {
            this.type = type;
        }

        BlockPos target() {
            return targets.get(step);
        }
    }

    private static final Map<UUID, Job> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> PICKPOCKETED = new HashMap<>();
    private static final Map<BlockPos, Long> SHOPLIFTED = new HashMap<>();

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    // ------------------------------------------------------------------ start / stop

    public static void start(ServerPlayer player, Type type) {
        if (ACTIVE.containsKey(player.getUUID())) {
            player.sendOverlayMessage(Component.literal("Du hast schon einen Job. Brich ihn erst ab.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = level.getRandom();
        Job job = new Job(type);
        switch (type) {
            case DELIVERY -> {
                CityMap.Place depot = CityMap.nearest(CityMap.Kind.DEPOT, player.getX(), player.getZ());
                BlockPos from = depot == null ? CityPlaces.spawn() : depot.entrance();
                add(job, from, "Pakete im Depot abholen");
                BlockPos last = from;
                for (int i = 1; i <= DELIVERIES; i++) {
                    BlockPos to = randomAddress(random, last, 200, 700,
                            EnumSet.of(CityLayout.LotType.HOUSE, CityLayout.LotType.VILLA, CityLayout.LotType.OFFICE,
                                    CityLayout.LotType.SKYSCRAPER));
                    add(job, to, "Paket " + i + "/" + DELIVERIES + " zustellen");
                    last = to;
                }
            }
            case GUN_RUNNING -> {
                BlockPos crate = randomAddress(random, player.blockPosition(), 0, 5000,
                        EnumSet.of(CityLayout.LotType.CONTAINERS), CityLayout.District.INDUSTRIAL);
                add(job, crate, "Waffenkiste im Hafen abholen");
                add(job, randomAddress(random, crate, 700, 1600,
                        EnumSet.of(CityLayout.LotType.HOUSE, CityLayout.LotType.WAREHOUSE, CityLayout.LotType.PARKING)),
                        "Waffen dem Käufer bringen");
            }
            case CAR_THEFT -> {
                BlockPos yard = randomAddress(random, player.blockPosition(), 0, 5000,
                        EnumSet.of(CityLayout.LotType.CONTAINERS), CityLayout.District.INDUSTRIAL);
                add(job, yard, "Sportwagen zum Schrottplatz bringen");
            }
        }
        job.deadline = level.getGameTime() + LEG_TIME;
        ACTIVE.put(player.getUUID(), job);
        player.sendSystemMessage(Component.literal("Job: " + type.label + " - " + type.description)
                .withStyle(type.illegal ? ChatFormatting.RED : ChatFormatting.AQUA));
        if (type == Type.CAR_THEFT) {
            player.sendSystemMessage(Component.literal("Gekaufte Autos nimmt der Schrottplatz nicht - es muss "
                    + "geklaut sein. Sportwagen $6.000, Supersportwagen $15.000.").withStyle(ChatFormatting.GRAY));
        }
        updateMission(player, job);
    }

    public static void cancel(ServerPlayer player, String reason) {
        Job job = ACTIVE.remove(player.getUUID());
        player.removeAttached(ModAttachments.MISSION);
        if (job != null) {
            player.sendSystemMessage(Component.literal("Job beendet: " + reason).withStyle(ChatFormatting.GRAY));
        }
    }

    public static void forget(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    private static void add(Job job, BlockPos pos, String label) {
        job.targets.add(pos);
        job.labels.add(label);
    }

    private static void updateMission(ServerPlayer player, Job job) {
        BlockPos target = job.target();
        player.setAttached(ModAttachments.MISSION, new ModAttachments.Mission(job.labels.get(job.step),
                target.getX(), target.getZ()));
    }

    /** Sidewalk in front of a random lot of the given types, between min and max blocks away from {@code from}. */
    private static BlockPos randomAddress(RandomSource random, BlockPos from, int min, int max,
                                          Set<CityLayout.LotType> types, CityLayout.District... districts) {
        BlockPos fallback = null;
        for (int attempt = 0; attempt < 400; attempt++) {
            int gx = random.nextInt(CityLayout.HALF_CELLS * 2) - CityLayout.HALF_CELLS;
            int gz = random.nextInt(CityLayout.HALF_CELLS * 2) - CityLayout.HALF_CELLS;
            if (CityLayout.isParkCell(gx, gz)) {
                continue;
            }
            if (districts.length > 0 && !List.of(districts).contains(CityLayout.district(gx, gz))) {
                continue;
            }
            int n = CityLayout.lotsPerSide(gx, gz);
            Lot lot = new Lot(gx, gz, random.nextInt(n), random.nextInt(n), n);
            if (!types.contains(lot.type) || lot.front == null) {
                continue;
            }
            BlockPos pos = CityPlaces.entrance(lot);
            double d = Math.sqrt(pos.distSqr(from.atY(pos.getY())));
            if (fallback == null) {
                fallback = pos;
            }
            if (d >= min && d <= max) {
                return pos;
            }
        }
        return fallback != null ? fallback : CityPlaces.spawn();
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0 || ACTIVE.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(ACTIVE.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Job job = ACTIVE.get(id);
            if (player == null || job == null) {
                ACTIVE.remove(id);
                continue;
            }
            if (!player.isAlive()) {
                cancel(player, "Du bist gestorben.");
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            if (level.getGameTime() > job.deadline) {
                cancel(player, "Zu spät!");
                continue;
            }
            BlockPos target = job.target();
            double dx = player.getX() - (target.getX() + 0.5), dz = player.getZ() - (target.getZ() + 0.5);
            if (dx * dx + dz * dz > ARRIVE * ARRIVE * (player.getVehicle() != null ? 2.5 : 1.0)) {
                continue;
            }
            arrive(level, player, job);
        }
    }

    private static void arrive(ServerLevel level, ServerPlayer player, Job job) {
        switch (job.type) {
            case DELIVERY -> {
                if (job.step == 0) {
                    player.sendOverlayMessage(Component.literal(DELIVERIES + " Pakete eingeladen. Los geht's!")
                            .withStyle(ChatFormatting.AQUA));
                } else {
                    BlockPos from = job.targets.get(job.step - 1);
                    int pay = 150 + (int) (Math.sqrt(from.distSqr(job.target())) * 1.2);
                    pay(player, job, pay, "Paket zugestellt");
                }
            }
            case GUN_RUNNING -> {
                if (job.step == 0) {
                    player.sendOverlayMessage(Component.literal("Waffenkiste geladen!").withStyle(ChatFormatting.RED));
                    if (level.getRandom().nextFloat() < 0.5F) {
                        WantedSystem.commit(player, 2);
                        player.sendSystemMessage(Component.literal("Ein Zeuge hat die Polizei gerufen!")
                                .withStyle(ChatFormatting.RED));
                    }
                } else {
                    if (WantedSystem.level(player) > 0) {
                        player.sendOverlayMessage(Component.literal("Der Käufer wartet nicht auf die Bullen - "
                                + "häng erst die Polizei ab!").withStyle(ChatFormatting.RED));
                        return;
                    }
                    pay(player, job, 5000 + level.getRandom().nextInt(4001), "Waffen verkauft");
                }
            }
            case CAR_THEFT -> {
                if (!(player.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != player) {
                    player.sendOverlayMessage(Component.literal("Du musst mit einem geklauten Sportwagen kommen.")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                CarVariant.Shape shape = car.getVariant().shape;
                if (shape != CarVariant.Shape.SPORTS && shape != CarVariant.Shape.SUPER) {
                    player.sendOverlayMessage(Component.literal("Der Schrotthändler will nur Sportwagen!")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                if (car.isOwnedBy(player)) {
                    player.sendOverlayMessage(Component.literal("Das ist dein eigenes Auto - das nimmt hier keiner.")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                player.stopRiding();
                car.despawn();
                pay(player, job, shape == CarVariant.Shape.SUPER ? 15000 : 6000, "Auto verkauft");
            }
        }
        job.step++;
        if (job.step >= job.targets.size()) {
            ACTIVE.remove(player.getUUID());
            player.removeAttached(ModAttachments.MISSION);
            WantedSystem.title(player, Component.literal("JOB ERLEDIGT").withStyle(ChatFormatting.GOLD),
                    Component.literal("Verdient: " + Economy.format(job.earned)).withStyle(ChatFormatting.GREEN));
            return;
        }
        job.deadline = level.getGameTime() + LEG_TIME;
        updateMission(player, job);
    }

    private static void pay(ServerPlayer player, Job job, int amount, String what) {
        Economy.add(player, amount);
        job.earned += amount;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASH,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendOverlayMessage(Component.literal(what + ": +" + Economy.format(amount))
                .withStyle(ChatFormatting.GREEN));
    }

    // ------------------------------------------------------------------ street crime

    /** Sneak up behind a pedestrian and right click with an empty hand. */
    public static boolean pickpocket(ServerPlayer player, NpcEntity npc) {
        if (npc instanceof PoliceEntity || npc.isPassenger() || npc.isPanicking()) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Long last = PICKPOCKETED.get(npc.getUUID());
        if (last != null && now - last < 20L * 60 * 3) {
            player.sendOverlayMessage(Component.literal("Der hat nichts mehr in der Tasche.")
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }
        PICKPOCKETED.put(npc.getUUID(), now);
        Vec3 look = npc.getViewVector(1.0F).multiply(1, 0, 1).normalize();
        Vec3 toPlayer = player.position().subtract(npc.position()).multiply(1, 0, 1).normalize();
        boolean behind = look.dot(toPlayer) < -0.2;
        float noticeChance = behind ? 0.25F : 0.8F;
        int loot = 20 + level.getRandom().nextInt(npc.isGang() ? 300 : 130);
        Economy.add(player, loot);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASH, SoundSource.PLAYERS,
                0.5F, 1.3F);
        if (level.getRandom().nextFloat() < noticeChance) {
            npc.panic(player.position(), 160);
            player.sendOverlayMessage(Component.literal("Erwischt! +" + Economy.format(loot) + " - \"Haltet den Dieb!\"")
                    .withStyle(ChatFormatting.RED));
            if (WantedSystem.copSees(player, 40)) {
                WantedSystem.commit(player, 1);
            }
        } else {
            player.sendOverlayMessage(Component.literal("Geldbörse geklaut: +" + Economy.format(loot))
                    .withStyle(ChatFormatting.GREEN));
        }
        return true;
    }

    /** Sneak + right click on the 24/7 counter with an empty hand: grab a few things and run. */
    public static void shoplift(ServerPlayer player, BlockPos counter) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Long until = SHOPLIFTED.get(counter);
        if (until != null && until > now) {
            player.sendOverlayMessage(Component.literal("Der Verkäufer lässt dich nicht aus den Augen.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        SHOPLIFTED.put(counter.immutable(), now + 20L * 90);
        RandomSource random = level.getRandom();
        Item[] loot = {ModItems.BURGER, ModItems.ECOLA, ModItems.CANDY_BAR, ModItems.MEDKIT};
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            ItemStack stack = new ItemStack(loot[random.nextInt(loot.length)]);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, Prediction.SERVER_ONLY);
            }
        }
        if (random.nextFloat() < 0.35F) {
            level.playSound(null, counter, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.5F);
            WantedSystem.commit(player, 1);
            player.sendOverlayMessage(Component.literal("Ladendiebstahl bemerkt - der Alarm geht los!")
                    .withStyle(ChatFormatting.RED));
        } else {
            player.sendOverlayMessage(Component.literal("Unbemerkt eingesteckt: " + count + " Sachen")
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
