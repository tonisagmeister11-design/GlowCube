package de.gtacity.gameplay;

import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.entity.NpcEntity;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.network.Payloads;
import de.gtacity.registry.ModAttachments;
import de.gtacity.registry.ModEntities;
import de.gtacity.registry.ModItems;
import de.gtacity.registry.ModSounds;
import de.gtacity.world.CityLayout;
import de.gtacity.world.CityMap;
import de.gtacity.world.CityPlaces;
import de.gtacity.world.Lot;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * Work in Los Santos. Jobs are taken at the job stations (the clerk behind the desk, or the desk itself):
 * the legal ones at the job centre, the dirty ones at the harbour office. A job is a list of steps - drive here,
 * pick up a passenger, bring him there, take somebody out ... - each with a pay cheque, a map marker with GPS and often
 * a time limit. On top of the repeatable jobs there is a story in five chapters, told by the clerks.
 */
public final class Jobs {
    private Jobs() {
    }

    public enum Station {
        JOBCENTER("Jobcenter", "Marco", false),
        SHADY("Hafenbüro", "Tony", true);

        public final String label;
        public final String clerk;
        public final boolean illegal;

        Station(String label, String clerk, boolean illegal) {
            this.label = label;
            this.clerk = clerk;
            this.illegal = illegal;
        }
    }

    public enum Type {
        COURIER("Kurierfahrer", Station.JOBCENTER, "Drei Pakete zu Kunden in der Stadt bringen. Schnelle Zustellung "
                + "bringt Trinkgeld.", "ca. $1.500"),
        TAXI("Taxifahrer", Station.JOBCENTER, "Zwei Fahrgäste abholen und ans Ziel fahren. Du brauchst ein Auto "
                + "(F zum Einsteigen).", "ca. $1.300"),
        AMBULANCE("Krankentransport", Station.JOBCENTER, "Einen Verletzten abholen und ins nächste Krankenhaus "
                + "bringen. Schnell, aber vorsichtig!", "ca. $2.200"),
        BOUNTY("Kopfgeldjäger", Station.JOBCENTER, "Ein gesuchter Verbrecher versteckt sich in der Stadt. "
                + "Spür ihn auf (er leuchtet) und schalte ihn aus.", "$3.500"),
        GUN_RUNNING("Waffendealer", Station.SHADY, "Eine Kiste Waffen im Hafen abholen und dem Käufer bringen. "
                + "Die Polizei darf dich bei der Übergabe nicht verfolgen.", "$7.000 - $9.500"),
        CAR_THEFT("Autodieb", Station.SHADY, "Einen Sportwagen oder Supersportwagen klauen und zum Schrottplatz "
                + "im Hafen bringen.", "$6.000 / $15.000");

        public final String label;
        public final Station station;
        public final String description;
        public final String pay;

        Type(String label, Station station, String description, String pay) {
            this.label = label;
            this.station = station;
            this.description = description;
            this.pay = pay;
        }

        public boolean illegal() {
            return station.illegal;
        }
    }

    public static final String[] CHAPTER_TITLES = {"Führung durch Los Santos", "Der erste Lohn", "Fahrgäste",
            "Schattengeschäfte", "Der große Coup"};
    public static final Station[] CHAPTER_STATION = {Station.JOBCENTER, Station.JOBCENTER, Station.JOBCENTER,
            Station.SHADY, Station.SHADY};
    public static final String[] CHAPTER_TEXT = {
            "Marco zeigt dir alles: Supermarkt, Waffenladen, Autohaus, Krankenhaus, Polizei und die Bank.",
            "Zwei Pakete ausliefern - dein erster ehrlicher Lohn.",
            "Ein Fahrgast will quer durch die Stadt. Du brauchst ein Auto.",
            "Tony vom Hafenbüro hat eine Kiste, die nie jemand gesehen haben darf.",
            "Kauf einen Thermobohrer, knack den Banktresor und hau ab. Der Coup deines Lebens."};
    private static final int[] CHAPTER_REWARD = {1500, 2500, 3500, 10000, 30000};

    public static final int MAX_CHAPTER = CHAPTER_TITLES.length;

    // ------------------------------------------------------------------ data

    private enum Goal {
        REACH, PICKUP, DROPOFF, KILL, HAVE_ITEM, HEIST, STEAL_CAR, CLEAR
    }

    private static final class Step {
        final Goal goal;
        final BlockPos pos;
        final String label;
        int pay;
        int seconds;
        String say;
        Item item;
        int wantedStars;
        String npcName;

        Step(Goal goal, BlockPos pos, String label) {
            this.goal = goal;
            this.pos = pos;
            this.label = label;
        }

        Step pay(int pay) {
            this.pay = pay;
            return this;
        }

        Step seconds(int seconds) {
            this.seconds = seconds;
            return this;
        }

        Step say(String say) {
            this.say = say;
            return this;
        }

        Step item(Item item) {
            this.item = item;
            return this;
        }

        Step wanted(int stars) {
            this.wantedStars = stars;
            return this;
        }

        Step npc(String name) {
            this.npcName = name;
            return this;
        }
    }

    private static final class Job {
        final Type type;
        final Station station;
        final int chapter;
        final List<Step> steps = new ArrayList<>();
        int index;
        long deadline;
        long earned;
        boolean spawned;
        boolean boarded;
        boolean heistDone;
        long lastHint;
        UUID npc;

        Job(Type type, Station station, int chapter) {
            this.type = type;
            this.station = station;
            this.chapter = chapter;
        }

        Step step() {
            return steps.get(index);
        }

        String title() {
            return chapter > 0 ? "Kapitel " + chapter + ": " + CHAPTER_TITLES[chapter - 1] : type.label;
        }
    }

    private static final Map<UUID, Job> ACTIVE = new HashMap<>();
    /** Which job board a player has open (a start request is only valid for the board they are looking at). */
    private static final Map<UUID, long[]> BOARD = new HashMap<>();
    private static final Map<UUID, Long> PICKPOCKETED = new HashMap<>();
    private static final Map<BlockPos, Long> SHOPLIFTED = new HashMap<>();
    private static final String[] PASSENGERS = {"Herr Weber", "Frau Kaya", "Lukas", "Mia", "Herr Okafor", "Frau Ivanova",
            "Paul", "Nina"};
    private static final String[] WANTED = {"Vinnie \"Messer\"", "Dutch", "Big Mo", "Slick Ray", "Cortez"};

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    // ------------------------------------------------------------------ rank

    public static int done(net.minecraft.world.entity.player.Player player) {
        Integer d = player.getAttached(ModAttachments.JOBS_DONE);
        return d == null ? 0 : d;
    }

    public static String rank(int done) {
        return done >= 30 ? "Legende" : done >= 15 ? "Veteran" : done >= 5 ? "Profi" : "Neuling";
    }

    /** +3 % pay per finished job, at most +50 %. */
    private static double bonus(net.minecraft.world.entity.player.Player player) {
        return 1.0 + Math.min(0.5, done(player) * 0.03);
    }

    public static int chapter(net.minecraft.world.entity.player.Player player) {
        Integer c = player.getAttached(ModAttachments.STORY);
        return c == null ? 0 : c;
    }

    // ------------------------------------------------------------------ board

    public static void openBoard(ServerPlayer player, Station station) {
        BOARD.put(player.getUUID(), new long[]{station.ordinal(), player.level().getGameTime()});
        ServerPlayNetworking.send(player, new Payloads.OpenJobs(station.ordinal()));
    }

    private static boolean boardOpen(ServerPlayer player, Station station) {
        long[] b = BOARD.get(player.getUUID());
        return b != null && b[0] == station.ordinal() && player.level().getGameTime() - b[1] < 20L * 60 * 5;
    }

    private static void say(ServerPlayer player, Station station, String text) {
        player.sendSystemMessage(Component.literal("[" + station.clerk + "] ").withStyle(
                station.illegal ? ChatFormatting.RED : ChatFormatting.AQUA).append(
                Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }

    // ------------------------------------------------------------------ start / stop

    public static void start(ServerPlayer player, Type type) {
        if (!boardOpen(player, type.station)) {
            player.sendOverlayMessage(Component.literal("Sprich zuerst mit dem Mitarbeiter im "
                    + type.station.label + ".").withStyle(ChatFormatting.RED));
            return;
        }
        begin(player, new Job(type, type.station, 0));
    }

    public static void startStory(ServerPlayer player, int chapter) {
        if (chapter < 1 || chapter > MAX_CHAPTER) {
            return;
        }
        Station station = CHAPTER_STATION[chapter - 1];
        if (!boardOpen(player, station)) {
            player.sendOverlayMessage(Component.literal("Sprich zuerst mit dem Mitarbeiter im "
                    + station.label + ".").withStyle(ChatFormatting.RED));
            return;
        }
        if (chapter != chapter(player) + 1) {
            player.sendOverlayMessage(Component.literal(chapter <= chapter(player) ? "Dieses Kapitel hast du schon "
                    + "geschafft." : "Erst die vorigen Kapitel abschließen.").withStyle(ChatFormatting.RED));
            return;
        }
        Type type = switch (chapter) {
            case 1 -> Type.COURIER;
            case 2 -> Type.COURIER;
            case 3 -> Type.TAXI;
            case 4 -> Type.GUN_RUNNING;
            default -> Type.CAR_THEFT;
        };
        begin(player, new Job(type, station, chapter));
    }

    private static void begin(ServerPlayer player, Job job) {
        if (ACTIVE.containsKey(player.getUUID())) {
            player.sendOverlayMessage(Component.literal("Du hast schon einen Job. Brich ihn erst ab (Karte, Tab Jobs).")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        build(level, player, job);
        if (job.steps.isEmpty()) {
            return;
        }
        ACTIVE.put(player.getUUID(), job);
        enter(level, player, job);
        say(player, job.station, intro(job));
        player.sendSystemMessage(Component.literal("Job angenommen: " + job.title() + " - das Ziel ist auf Karte und "
                + "Radar markiert (Navi).").withStyle(job.station.illegal ? ChatFormatting.RED : ChatFormatting.AQUA));
    }

    private static String intro(Job job) {
        if (job.chapter > 0) {
            return switch (job.chapter) {
                case 1 -> "Willkommen in Los Santos! Ich zeige dir alles. Folge einfach den Markierungen - "
                        + "ich erkläre dir an jeder Station, was dort los ist.";
                case 2 -> "Zeit für deinen ersten ehrlichen Lohn. Zwei Pakete, zwei Kunden. Los!";
                case 3 -> "Ein Fahrgast wartet. Schnapp dir ein Auto (F zum Einsteigen) und fahr ihn ans Ziel.";
                case 4 -> "Du willst mehr Geld? Dann hör zu: im Hafen liegt eine Kiste. Bring sie zum Käufer - "
                        + "und lass dich nicht von den Bullen erwischen.";
                default -> "Der große Coup. Erst der Thermobohrer bei Ammu-Nation, dann die Bank. Danach musst du "
                        + "die Polizei abhängen. Kein Fehler, verstanden?";
            };
        }
        return switch (job.type) {
            case COURIER -> "Drei Pakete. Nimm ein Auto, dann bist du schnell - Trinkgeld gibt's für flotte Zustellung.";
            case TAXI -> "Zwei Fahrgäste. Fahr zum Startpunkt, dann nimmt dich der Fahrgast mit. Fahr vorsichtig.";
            case AMBULANCE -> "Ein Verletzter! Hol ihn ab und bring ihn ins Krankenhaus. Jede Minute zählt.";
            case BOUNTY -> "Der Gesuchte leuchtet auf der Karte. Er wehrt sich, also sei bewaffnet.";
            case GUN_RUNNING -> "Die Kiste liegt im Hafen. Beim Abholen kann jemand die Polizei rufen.";
            case CAR_THEFT -> "Ich brauche einen Sportwagen. Klau einen und bring ihn zum Schrottplatz. "
                    + "Dein eigenes Auto nehme ich nicht.";
        };
    }

    public static void cancel(ServerPlayer player, String reason) {
        Job job = ACTIVE.remove(player.getUUID());
        player.removeAttached(ModAttachments.MISSION);
        if (job != null) {
            cleanup((ServerLevel) player.level(), job);
            player.sendSystemMessage(Component.literal("Job beendet: " + reason).withStyle(ChatFormatting.GRAY));
        }
    }

    public static void forget(ServerPlayer player) {
        Job job = ACTIVE.remove(player.getUUID());
        if (job != null) {
            cleanup((ServerLevel) player.level(), job);
        }
        BOARD.remove(player.getUUID());
    }

    private static void cleanup(ServerLevel level, Job job) {
        if (job.npc != null && level.getEntity(job.npc) instanceof NpcEntity npc) {
            npc.stopRiding();
            npc.discard();
        }
        job.npc = null;
    }

    // ------------------------------------------------------------------ building the steps

    private static void build(ServerLevel level, ServerPlayer player, Job job) {
        RandomSource random = level.getRandom();
        BlockPos from = player.blockPosition();
        Set<CityLayout.LotType> homes = EnumSet.of(CityLayout.LotType.HOUSE, CityLayout.LotType.VILLA,
                CityLayout.LotType.OFFICE, CityLayout.LotType.SKYSCRAPER, CityLayout.LotType.STORE,
                CityLayout.LotType.GAS_STATION);
        switch (job.chapter) {
            case 1 -> {
                tour(job, from);
                return;
            }
            case 5 -> {
                heist(job, from);
                return;
            }
            default -> {
            }
        }
        switch (job.type) {
            case COURIER -> {
                int stops = job.chapter == 2 ? 2 : 3;
                BlockPos last = from;
                for (int i = 1; i <= stops; i++) {
                    BlockPos to = randomAddress(random, last, 250, 650, homes);
                    double dist = flat(last, to);
                    job.steps.add(new Step(Goal.REACH, to, "Paket " + i + "/" + stops + " zustellen")
                            .pay((int) (250 + dist * 0.7)).seconds((int) Math.max(100, 60 + dist / 5))
                            .say("Danke fürs Bringen!"));
                    last = to;
                }
            }
            case TAXI -> {
                int rides = job.chapter == 3 ? 1 : 2;
                BlockPos last = from;
                for (int i = 1; i <= rides; i++) {
                    String name = PASSENGERS[random.nextInt(PASSENGERS.length)];
                    BlockPos pickup = randomAddress(random, last, 120, 420, homes);
                    BlockPos drop = randomAddress(random, pickup, 500, 1300, homes);
                    double dist = flat(pickup, drop);
                    job.steps.add(new Step(Goal.PICKUP, pickup, "Fahrgast " + name + " abholen (im Auto!)")
                            .seconds(300).npc(name).say(name + ": Zum Flughafen... äh, einfach zum markierten Ziel!"));
                    job.steps.add(new Step(Goal.DROPOFF, drop, "Fahrgast " + name + " zum Ziel bringen")
                            .pay((int) (250 + dist * 1.5)).seconds((int) (60 + dist / 7)).npc(name)
                            .say(name + ": Stimmt so, danke!"));
                    last = drop;
                }
            }
            case AMBULANCE -> {
                BlockPos victim = randomAddress(random, from, 150, 500, homes);
                CityMap.Place hospital = CityMap.nearest(CityMap.Kind.HOSPITAL, victim.getX(), victim.getZ());
                BlockPos to = hospital == null ? CityPlaces.spawn() : hospital.entrance();
                double dist = flat(victim, to);
                job.steps.add(new Step(Goal.PICKUP, victim, "Verletzten abholen (im Auto!)").seconds(240)
                        .npc("Verletzter").say("Verletzter: Bitte... schnell ins Krankenhaus!"));
                job.steps.add(new Step(Goal.DROPOFF, to, "Verletzten ins Krankenhaus bringen")
                        .pay((int) (1000 + dist * 2.0)).seconds((int) (50 + dist / 7)).npc("Verletzter")
                        .say("Der Arzt: Gut gemacht - du hast ein Leben gerettet!"));
            }
            case BOUNTY -> {
                BlockPos target = randomAddress(random, from, 250, 600, homes);
                String name = WANTED[random.nextInt(WANTED.length)];
                job.steps.add(new Step(Goal.KILL, target, "Gesuchten ausschalten: " + name).pay(3500).seconds(900)
                        .npc(name).say("Kopfgeld kassiert. Die Stadt ist ein Stück sicherer."));
            }
            case GUN_RUNNING -> {
                BlockPos crate = randomAddress(random, from, 0, 5000, EnumSet.of(CityLayout.LotType.CONTAINERS),
                        CityLayout.District.INDUSTRIAL);
                BlockPos buyer = randomAddress(random, crate, 700, 1600, EnumSet.of(CityLayout.LotType.HOUSE,
                        CityLayout.LotType.WAREHOUSE, CityLayout.LotType.PARKING));
                job.steps.add(new Step(Goal.REACH, crate, "Waffenkiste im Hafen abholen").seconds(900)
                        .wanted(random.nextBoolean() ? 2 : 0));
                job.steps.add(new Step(Goal.CLEAR, buyer, "Waffen dem Käufer bringen (ohne Fahndung!)")
                        .pay(7000 + random.nextInt(2501)).seconds(600).say("Käufer: Saubere Arbeit."));
            }
            case CAR_THEFT -> {
                BlockPos yard = randomAddress(random, from, 0, 5000, EnumSet.of(CityLayout.LotType.CONTAINERS),
                        CityLayout.District.INDUSTRIAL);
                job.steps.add(new Step(Goal.STEAL_CAR, yard, "Geklauten Sportwagen zum Schrottplatz bringen")
                        .seconds(1200));
            }
        }
        if (job.chapter == 4) {
            job.steps.getLast().say("Tony: Nicht schlecht. Ich rufe dich an, wenn ich einen richtigen Coup habe.");
        }
    }

    /** Chapter 1: the tour. Every stop has a few words from the clerk about the place. */
    private static void tour(Job job, BlockPos from) {
        Object[][] stops = {
                {CityMap.Kind.STORE, "Supermarkt 24/7", "Hier kaufst du Essen, Medikits und Schutzwesten. Sprich mit "
                        + "dem Verkäufer hinter der Kasse. Wer sich traut, kann ihn auch überfallen - "
                        + "schleichen und mit Waffe auf die Theke klicken."},
                {CityMap.Kind.AMMU_NATION, "Ammu-Nation", "Waffen gibt es NUR hier, nirgendwo sonst. Pistole, SMG, "
                        + "Karabiner, Pumpgun, Sniper, Minigun, Raketenwerfer - und den Thermobohrer für Tresore."},
                {CityMap.Kind.CAR_DEALER, "Autohaus", "Limousinen, SUVs, Sportwagen und der Supersportwagen 'Furia' mit "
                        + "300 km/h. Gekaufte Autos stehen in deiner Garage (Karte, M)."},
                {CityMap.Kind.HOSPITAL, "Krankenhaus", "Wenn du stirbst, wachst du hier auf und zahlst die Rechnung. "
                        + "Als Krankentransport-Fahrer bringst du hier Verletzte her."},
                {CityMap.Kind.POLICE, "Polizeirevier", "Wer zu viel Ärger macht, landet hier. Ab 1 Stern kommen "
                        + "Streifenwagen, ab 5 Sternen ein Hubschrauber."},
                {CityMap.Kind.BANK, "Bank", "Geldautomaten für deinen Kontostand, und hinter der Glaswand liegt der "
                        + "Tresor. Aber das ist eine andere Geschichte..."},
        };
        BlockPos last = from;
        for (int i = 0; i < stops.length; i++) {
            CityMap.Kind kind = (CityMap.Kind) stops[i][0];
            CityMap.Place place = CityMap.nearest(kind, last.getX(), last.getZ());
            if (place == null) {
                continue;
            }
            last = place.entrance();
            job.steps.add(new Step(Goal.REACH, last, "Führung " + (i + 1) + "/" + stops.length + ": "
                    + stops[i][1]).pay(100).say((String) stops[i][2]));
        }
        Step end = job.steps.getLast();
        end.say(end.say + " Das war die Führung. Komm zurück zum Jobcenter, ich habe Arbeit für dich.");
    }

    /** Chapter 5: the bank job. */
    private static void heist(Job job, BlockPos from) {
        CityMap.Place bank = CityMap.nearest(CityMap.Kind.BANK, from.getX(), from.getZ());
        CityMap.Place hideout = CityMap.nearest(CityMap.Kind.DOCKS, from.getX(), from.getZ());
        BlockPos ammu = CityMap.nearest(CityMap.Kind.AMMU_NATION, from.getX(), from.getZ()).entrance();
        job.steps.add(new Step(Goal.HAVE_ITEM, ammu, "Thermobohrer bei Ammu-Nation kaufen ($2.500)")
                .item(ModItems.THERMAL_DRILL).seconds(0)
                .say("Tony: Gut, das Werkzeug hast du. Jetzt die Bank."));
        job.steps.add(new Step(Goal.HEIST, bank.entrance(), "Bank: Tresor mit dem Bohrer aufbrechen (30 s)")
                .say("Tony: Der Tresor ist offen! Verschwinde, bevor die Bullen da sind!"));
        job.steps.add(new Step(Goal.CLEAR, hideout == null ? from : hideout.entrance(),
                "Zum Hafenbüro und die Polizei abhängen").seconds(1200)
                .say("Tony: Du hast es geschafft. Los Santos gehört dir."));
    }

    private static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Sidewalk in front of a random lot of the given types, between min and max blocks away from {@code from}. */
    private static BlockPos randomAddress(RandomSource random, BlockPos from, int min, int max,
                                          Set<CityLayout.LotType> types, CityLayout.District... districts) {
        BlockPos fallback = null;
        for (int attempt = 0; attempt < 500; attempt++) {
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
            double d = flat(pos, from);
            if (fallback == null) {
                fallback = pos;
            }
            if (d >= min && d <= max) {
                return pos;
            }
        }
        return fallback != null ? fallback : CityPlaces.spawn();
    }

    // ------------------------------------------------------------------ steps

    private static void updateMission(ServerPlayer player, Job job) {
        Step s = job.step();
        BlockPos p = s.pos;
        if (s.goal == Goal.KILL && job.npc != null && player.level() instanceof ServerLevel level
                && level.getEntity(job.npc) != null) {
            p = level.getEntity(job.npc).blockPosition();
        }
        player.setAttached(ModAttachments.MISSION, new ModAttachments.Mission(s.label, p.getX(), p.getZ()));
    }

    /** Called when a step becomes the current one. */
    private static void enter(ServerLevel level, ServerPlayer player, Job job) {
        Step s = job.step();
        job.spawned = false;
        job.lastHint = 0;
        job.deadline = s.seconds > 0 ? level.getGameTime() + s.seconds * 20L : Long.MAX_VALUE;
        updateMission(player, job);
    }

    /** Step done: pay, tell the story bit, go on to the next one - or finish the job. */
    private static void advance(ServerLevel level, ServerPlayer player, Job job, int extra) {
        Step s = job.step();
        int pay = (int) Math.round((s.pay + extra) * bonus(player));
        if (pay > 0) {
            Economy.add(player, pay);
            job.earned += pay;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASH,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.literal(s.label.split(":")[0] + " erledigt: +" + Economy.format(pay))
                    .withStyle(ChatFormatting.GREEN));
        }
        if (s.say != null) {
            player.sendSystemMessage(Component.literal(s.say).withStyle(job.station.illegal ? ChatFormatting.RED
                    : ChatFormatting.AQUA));
        }
        if (s.wantedStars > 0) {
            WantedSystem.commit(player, s.wantedStars);
            player.sendSystemMessage(Component.literal("Ein Zeuge hat die Polizei gerufen!")
                    .withStyle(ChatFormatting.RED));
        }
        job.index++;
        job.boarded = false;
        if (job.index >= job.steps.size()) {
            finish(level, player, job);
        } else {
            enter(level, player, job);
        }
    }

    private static void finish(ServerLevel level, ServerPlayer player, Job job) {
        ACTIVE.remove(player.getUUID());
        player.removeAttached(ModAttachments.MISSION);
        cleanup(level, job);
        player.setAttached(ModAttachments.JOBS_DONE, done(player) + 1);
        long total = job.earned;
        if (job.chapter > 0) {
            int reward = (int) Math.round(CHAPTER_REWARD[job.chapter - 1] * bonus(player));
            Economy.add(player, reward);
            total += reward;
            player.setAttached(ModAttachments.STORY, job.chapter);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASH,
                SoundSource.PLAYERS, 1.0F, 0.8F);
        WantedSystem.title(player, Component.literal(job.chapter > 0 ? "KAPITEL " + job.chapter + " GESCHAFFT"
                : "JOB ERLEDIGT").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("Verdient: " + Economy.format(total) + "  -  Rang: " + rank(done(player)))
                        .withStyle(ChatFormatting.GREEN));
        if (job.chapter > 0 && job.chapter < MAX_CHAPTER) {
            Station next = CHAPTER_STATION[job.chapter];
            player.sendSystemMessage(Component.literal("Nächstes Kapitel: " + CHAPTER_TITLES[job.chapter] + " - im "
                    + next.label + " (auf der Karte).").withStyle(ChatFormatting.GOLD));
        } else if (job.chapter == MAX_CHAPTER) {
            player.sendSystemMessage(Component.literal("Die Story ist durch - du bist der Boss von Los Santos. "
                    + "Kauf dir eine Villa und einen Supersportwagen, oder arbeite weiter für mehr Rang.")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static void fail(ServerPlayer player, String reason) {
        cancel(player, reason);
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
                fail(player, "Du bist gestorben.");
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            Step s = job.step();
            if (level.getGameTime() > job.deadline) {
                fail(player, "Zu spät!");
                continue;
            }
            double dx = player.getX() - (s.pos.getX() + 0.5), dz = player.getZ() - (s.pos.getZ() + 0.5);
            double dist = Math.sqrt(dx * dx + dz * dz);
            boolean inCar = player.getVehicle() instanceof CarEntity car && car.getControllingPassenger() == player;
            double reach = inCar ? 14.0 : 7.0;
            spawnLazily(level, player, job, dist);
            long now = level.getGameTime();
            switch (s.goal) {
                case REACH -> {
                    if (dist <= reach) {
                        advance(level, player, job, timeBonus(job, level));
                    }
                }
                case PICKUP -> {
                    if (dist <= 10.0) {
                        pickup(level, player, job, s, inCar, now);
                    }
                }
                case DROPOFF -> {
                    if (!dropoffAlive(level, player, job)) {
                        continue;
                    }
                    NpcEntity rider = level.getEntity(job.npc) instanceof NpcEntity n ? n : null;
                    boolean aboard = rider != null && rider.getVehicle() == player.getVehicle();
                    if (dist <= 12.0 && inCar && aboard) {
                        NpcEntity npc = rider;
                        int tip = player.getVehicle() instanceof CarEntity car && car.healthFraction() > 0.8F
                                ? (int) (s.pay * 0.2) : 0;
                        if (npc != null) {
                            npc.stopRiding();
                            npc.discard();
                        }
                        job.npc = null;
                        advance(level, player, job, tip + timeBonus(job, level));
                    }
                }
                case KILL -> killCheck(level, player, job, s);
                case HAVE_ITEM -> {
                    if (player.getInventory().contains(new ItemStack(s.item))) {
                        advance(level, player, job, 0);
                    } else if (now - job.lastHint > 20 * 20) {
                        job.lastHint = now;
                        player.sendOverlayMessage(Component.literal("Dir fehlt: Thermobohrer (Ammu-Nation, $2.500)")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                }
                case HEIST -> {
                    if (job.heistDone) {
                        advance(level, player, job, 0);
                    }
                }
                case STEAL_CAR -> {
                    if (dist <= 16.0) {
                        sellCar(level, player, job, s, inCar, now);
                    }
                }
                case CLEAR -> {
                    if (dist <= reach) {
                        if (WantedSystem.level(player) > 0) {
                            if (now - job.lastHint > 20 * 15) {
                                job.lastHint = now;
                                player.sendOverlayMessage(Component.literal("Erst die Polizei abhängen - "
                                        + "sonst nimmt dich keiner an!").withStyle(ChatFormatting.RED));
                            }
                        } else {
                            advance(level, player, job, timeBonus(job, level));
                        }
                    }
                }
            }
            if (ACTIVE.get(id) == job && (s.goal == Goal.KILL) && tickCountEvery(server, 40)) {
                updateMission(player, job);
            }
        }
    }

    private static boolean tickCountEvery(MinecraftServer server, int n) {
        return server.getTickCount() % n == 0;
    }

    /** Quick job = bonus: more than half of the time left at the end of a courier / taxi leg pays 25 % extra. */
    private static int timeBonus(Job job, ServerLevel level) {
        Step s = job.step();
        if (s.seconds <= 0 || s.pay <= 0) {
            return 0;
        }
        long left = job.deadline - level.getGameTime();
        return left > s.seconds * 10L ? (int) (s.pay * 0.25) : 0;
    }

    /** Passengers and bounty targets appear when the player gets close (the chunks around are loaded then). */
    private static void spawnLazily(ServerLevel level, ServerPlayer player, Job job, double dist) {
        Step s = job.step();
        if (job.spawned || dist > 90.0 || !(s.goal == Goal.PICKUP || s.goal == Goal.KILL)) {
            return;
        }
        if (!level.hasChunkAt(s.pos) || !level.isPositionEntityTicking(s.pos)) {
            return;
        }
        job.spawned = true;
        NpcEntity npc = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.EVENT);
        if (npc == null) {
            return;
        }
        boolean bounty = s.goal == Goal.KILL;
        npc.snapTo(s.pos.getX() + 0.5, s.pos.getY(), s.pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        npc.randomizeLook(bounty);
        npc.setRole(bounty ? "bounty" : "passenger", (bounty ? "Gesucht: " : "") + (s.npcName == null ? "?" : s.npcName));
        if (bounty) {
            npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
            npc.setHealth(40.0F);
            npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.BASEBALL_BAT));
            npc.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 20, 0, false, false));
        } else {
            npc.setNoAi(true);
        }
        level.addFreshEntity(npc);
        job.npc = npc.getUUID();
        updateMission(player, job);
    }

    private static void pickup(ServerLevel level, ServerPlayer player, Job job, Step s, boolean inCar, long now) {
        if (job.npc == null || !(level.getEntity(job.npc) instanceof NpcEntity npc)) {
            return;
        }
        if (!inCar) {
            if (now - job.lastHint > 20 * 8) {
                job.lastHint = now;
                player.sendOverlayMessage(Component.literal("Steig in ein Auto (F), damit " + s.npcName
                        + " einsteigen kann!").withStyle(ChatFormatting.YELLOW));
            }
            return;
        }
        if (npc.startRiding(player.getVehicle(), true, false)) {
            job.boarded = true;
            advance(level, player, job, 0);
        } else if (now - job.lastHint > 20 * 8) {
            job.lastHint = now;
            player.sendOverlayMessage(Component.literal("Im Auto ist kein Platz frei.").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * The passenger must survive the ride. If he is out of the car (crash, the player left him somewhere), he gets
     * back in when the player stops next to him with a car.
     */
    private static boolean dropoffAlive(ServerLevel level, ServerPlayer player, Job job) {
        Entity e = job.npc == null ? null : level.getEntity(job.npc);
        if (e instanceof NpcEntity npc && npc.isAlive()) {
            if (npc.getVehicle() == null && player.getVehicle() instanceof CarEntity car
                    && car.getControllingPassenger() == player && npc.distanceTo(player) < 8.0) {
                npc.startRiding(car, true, false);
            }
            return true;
        }
        if (e == null && !level.hasChunkAt(job.step().pos)) {
            return true; // not loaded right now
        }
        fail(player, "Dein Fahrgast ist verletzt oder weg.");
        return false;
    }

    private static void killCheck(ServerLevel level, ServerPlayer player, Job job, Step s) {
        if (!job.spawned || job.npc == null) {
            return;
        }
        Entity e = level.getEntity(job.npc);
        if (e == null) {
            // Gone: dead, if the chunk he stood in is loaded (else he is just out of range).
            if (level.hasChunkAt(s.pos)) {
                job.npc = null;
                advance(level, player, job, 0);
            }
            return;
        }
        if (!e.isAlive()) {
            job.npc = null;
            advance(level, player, job, 0);
        }
    }

    private static void sellCar(ServerLevel level, ServerPlayer player, Job job, Step s, boolean inCar, long now) {
        String problem = null;
        if (!inCar) {
            problem = "Du musst mit einem geklauten Sportwagen kommen.";
        } else {
            CarEntity car = (CarEntity) player.getVehicle();
            CarVariant.Shape shape = car.getVariant().shape;
            if (shape != CarVariant.Shape.SPORTS && shape != CarVariant.Shape.SUPER) {
                problem = "Der Schrotthändler will nur Sportwagen!";
            } else if (car.isOwnedBy(player)) {
                problem = "Das ist dein eigenes Auto - das nimmt hier keiner.";
            } else {
                player.stopRiding();
                car.despawn();
                s.pay(shape == CarVariant.Shape.SUPER ? 15000 : 6000);
                s.say("Schrotthändler: Feiner Wagen. Hier dein Geld, und du warst nie hier.");
                advance(level, player, job, 0);
                return;
            }
        }
        if (now - job.lastHint > 20 * 10) {
            job.lastHint = now;
            player.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
        }
    }

    /** The bank vault was drilled open: chapter 5 goes on. */
    public static void onVaultOpened(ServerPlayer player) {
        Job job = ACTIVE.get(player.getUUID());
        if (job != null && job.chapter == 5 && job.step().goal == Goal.HEIST) {
            job.heistDone = true;
        }
    }

    // ------------------------------------------------------------------ street crime

    /** Sneak up behind a pedestrian and right click with an empty hand. */
    public static boolean pickpocket(ServerPlayer player, NpcEntity npc) {
        if (npc instanceof PoliceEntity || !npc.role().isEmpty() || npc.isPassenger() || npc.isPanicking()) {
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
