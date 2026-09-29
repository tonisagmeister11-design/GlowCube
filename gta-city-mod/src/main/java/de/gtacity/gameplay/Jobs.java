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
import java.util.HashSet;
import java.util.LinkedHashSet;
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
                + "im Hafen bringen.", "$6.000 / $15.000"),
        GANG_WAR("Bandenkrieg", Station.JOBCENTER, "Die Polizei zahlt: räum ein Gangversteck aus. Gangster, "
                + "die sich wehren - allein schwer, im Team leichter.", "$6.000"),
        STREET_RACE("Straßenrennen", Station.SHADY, "Mit dem Auto durch die Checkpoints quer durch die Stadt, "
                + "gegen die Uhr. Im Team: wer zuerst ankommt, kassiert den Siegerbonus.", "$3.000 + Bonus"),
        GANG_CAR("Gang-Auto klauen", Station.SHADY, "Eine Gang hat einen teuren Wagen vor ihrem Versteck stehen. "
                + "Klau ihn und bring ihn zum Käufer - die Gang wird nicht begeistert sein.", "$9.000"),
        PROTECTION("Schutzgeld eintreiben", Station.SHADY, "Tony will sein Geld von den Läden. Klapper die "
                + "Geschäfte ab - manchmal ruft ein Verkäufer die Polizei.", "$800 pro Laden"),
        CREW_HEIST("Bankraub im Team", Station.SHADY, "Trefft euch vor der Bank, bohrt den Tresor auf "
                + "(Thermobohrer), hängt die Polizei ab - jeder bekommt den vollen Anteil.", "$20.000 pro Kopf",
                true);

        public final String label;
        public final Station station;
        public final String description;
        public final String pay;
        /** Only on the team list of the board (made for several players). */
        public final boolean teamOnly;

        Type(String label, Station station, String description, String pay) {
            this(label, station, description, pay, false);
        }

        Type(String label, Station station, String description, String pay, boolean teamOnly) {
            this.label = label;
            this.station = station;
            this.description = description;
            this.pay = pay;
            this.teamOnly = teamOnly;
        }

        /** Shown under "Team-Jobs": players in the world get asked to join. */
        public boolean team() {
            return switch (this) {
                case BOUNTY, GANG_WAR, GUN_RUNNING, STREET_RACE, CREW_HEIST, GANG_CAR -> true;
                default -> false;
            };
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
        REACH, PICKUP, DROPOFF, KILL, HAVE_ITEM, HEIST, STEAL_CAR, CLEAR, KILL_GROUP, CHECKPOINT, TOGETHER,
        GANG_CAR, DELIVER_CAR
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
        /** Where the guide points when the player arrives (the building). */
        BlockPos look;

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

        Step look(BlockPos look) {
            this.look = look;
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
        int missing;
        UUID npc;
        /** The gang's car (gang car theft) and whether the gang has seen the player yet. */
        UUID car;
        boolean spotted;
        /** Gang members of a gang war. */
        final List<UUID> group = new ArrayList<>();
        /** Everybody doing this job: the player who took it and, in a crew, the mates (partner mission). */
        final Set<UUID> crew = new LinkedHashSet<>();

        /** Difficulty: every "Weitermachen" is one level harder (more stops, less time, more money). */
        final int level;

        Job(Type type, Station station, int chapter) {
            this(type, station, chapter, 1);
        }

        Job(Type type, Station station, int chapter, int level) {
            this.type = type;
            this.station = station;
            this.chapter = chapter;
            this.level = Math.max(1, level);
        }

        Step step() {
            return steps.get(index);
        }

        String title() {
            return chapter > 0 ? "Kapitel " + chapter + ": " + CHAPTER_TITLES[chapter - 1]
                    : type.label + " (Stufe " + level + ")";
        }
    }

    private static final Map<UUID, Job> ACTIVE = new HashMap<>();

    /** The players (online) doing a job together. */
    private static List<ServerPlayer> members(MinecraftServer server, Job job) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : job.crew) {
            ServerPlayer p = Crew.find(server, id);
            if (p != null && ACTIVE.get(id) == job) {
                out.add(p);
            }
        }
        return out;
    }

    private static List<ServerPlayer> members(ServerPlayer player, Job job) {
        List<ServerPlayer> out = members(player.level().getServer(), job);
        if (out.isEmpty()) {
            out.add(player);
        }
        return out;
    }
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

    /**
     * Plays a recorded voice line to one player: gtacity:voice_&lt;clerk&gt;_&lt;key&gt;, e.g. voice_marco_courier. A line
     * whose sound file is not there yet (tools/import_voices.py) is simply silent - the text is in the chat anyway.
     */
    public static void voice(ServerPlayer player, String name) {
        if (player instanceof net.fabricmc.fabric.api.entity.FakePlayer) {
            return;
        }
        var event = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(de.gtacity.GtaCity.id("voice_" + name));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(event), SoundSource.VOICE, player.getX(), player.getEyeY(),
                player.getZ(), 1.0F, 1.0F, player.getRandom().nextLong()));
    }

    /** Voice line name of a job's intro: clerk + job, e.g. "marco_courier", "tony_chapter4". */
    private static String introVoice(Job job) {
        return job.station.clerk.toLowerCase(java.util.Locale.ROOT) + "_"
                + (job.chapter > 0 ? "chapter" + job.chapter : job.type.name().toLowerCase(java.util.Locale.ROOT));
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
        clerkShowsTheWay(player);
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
        STORY_PAUSED.remove(player.getUUID());
        begin(player, new Job(storyType(chapter), station, chapter));
        clerkShowsTheWay(player);
    }

    /** The clerk behind the desk points towards the first goal of the job that was just taken. */
    private static void clerkShowsTheWay(ServerPlayer player) {
        Job job = ACTIVE.get(player.getUUID());
        if (job == null) {
            return;
        }
        BlockPos goal = job.step().pos;
        for (NpcEntity npc : player.level().getEntitiesOfClass(NpcEntity.class, player.getBoundingBox().inflate(8.0),
                n -> "jobs".equals(n.role()) || "shady".equals(n.role()))) {
            npc.pointAt(goal.getX() + 0.5, goal.getZ() + 0.5);
        }
    }

    private static Type storyType(int chapter) {
        return switch (chapter) {
            case 1, 2 -> Type.COURIER;
            case 3 -> Type.TAXI;
            case 4 -> Type.GUN_RUNNING;
            default -> Type.CAR_THEFT;
        };
    }

    // ------------------------------------------------------------------ story autostart

    /** Off in the automated test, which starts its chapters itself. */
    public static boolean autoStory = true;
    /** When the next chapter calls the player (game time). */
    private static final Map<UUID, Long> STORY_DUE = new HashMap<>();
    /** Players who cancelled a chapter: no more calls until they join again. */
    private static final Set<UUID> STORY_PAUSED = new HashSet<>();

    /** The next chapter starts by itself after the delay: the clerk phones the player. */
    public static void scheduleStory(ServerPlayer player, int delayTicks) {
        if (chapter(player) < MAX_CHAPTER) {
            STORY_DUE.put(player.getUUID(), player.level().getGameTime() + delayTicks);
        }
    }

    private static void storyTick(MinecraftServer server) {
        if (!autoStory || STORY_DUE.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(STORY_DUE.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                STORY_DUE.remove(id);
                continue;
            }
            if (player.level().getGameTime() < STORY_DUE.get(id)) {
                continue;
            }
            if (!player.isAlive() || ACTIVE.containsKey(id)) {
                STORY_DUE.put(id, player.level().getGameTime() + 20L * 20); // try again later
                continue;
            }
            STORY_DUE.remove(id);
            int chapter = chapter(player) + 1;
            if (chapter > MAX_CHAPTER || STORY_PAUSED.contains(id)) {
                continue;
            }
            Station station = CHAPTER_STATION[chapter - 1];
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
            player.sendSystemMessage(Component.literal("Dein Handy klingelt - " + station.clerk + " ruft an.")
                    .withStyle(ChatFormatting.GOLD));
            WantedSystem.title(player, Component.literal("KAPITEL " + chapter).withStyle(ChatFormatting.GOLD,
                    ChatFormatting.BOLD), Component.literal(CHAPTER_TITLES[chapter - 1])
                    .withStyle(ChatFormatting.YELLOW));
            begin(player, new Job(storyType(chapter), station, chapter));
            if (ACTIVE.containsKey(id)) {
                player.sendSystemMessage(Component.literal("Keine Lust? Karte (M) → Jobs → Abbrechen. Später "
                        + "geht's im " + station.label + " weiter.").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void begin(ServerPlayer player, Job job) {
        begin(player, job, List.of());
    }

    /** {@code team}: players of the last job who come along when it is continued. */
    private static void begin(ServerPlayer player, Job job, java.util.Collection<UUID> team) {
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
        job.crew.add(player.getUUID());
        if (job.chapter == 0) {
            // Partner mission: the crew mates in the same world who are free join in.
            List<ServerPlayer> mates = new ArrayList<>(Crew.mates(player));
            for (UUID id : team) {
                ServerPlayer p = Crew.find(player.level().getServer(), id);
                if (p != null && p != player && !mates.contains(p)) {
                    mates.add(p);
                }
            }
            for (ServerPlayer mate : mates) {
                if (mate.level() == level && mate.isAlive() && !ACTIVE.containsKey(mate.getUUID())) {
                    ACTIVE.put(mate.getUUID(), job);
                    job.crew.add(mate.getUUID());
                }
            }
        }
        enter(level, player, job);
        for (ServerPlayer p : members(player, job)) {
            say(p, job.station, intro(job));
            voice(p, introVoice(job));
            p.sendSystemMessage(Component.literal("Job angenommen: " + job.title() + " - das Ziel ist auf Karte und "
                    + "Radar markiert (Navi).").withStyle(job.station.illegal ? ChatFormatting.RED
                    : ChatFormatting.AQUA));
            if (job.crew.size() > 1) {
                p.sendSystemMessage(Component.literal("Partnermission mit " + names(player.level().getServer(), job, p)
                        + " - jeder bekommt den vollen Lohn.").withStyle(ChatFormatting.GREEN));
            }
        }
    }

    private static String names(MinecraftServer server, Job job, ServerPlayer except) {
        return String.join(", ", members(server, job).stream().filter(p -> p != except)
                .map(p -> p.getName().getString()).toList());
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
            case GANG_WAR -> "Die Ballas haben sich in einem Hinterhof verschanzt. Vier Mann, bewaffnet. Die Polizei "
                    + "zahlt, wenn du aufräumst - und es gibt keine Sterne dafür.";
            case STREET_RACE -> "Fünf Checkpoints, die Uhr läuft. Du brauchst ein schnelles Auto - am Steuer zählt's.";
            case GANG_CAR -> "Vor dem Versteck der Ballas steht ein Wagen, den mein Kunde unbedingt will. Hol ihn dir. "
                    + "Wenn sie dich sehen, wird's hässlich - also sei schnell oder bewaffnet.";
            case PROTECTION -> "Die Läden in der Gegend zahlen mir Schutzgeld. Sie wissen es nur noch nicht. Geh hin, "
                    + "sag schöne Grüße von Tony und kassier ab.";
            case CREW_HEIST -> "Der große Coup, diesmal zu mehreren. Trefft euch vor der Bank. Einer von euch braucht "
                    + "einen Thermobohrer (Ammu-Nation). Danach: Bullen abhängen, ab zum Hafenbüro.";
        };
    }

    public static void cancel(ServerPlayer player, String reason) {
        Job job = ACTIVE.remove(player.getUUID());
        player.removeAttached(ModAttachments.MISSION);
        if (job != null && job.chapter > 0) {
            STORY_PAUSED.add(player.getUUID());
        }
        if (job != null) {
            leaveJob(player, job);
            player.sendSystemMessage(Component.literal("Job beendet: " + reason).withStyle(ChatFormatting.GRAY));
        }
    }

    /** One player is out of a job: the others of a partner mission go on, the last one cleans up. */
    private static void leaveJob(ServerPlayer player, Job job) {
        job.crew.remove(player.getUUID());
        List<ServerPlayer> rest = members(player.level().getServer(), job);
        if (rest.isEmpty()) {
            cleanup((ServerLevel) player.level(), job);
            return;
        }
        for (ServerPlayer p : rest) {
            p.sendSystemMessage(Component.literal(player.getName().getString() + " ist aus der Partnermission raus - "
                    + "ihr macht weiter.").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void forget(ServerPlayer player) {
        NEXT.remove(player.getUUID());
        TEAM_INVITES.remove(player.getUUID());
        STORY_DUE.remove(player.getUUID());
        STORY_PAUSED.remove(player.getUUID());
        Job job = ACTIVE.remove(player.getUUID());
        if (job != null) {
            leaveJob(player, job);
        }
        BOARD.remove(player.getUUID());
    }

    private static void cleanup(ServerLevel level, Job job) {
        if (job.npc != null && level.getEntity(job.npc) instanceof NpcEntity npc) {
            npc.stopRiding();
            npc.discard();
        }
        job.npc = null;
        for (UUID id : job.group) {
            if (level.getEntity(id) instanceof NpcEntity npc) {
                npc.discard();
            }
        }
        job.group.clear();
        if (job.car != null && level.getEntity(job.car) instanceof CarEntity car && car.getPassengers().isEmpty()) {
            car.despawn();
        }
        job.car = null;
    }

    // ------------------------------------------------------------------ continue / team jobs

    /** What "Weitermachen" starts: the same job one level up, with the same team. */
    private record Next(Type type, int level, Set<UUID> team, long time) {
    }

    private record TeamInvite(UUID from, Job job, long time) {
    }

    private static final Map<UUID, Next> NEXT = new HashMap<>();
    private static final Map<UUID, TeamInvite> TEAM_INVITES = new HashMap<>();
    private static final long CONTINUE_TICKS = 20L * 60 * 10;
    private static final long TEAM_INVITE_TICKS = 20L * 120;

    /** "Weitermachen" in the window after a job: the next order, one level harder. */
    public static void continueJob(ServerPlayer player) {
        Next next = NEXT.remove(player.getUUID());
        if (ACTIVE.containsKey(player.getUUID())) {
            player.sendOverlayMessage(Component.literal("Du bist schon im nächsten Auftrag.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (next == null || player.level().getGameTime() - next.time() > CONTINUE_TICKS) {
            player.sendOverlayMessage(Component.literal("Kein Auftrag zum Weitermachen - hol dir einen neuen am "
                    + "Job-Board.").withStyle(ChatFormatting.RED));
            return;
        }
        begin(player, new Job(next.type(), next.type().station, 0, next.level()), next.team());
        Job job = ACTIVE.get(player.getUUID());
        if (job != null) {
            for (UUID id : job.crew) {
                NEXT.remove(id);
            }
        }
    }

    /** Team job from the board: starts it and asks every other player in the world to join. */
    public static void startTeam(ServerPlayer player, Type type) {
        if (!type.team()) {
            return;
        }
        if (!boardOpen(player, type.station)) {
            player.sendOverlayMessage(Component.literal("Sprich zuerst mit dem Mitarbeiter im "
                    + type.station.label + ".").withStyle(ChatFormatting.RED));
            return;
        }
        begin(player, new Job(type, type.station, 0));
        Job job = ACTIVE.get(player.getUUID());
        if (job == null || job.type != type) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        int asked = 0;
        for (ServerPlayer other : List.copyOf(level.players())) {
            if (other == player || job.crew.contains(other.getUUID()) || other.isSpectator()) {
                continue;
            }
            TEAM_INVITES.put(other.getUUID(), new TeamInvite(player.getUUID(), job, level.getGameTime()));
            Component yes = Component.literal("[Ja]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withBold(true).withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/job ja"))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(
                            "Mitmachen - du wirst zu " + player.getName().getString() + " teleportiert"))));
            Component no = Component.literal("[Nein]").withStyle(style -> style.withColor(ChatFormatting.RED)
                    .withBold(true).withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/job nein")));
            other.sendSystemMessage(Component.literal(player.getName().getString() + " will mit dir den Team-Job \""
                    + type.label + "\" machen. ").withStyle(ChatFormatting.GOLD).append(yes)
                    .append(Component.literal(" ")).append(no));
            other.level().playSound(null, other.getX(), other.getY(), other.getZ(),
                    SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
            asked++;
        }
        player.sendSystemMessage(Component.literal(asked == 0
                ? "Gerade ist kein anderer Spieler in der Welt - du fängst allein an."
                : "Anfrage an " + asked + (asked == 1 ? " Spieler" : " Spieler") + " geschickt. Wer Ja sagt, wird "
                + "zu dir teleportiert.").withStyle(ChatFormatting.GREEN));
    }

    /** [Ja] on a team job request: teleport to the player who asked and join the job. */
    public static void acceptTeam(ServerPlayer player) {
        TeamInvite invite = TEAM_INVITES.remove(player.getUUID());
        MinecraftServer server = player.level().getServer();
        if (invite == null || player.level().getGameTime() - invite.time() > TEAM_INVITE_TICKS) {
            player.sendSystemMessage(Component.literal("Keine offene Job-Anfrage.").withStyle(ChatFormatting.RED));
            return;
        }
        ServerPlayer from = Crew.find(server, invite.from());
        if (from == null || ACTIVE.get(from.getUUID()) != invite.job()) {
            player.sendSystemMessage(Component.literal("Der Job läuft nicht mehr.").withStyle(ChatFormatting.RED));
            return;
        }
        Job job = invite.job();
        if (ACTIVE.get(player.getUUID()) == job) {
            return;
        }
        if (ACTIVE.containsKey(player.getUUID())) {
            cancel(player, "du hilfst jetzt " + from.getName().getString() + ".");
        }
        player.stopRiding();
        Vec3 side = from.getLookAngle().multiply(1, 0, 1);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 spot = from.position().add(-side.z * 1.5, 0.0, side.x * 1.5);
        player.teleportTo((ServerLevel) from.level(), spot.x, from.getY(), spot.z, java.util.Set.of(),
                from.getYRot(), 0.0F, true);
        ACTIVE.put(player.getUUID(), job);
        job.crew.add(player.getUUID());
        Crew.join(from, player);
        updateMission(from, job);
        for (ServerPlayer m : members(server, job)) {
            m.sendSystemMessage(Component.literal(player.getName().getString() + " macht beim Team-Job mit ("
                    + job.title() + "). Jeder bekommt den vollen Lohn.").withStyle(ChatFormatting.GREEN));
        }
        say(player, job.station, intro(job));
        voice(player, introVoice(job));
    }

    public static void declineTeam(ServerPlayer player) {
        TeamInvite invite = TEAM_INVITES.remove(player.getUUID());
        if (invite == null) {
            return;
        }
        ServerPlayer from = Crew.find(player.level().getServer(), invite.from());
        if (from != null) {
            from.sendSystemMessage(Component.literal(player.getName().getString() + " hat keine Zeit.")
                    .withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.literal("Anfrage abgelehnt.").withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ building the steps

    /** Pay factor of a level: +30 % per level. */
    private static double payFactor(Job job) {
        return 1.0 + 0.3 * (job.level - 1);
    }

    /** Time factor of a level: 8 % less time per level, at least 55 %. */
    private static double timeFactor(Job job) {
        return Math.max(0.55, 1.0 - 0.08 * (job.level - 1));
    }

    private static void build(ServerLevel level, ServerPlayer player, Job job) {
        buildSteps(level, player, job);
        if (job.chapter == 0) {
            for (Step step : job.steps) {
                step.pay = (int) Math.round(step.pay * payFactor(job));
                if (step.seconds > 0) {
                    step.seconds = (int) Math.max(45, step.seconds * timeFactor(job));
                }
            }
        }
    }

    private static void buildSteps(ServerLevel level, ServerPlayer player, Job job) {
        RandomSource random = level.getRandom();
        int lv = job.chapter > 0 ? 1 : job.level;
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
                int stops = job.chapter == 2 ? 2 : Math.min(7, 2 + lv);
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
                int rides = job.chapter == 3 ? 1 : Math.min(5, 1 + lv);
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
                int patients = Math.min(4, lv);
                BlockPos last = from;
                for (int i = 1; i <= patients; i++) {
                    String n = patients > 1 ? " " + i + "/" + patients : "";
                    BlockPos victim = randomAddress(random, last, 150, 500, homes);
                    CityMap.Place hospital = CityMap.nearest(CityMap.Kind.HOSPITAL, victim.getX(), victim.getZ());
                    BlockPos to = hospital == null ? CityPlaces.spawn() : hospital.entrance();
                    double dist = flat(victim, to);
                    job.steps.add(new Step(Goal.PICKUP, victim, "Verletzten" + n + " abholen (im Auto!)").seconds(240)
                            .npc("Verletzter").say("Verletzter: Bitte... schnell ins Krankenhaus!"));
                    job.steps.add(new Step(Goal.DROPOFF, to, "Verletzten" + n + " ins Krankenhaus bringen")
                            .pay((int) (1000 + dist * 2.0)).seconds((int) (50 + dist / 7)).npc("Verletzter")
                            .say("Der Arzt: Gut gemacht - du hast ein Leben gerettet!"));
                    last = to;
                }
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
                        .wanted(lv >= 3 ? 3 : lv == 2 ? 2 : random.nextBoolean() ? 2 : 0));
                job.steps.add(new Step(Goal.CLEAR, buyer, "Waffen dem Käufer bringen (ohne Fahndung!)")
                        .pay(7000 + random.nextInt(2501)).seconds(600).say("Käufer: Saubere Arbeit."));
            }
            case GANG_WAR -> {
                BlockPos hideout = randomAddress(random, from, 250, 700, EnumSet.of(CityLayout.LotType.WAREHOUSE,
                        CityLayout.LotType.PARKING, CityLayout.LotType.HOUSE));
                job.steps.add(new Step(Goal.KILL_GROUP, hideout, "Gangversteck ausräumen (" + gangSize(job)
                        + " Gangster)").pay(6000)
                        .seconds(900).npc("Ballas").say("Polizei: Saubere Arbeit. Das Geld ist überwiesen."));
            }
            case STREET_RACE -> {
                BlockPos last = from;
                int total = 0;
                int points = Math.min(8, 4 + lv);
                for (int i = 1; i <= points; i++) {
                    BlockPos to = randomAddress(random, last, 180, 420, homes);
                    int secs = (int) (12 + flat(last, to) / 9);
                    total += secs;
                    job.steps.add(new Step(Goal.CHECKPOINT, to, "Rennen: Checkpoint " + i + "/" + points + " (im Auto)")
                            .seconds(i == 1 ? 240 : secs).pay(i == points ? 3000 : 0));
                    last = to;
                }
                job.steps.getLast().say("Rennleiter: Im Ziel! Schnelle Karre, schneller Fahrer.");
            }
            case GANG_CAR -> {
                BlockPos hideout = randomAddress(random, from, 300, 800, EnumSet.of(CityLayout.LotType.WAREHOUSE,
                        CityLayout.LotType.PARKING, CityLayout.LotType.HOUSE));
                BlockPos buyer = randomAddress(random, hideout, 600, 1400, EnumSet.of(CityLayout.LotType.CONTAINERS,
                        CityLayout.LotType.WAREHOUSE, CityLayout.LotType.PARKING));
                job.steps.add(new Step(Goal.GANG_CAR, hideout, "Den Wagen der Gang klauen").seconds(900));
                job.steps.add(new Step(Goal.DELIVER_CAR, buyer, "Gang-Auto zum Käufer bringen").pay(9000)
                        .seconds(600).say("Käufer: Die Karre ist heiß - genau richtig. Hier ist dein Geld."));
            }
            case PROTECTION -> {
                int shops = Math.min(6, 2 + lv);
                BlockPos last = from;
                java.util.Set<Long> used = new java.util.HashSet<>();
                for (int i = 1; i <= shops; i++) {
                    CityMap.Place shop = null;
                    double best = Double.MAX_VALUE;
                    for (CityMap.Place p : CityMap.places()) {
                        if ((p.kind() == CityMap.Kind.STORE || p.kind() == CityMap.Kind.GAS_STATION)
                                && !used.contains(p.id())) {
                            double d = flat(last, p.entrance()) + random.nextInt(250);
                            if (d > 120 && d < best) {
                                best = d;
                                shop = p;
                            }
                        }
                    }
                    if (shop == null) {
                        break;
                    }
                    used.add(shop.id());
                    last = shop.entrance();
                    job.steps.add(new Step(Goal.REACH, last, "Schutzgeld kassieren: Laden " + i + "/" + shops)
                            .pay(800).seconds(300).wanted(random.nextInt(4) == 0 || lv >= 3 && i == shops ? 1 : 0)
                            .say("Verkäufer: Schon gut, schon gut ... hier. Grüß Tony."));
                }
            }
            case CREW_HEIST -> {
                CityMap.Place bank = CityMap.nearest(CityMap.Kind.BANK, from.getX(), from.getZ());
                CityMap.Place hideout = CityMap.nearest(CityMap.Kind.DOCKS, from.getX(), from.getZ());
                BlockPos door = bank == null ? CityPlaces.spawn() : bank.entrance();
                job.steps.add(new Step(Goal.TOGETHER, door, "Crew: alle vor der Bank treffen").seconds(900)
                        .say("Tony: Alle da. Jetzt den Tresor aufbohren!"));
                job.steps.add(new Step(Goal.HEIST, door, "Bank: Tresor mit dem Thermobohrer knacken").seconds(900)
                        .wanted(Math.min(5, 2 + lv)).say("Tony: Der Tresor ist offen! Nehmt alles und weg da!"));
                job.steps.add(new Step(Goal.CLEAR, hideout == null ? from : hideout.entrance(),
                        "Zum Hafenbüro und die Polizei abhängen").pay(20000).seconds(1500)
                        .say("Tony: Was für ein Coup! Jeder von euch kriegt seinen vollen Anteil."));
            }
            case CAR_THEFT -> {
                BlockPos yard = randomAddress(random, from, 0, 5000, EnumSet.of(CityLayout.LotType.CONTAINERS),
                        CityLayout.District.INDUSTRIAL);
                int cars = Math.min(3, lv);
                for (int i = 1; i <= cars; i++) {
                    job.steps.add(new Step(Goal.STEAL_CAR, yard, "Geklauten Sportwagen zum Schrottplatz bringen"
                            + (cars > 1 ? " (" + i + "/" + cars + ")" : "")).seconds(1200));
                }
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
                    + stops[i][1]).pay(100).say((String) stops[i][2])
                    .look(new BlockPos(place.x(), CityLayout.GROUND, place.z())));
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
        for (ServerPlayer m : members(player, job)) {
            m.setAttached(ModAttachments.MISSION, new ModAttachments.Mission(s.label, p.getX(), p.getZ()));
        }
    }

    /** Called when a step becomes the current one. */
    private static void enter(ServerLevel level, ServerPlayer player, Job job) {
        Step s = job.step();
        job.spawned = false;
        job.lastHint = 0;
        job.missing = 0;
        job.deadline = s.seconds > 0 ? level.getGameTime() + s.seconds * 20L : Long.MAX_VALUE;
        updateMission(player, job);
    }

    /** Step done: pay, tell the story bit, go on to the next one - or finish the job. */
    private static void advance(ServerLevel level, ServerPlayer player, Job job, int extra) {
        Step s = job.step();
        if (s.look != null && job.npc != null && level.getEntity(job.npc) instanceof NpcEntity guide
                && "guide".equals(guide.role())) {
            guide.pointAt(s.look.getX() + 0.5, s.look.getZ() + 0.5);
            guide.despawnIn(100);
            job.npc = null;
        }
        List<ServerPlayer> team = members(player, job);
        for (ServerPlayer m : team) {
            int pay = (int) Math.round((s.pay + extra) * bonus(m));
            if (pay > 0) {
                Economy.add(m, pay);
                if (m == player) {
                    job.earned += pay;
                }
                m.level().playSound(null, m.getX(), m.getY(), m.getZ(), ModSounds.CASH, SoundSource.PLAYERS, 1.0F,
                        1.0F);
                m.sendOverlayMessage(Component.literal(s.label.split(":")[0] + " erledigt: +" + Economy.format(pay))
                        .withStyle(ChatFormatting.GREEN));
            }
            if (s.say != null) {
                m.sendSystemMessage(Component.literal(s.say).withStyle(job.station.illegal ? ChatFormatting.RED
                        : ChatFormatting.AQUA));
            }
        }
        if (job.type == Type.STREET_RACE && team.size() > 1 && job.index == job.steps.size() - 1) {
            int prize = 2000;
            Economy.add(player, prize);
            for (ServerPlayer m : team) {
                m.sendSystemMessage(Component.literal(player.getName().getString() + " gewinnt das Rennen und "
                        + "kassiert " + Economy.format(prize) + " Siegerbonus!").withStyle(ChatFormatting.GOLD));
            }
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
        List<ServerPlayer> team = members(player, job);
        for (ServerPlayer m : team) {
            ACTIVE.remove(m.getUUID());
        }
        cleanup(level, job);
        Set<UUID> ids = new LinkedHashSet<>();
        team.forEach(m -> ids.add(m.getUUID()));
        for (ServerPlayer m : team) {
            finishFor(m, job);
            if (job.chapter == 0) {
                // The "Weitermachen" window: the next order of the same job, one level harder.
                NEXT.put(m.getUUID(), new Next(job.type, job.level + 1, ids, level.getGameTime()));
                if (!(m instanceof net.fabricmc.fabric.api.entity.FakePlayer)) {
                    ServerPlayNetworking.send(m, new Payloads.JobDone(job.type.ordinal(), job.level,
                            m == player ? job.earned : 0));
                }
            }
        }
    }

    private static void finishFor(ServerPlayer player, Job job) {
        player.removeAttached(ModAttachments.MISSION);
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
        voice(player, job.station.clerk.toLowerCase(java.util.Locale.ROOT) + "_job_done");
        WantedSystem.title(player, Component.literal(job.chapter > 0 ? "KAPITEL " + job.chapter + " GESCHAFFT"
                : "JOB ERLEDIGT").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("Verdient: " + Economy.format(total) + "  -  Rang: " + rank(done(player)))
                        .withStyle(ChatFormatting.GREEN));
        if (job.chapter > 0 && job.chapter < MAX_CHAPTER) {
            Station next = CHAPTER_STATION[job.chapter];
            player.sendSystemMessage(Component.literal("Nächstes Kapitel: " + CHAPTER_TITLES[job.chapter] + " - "
                    + next.clerk + " meldet sich in einer Minute bei dir (oder sofort im " + next.label + ").")
                    .withStyle(ChatFormatting.GOLD));
            scheduleStory(player, 20 * 60);
        } else if (job.chapter == MAX_CHAPTER) {
            player.sendSystemMessage(Component.literal("Die Story ist durch - du bist der Boss von Los Santos. "
                    + "Kauf dir eine Villa und einen Supersportwagen, oder arbeite weiter für mehr Rang.")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static void fail(ServerPlayer player, String reason) {
        Job job = ACTIVE.get(player.getUUID());
        if (job != null) {
            for (ServerPlayer mate : members(player, job)) {
                if (mate != player) {
                    cancel(mate, reason);
                }
            }
        }
        cancel(player, reason);
        if (job != null && job.chapter > 0) {
            // A lost chapter is tried again soon, it was not cancelled on purpose.
            STORY_PAUSED.remove(player.getUUID());
            scheduleStory(player, 20 * 45);
            player.sendSystemMessage(Component.literal("Das Kapitel startet gleich noch einmal.")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        storyTick(server);
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(ACTIVE.keySet())) {
            ServerPlayer player = Crew.find(server, id);
            Job job = ACTIVE.get(id);
            if (player == null || job == null) {
                ACTIVE.remove(id);
                continue;
            }
            if (!player.isAlive()) {
                if (members(server, job).size() > 1) {
                    cancel(player, "Du bist gestorben - deine Crew macht weiter.");
                } else {
                    fail(player, "Du bist gestorben.");
                }
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
                    if (aboard && dist > 20 && now % 160 == 0) {
                        rider.pointAt(s.pos.getX() + 0.5, s.pos.getZ() + 0.5); // "Da lang!"
                    }
                    if (dist <= 12.0 && inCar && aboard) {
                        NpcEntity npc = rider;
                        int tip = player.getVehicle() instanceof CarEntity car && car.healthFraction() > 0.8F
                                ? (int) (s.pay * 0.2) : 0;
                        if (npc != null) {
                            npc.stopRiding();
                            npc.snapTo(player.getX() + 1.5, player.getY(), player.getZ(), npc.getYRot(), 0.0F);
                            npc.gesture(NpcEntity.TALK, player.getYRot());
                            npc.despawnIn(70);
                        }
                        job.npc = null;
                        advance(level, player, job, tip + timeBonus(job, level));
                    }
                }
                case KILL -> killCheck(level, player, job, s);
                case KILL_GROUP -> {
                    if (job.spawned && job.group.stream().noneMatch(g -> level.getEntity(g) instanceof NpcEntity n
                            && n.isAlive()) && level.hasChunkAt(s.pos)) {
                        job.group.clear();
                        advance(level, player, job, 0);
                    }
                }
                case GANG_CAR -> gangCar(level, player, job, s, dist, inCar);
                case DELIVER_CAR -> {
                    boolean theirCar = job.car != null && player.getVehicle() instanceof CarEntity car
                            && car.getUUID().equals(job.car);
                    if (job.car != null && level.getEntity(job.car) == null && level.hasChunkAt(player.blockPosition())
                            && !theirCar && ++job.missing > 120) {
                        fail(player, "Der Wagen ist weg.");
                        continue;
                    }
                    if (dist <= 14.0 && theirCar) {
                        CarEntity car = (CarEntity) player.getVehicle();
                        player.stopRiding();
                        car.despawn();
                        job.car = null;
                        advance(level, player, job, timeBonus(job, level));
                    } else if (dist <= 14.0 && now - job.lastHint > 20 * 8) {
                        job.lastHint = now;
                        player.sendOverlayMessage(Component.literal("Der Käufer will den Wagen der Gang sehen!")
                                .withStyle(ChatFormatting.RED));
                    }
                }
                case CHECKPOINT -> {
                    if (dist <= 14.0) {
                        if (inCar) {
                            advance(level, player, job, 0);
                        } else if (now - job.lastHint > 20 * 8) {
                            job.lastHint = now;
                            player.sendOverlayMessage(Component.literal("Checkpoints zählen nur am Steuer eines Autos!")
                                    .withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }
                case TOGETHER -> {
                    List<ServerPlayer> team = members(server, job);
                    boolean all = team.stream().allMatch(m -> Math.sqrt(m.distanceToSqr(s.pos.getX() + 0.5,
                            m.getY(), s.pos.getZ() + 0.5)) <= 20.0);
                    if (all) {
                        advance(level, player, job, 0);
                    } else if (dist <= 20.0 && now - job.lastHint > 20 * 10) {
                        job.lastHint = now;
                        player.sendOverlayMessage(Component.literal("Warte auf deine Crew vor der Bank ...")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                }
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
            if (ACTIVE.get(id) == job && s.goal == Goal.KILL && tickCountEvery(server, 40)) {
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
        boolean guide = s.goal == Goal.REACH && s.look != null;
        if (job.spawned || dist > 90.0 || !(s.goal == Goal.PICKUP || s.goal == Goal.KILL
                || s.goal == Goal.KILL_GROUP || s.goal == Goal.GANG_CAR || guide)) {
            return;
        }
        if (!level.hasChunkAt(s.pos) || !level.isPositionEntityTicking(s.pos)) {
            return;
        }
        job.spawned = true;
        if (s.goal == Goal.KILL_GROUP) {
            spawnGang(level, player, job, s, true);
            return;
        }
        if (s.goal == Goal.GANG_CAR) {
            // The car parked on the street in front of the hideout, the gang hanging around it.
            CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.EVENT);
            if (car != null) {
                CarVariant[] fancy = {CarVariant.SPORTS_BLACK, CarVariant.SPORTS_RED, CarVariant.SUV_BLACK,
                        CarVariant.SPORTS_YELLOW};
                car.setVariant(job.level >= 3 ? CarVariant.SUPER_CARBON
                        : fancy[level.getRandom().nextInt(fancy.length)]);
                car.setPersistentCar(true);
                car.snapTo(s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5,
                        level.getRandom().nextFloat() * 360.0F, 0.0F);
                level.addFreshEntity(car);
                job.car = car.getUUID();
            }
            job.spotted = false;
            spawnGang(level, player, job, s, false);
            return;
        }
        if (guide) {
            // Marco waits at every stop of the tour, waves you over and points at the building.
            NpcEntity marco = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.EVENT);
            if (marco != null) {
                marco.snapTo(s.pos.getX() + 1.5, s.pos.getY(), s.pos.getZ() + 1.5, 0.0F, 0.0F);
                marco.randomizeLook(false);
                marco.setRole("guide", "Marco (Führer)");
                marco.setNoAi(true);
                level.addFreshEntity(marco);
                job.npc = marco.getUUID();
            }
            return;
        }
        NpcEntity npc = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.EVENT);
        if (npc == null) {
            return;
        }
        boolean bounty = s.goal == Goal.KILL;
        npc.snapTo(s.pos.getX() + 0.5, s.pos.getY(), s.pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        npc.randomizeLook(bounty);
        npc.setRole(bounty ? "bounty" : "passenger", (bounty ? "Gesucht: " : "") + (s.npcName == null ? "?" : s.npcName));
        if (bounty) {
            double health = 40.0 + 20.0 * (job.level - 1);
            npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            npc.setHealth((float) health);
            npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.BASEBALL_BAT));
            npc.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 20, 0, false, false));
        } else {
            npc.setNoAi(true);
        }
        level.addFreshEntity(npc);
        job.npc = npc.getUUID();
        updateMission(player, job);
    }

    private static int gangSize(Job job) {
        return Math.min(8, 3 + job.level);
    }

    /**
     * Gang car theft: when the player gets close, a gang member shouts ("you shouldn't have come here") and they
     * all attack. The step is done once the player drives off in the gang's car.
     */
    private static void gangCar(ServerLevel level, ServerPlayer player, Job job, Step s, double dist, boolean inCar) {
        Entity carEntity = job.car == null ? null : level.getEntity(job.car);
        if (inCar && carEntity != null && player.getVehicle() == carEntity) {
            job.missing = 0;
            advance(level, player, job, 0);
            return;
        }
        if (!job.spotted && job.spawned && dist < 18.0) {
            job.spotted = true;
            NpcEntity speaker = null;
            for (UUID id : job.group) {
                if (level.getEntity(id) instanceof NpcEntity n && n.isAlive()) {
                    n.setTarget(player);
                    if (speaker == null || n.distanceTo(player) < speaker.distanceTo(player)) {
                        speaker = n;
                    }
                }
            }
            if (speaker != null) {
                level.playSound(null, speaker.getX(), speaker.getEyeY(), speaker.getZ(), ModSounds.VOICE_GANG_THREAT,
                        SoundSource.HOSTILE, 3.0F, 1.0F);
                speaker.pointAt(player.getX(), player.getZ());
                for (ServerPlayer m : members(player, job)) {
                    m.sendSystemMessage(Component.literal("Gangster: Du hättest nicht herkommen sollen. Jetzt bist du "
                            + "dran!").withStyle(ChatFormatting.DARK_RED));
                }
            }
        }
        if (job.spawned && carEntity == null && level.hasChunkAt(s.pos) && level.isPositionEntityTicking(s.pos)
                && ++job.missing > 40) {
            fail(player, "Der Wagen der Gang ist zerstört.");
        }
    }

    /** Gang war: armed gang members around the hideout (more each level), they go for the player. */
    private static void spawnGang(ServerLevel level, ServerPlayer player, Job job, Step s, boolean attack) {
        RandomSource random = level.getRandom();
        Item[] weapons = {ModItems.BASEBALL_BAT, ModItems.KNIFE};
        double health = 30.0 + 5.0 * (job.level - 1);
        for (int i = 0; i < gangSize(job); i++) {
            NpcEntity npc = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.EVENT);
            if (npc == null) {
                continue;
            }
            double x = s.pos.getX() + 0.5 + random.nextInt(7) - 3, z = s.pos.getZ() + 0.5 + random.nextInt(7) - 3;
            npc.snapTo(x, s.pos.getY(), z, random.nextFloat() * 360.0F, 0.0F);
            npc.randomizeLook(true);
            npc.setRole("bounty", "Gangster");
            npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            npc.setHealth((float) health);
            npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons[i % weapons.length]));
            npc.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 15, 0, false, false));
            level.addFreshEntity(npc);
            if (attack) {
                npc.setTarget(player);
            }
            job.group.add(npc.getUUID());
        }
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
        if (npc.getVehicle() == player.getVehicle()) {
            job.boarded = true;
            advance(level, player, job, 0);
        } else if (player.getVehicle().getPassengers().size() >= 2) {
            if (now - job.lastHint > 20 * 8) {
                job.lastHint = now;
                player.sendOverlayMessage(Component.literal("Im Auto ist kein Platz frei.")
                        .withStyle(ChatFormatting.RED));
            }
        } else if (!npc.isBoarding() && player.getVehicle() instanceof CarEntity car) {
            // The guest walks over, the door opens, and in he gets.
            npc.gesture(NpcEntity.WAVE, npc.getYRot());
            npc.boardCar(car);
        }
    }

    /**
     * The passenger must survive the ride. If he is out of the car (crash, the player left him somewhere), he gets
     * back in when the player stops next to him with a car.
     */
    private static boolean dropoffAlive(ServerLevel level, ServerPlayer player, Job job) {
        Entity e = job.npc == null ? null : level.getEntity(job.npc);
        if (e instanceof NpcEntity npc && npc.isAlive()) {
            job.missing = 0;
            if (npc.getVehicle() == null && player.getVehicle() instanceof CarEntity car
                    && car.getControllingPassenger() == player && npc.distanceTo(player) < 8.0) {
                npc.boardCar(car);
            }
            return true;
        }
        if (e == null && ++job.missing < 20) {
            return true; // not loaded right now (teleport, far away) - give it ten seconds
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
                s.pay((int) Math.round((shape == CarVariant.Shape.SUPER ? 15000 : 6000) * payFactor(job)));
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

    /** For the automatic game test: moves the goal of the current step (e.g. next to the taxi). */
    public static void moveGoalForTest(ServerPlayer player, BlockPos pos) {
        Job job = ACTIVE.get(player.getUUID());
        if (job == null) {
            return;
        }
        Step old = job.step();
        Step moved = new Step(old.goal, pos, old.label).pay(old.pay).seconds(old.seconds).say(old.say).npc(old.npcName);
        job.steps.set(job.index, moved);
        updateMission(player, job);
    }

    /** The bank vault was drilled open: chapter 5 goes on. */
    public static void onVaultOpened(ServerPlayer player) {
        Job job = ACTIVE.get(player.getUUID());
        if (job != null && job.step().goal == Goal.HEIST) {
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
