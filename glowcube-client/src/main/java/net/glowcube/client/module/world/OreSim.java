package net.glowcube.client.module.world;

import net.glowcube.client.render.WeltRender;
import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.integration.SeedBridge;
import net.glowcube.client.util.Erz;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uebertragen aus Meteor Rejects (GPL-3.0), Modul {@code OreSim}.
 *
 * <p>Das hier ist kein X-Ray. X-Ray blendet aus, was der Client ohnehin
 * schon bekommen hat - auf einem Server mit Anti-X-Ray sieht es deshalb
 * nichts. OreSim <b>rechnet</b> stattdessen nach, wo die Erze bei der
 * Weltgenerierung entstanden <em>waeren</em>. Dafuer braucht es nur eines:
 * den Weltseed.
 *
 * <p>Der Weg ist derselbe, den Minecraft selbst geht. Aus Seed und
 * Chunkkoordinate entsteht ein Startwert, daraus je Merkmal eine eigene
 * Zufallsfolge (das sind {@code schritt} und {@code stelle} in
 * {@link Erz}), und aus dieser Folge Zahl fuer Zahl: wie viele Adern, wo,
 * in welcher Hoehe, wie gross. Die Aderform selbst ist Mojangs Rechnung,
 * Zeile fuer Zeile nachgezogen - schon eine vertauschte Zeile darin
 * verschiebt alles.
 *
 * <p>Den Seed liefert entweder SeedCrackerX von selbst, oder man gibt ihn
 * mit {@code /glowcube seed <zahl>} ein.
 *
 * <p><b>Luftpruefung.</b> Erz, das an einer Hoehle liegt, wird bei der
 * Generierung mit einer gewissen Wahrscheinlichkeit weggelassen - und
 * ausserdem hat es jemand vielleicht laengst abgebaut. Die Einstellung
 * entscheidet, ob gegen die wirklich geladene Welt geprueft wird.
 *
 * <p><b>Nicht uebernommen:</b> die Anbindung an Baritone.
 */
public final class OreSim extends Module {
    private final NumberSetting umkreis = register(new NumberSetting("Chunk-Umkreis",
            "Wie viele Chunks weit gerechnet wird", 5, 1, 10, 1));
    private final ModeSetting luftpruefung = register(new ModeSetting("Luftpruefung",
            "Gegen die geladene Welt pruefen, statt blind zu rechnen",
            "Beim Laden", "Beim Laden", "Aus"));
    private final NumberSetting hoechstens = register(new NumberSetting("Hoechstens",
            "Wie viele Bloecke gleichzeitig gezeichnet werden", 4000, 200, 40000, 200));

    private final Map<Erz.Art, BooleanSetting> arten = new EnumMap<>(Erz.Art.class);

    /** Fundstellen je Chunk und Erzart. */
    private final Map<Long, Map<Erz.Art, Set<BlockPos>>> proChunk = new ConcurrentHashMap<>();
    private Map<ResourceKey<Biome>, List<Erz>> verzeichnis;
    private Long seedInBenutzung;
    private boolean erzGemeldet;

    public OreSim() {
        super("OreSim", "Rechnet aus dem Weltseed, wo die Erze liegen", Category.WORLD,
                com.mojang.blaze3d.platform.InputConstants.KEY_O);
        for (Erz.Art art : Erz.Art.values()) {
            // Alles an: der Wunsch war ausdruecklich, dass alle Erze
            // gefunden werden. Wer will, klappt einzelne wieder weg.
            arten.put(art, register(new BooleanSetting(art.bezeichnung(),
                    art.bezeichnung() + " zeigen", true)));
        }
    }

    // --------------------------------------------------------------- Zustand

    @Override
    public void onEnable() {
        if (seed() == null) {
            melde("Kein Seed bekannt. SeedHunt (B) sucht einen, oder /glowcube seed <zahl>.");
            setEnabled(false);
            return;
        }
        neuAufbauen();
    }

    @Override
    public void onDisable() {
        proChunk.clear();
        verzeichnis = null;
        seedInBenutzung = null;
    }

    private Long seed() {
        return SeedBridge.seed();
    }

    private void melde(String text) {
        if (inGame()) {
            player().displayClientMessage(
                    net.minecraft.network.chat.Component.literal("[GlowCube] " + text), false);
        }
    }

    private void neuAufbauen() {
        Long seed = seed();
        if (seed == null || !inGame()) {
            return;
        }
        seedInBenutzung = seed;
        proChunk.clear();
        erzGemeldet = false;
        // Das Verzeichnis kommt aus Minecrafts eigener Weltvorlage, und die
        // aufzubauen dauert einen Augenblick. Besser ein Wort dazu als ein
        // Spiel, das scheinbar grundlos haengt.
        melde("Erztabelle wird aufgebaut - einen Moment.");
        try {
            verzeichnis = Erz.verzeichnis(dimensionsName());
            melde("Bereit. Seed " + seed + ", " + verzeichnis.size() + " Biome.");
        } catch (Throwable fehler) {
            GlowCubeClient.LOGGER.error("OreSim: Verzeichnis liess sich nicht bauen", fehler);
            melde("Die Erztabelle liess sich nicht lesen - siehe Protokoll.");
            setEnabled(false);
        }
    }

    private String dimensionsName() {
        ResourceKey<Level> welt = level().dimension();
        if (welt.equals(Level.NETHER)) {
            return "nether";
        }
        if (welt.equals(Level.END)) {
            return "end";
        }
        return "overworld";
    }

    // ----------------------------------------------------------------- Tick

    @Override
    public void onTick() {
        if (!inGame()) {
            return;
        }
        Long seed = seed();
        if (seed == null) {
            return;
        }
        // Der Seed kann waehrend des Betriebs eintreffen (SeedCrackerX wird
        // fertig) oder sich aendern (anderer Server). Dann gilt alles
        // Gerechnete nicht mehr.
        if (!seed.equals(seedInBenutzung) || verzeichnis == null) {
            neuAufbauen();
            return;
        }

        // Je Tick ein paar Chunks - der Rest kommt beim naechsten.
        ChunkPos mitte = player().chunkPosition();
        int weite = umkreis.getInt();
        int gerechnet = 0;
        for (int dx = -weite; dx <= weite && gerechnet < 2; dx++) {
            for (int dz = -weite; dz <= weite && gerechnet < 2; dz++) {
                long schluessel = ChunkPos.asLong(mitte.x + dx, mitte.z + dz);
                if (proChunk.containsKey(schluessel)) {
                    continue;
                }
                if (!level().getChunkSource().hasChunk(mitte.x + dx, mitte.z + dz)) {
                    continue;
                }
                rechnen(level().getChunk(mitte.x + dx, mitte.z + dz));
                gerechnet++;
            }
        }

        // Was weit weg liegt, wieder wegwerfen - sonst waechst die Karte
        // beim Herumlaufen ohne Ende.
        int grenze = weite + 3;
        proChunk.keySet().removeIf(schluessel -> {
            int cx = ChunkPos.getX(schluessel);
            int cz = ChunkPos.getZ(schluessel);
            return Math.abs(cx - mitte.x) > grenze || Math.abs(cz - mitte.z) > grenze;
        });

        // Einmal Rueckmeldung geben, sobald wirklich Erze berechnet wurden -
        // sonst weiss man nicht, ob es laeuft.
        if (!erzGemeldet) {
            int summe = 0;
            for (var chunk : proChunk.values()) {
                for (var stellen : chunk.values()) {
                    summe += stellen.size();
                }
            }
            if (summe > 0) {
                erzGemeldet = true;
                melde(summe + " Erzbloecke berechnet und markiert (Seed " + seed + ").");
            }
        }
    }

    // -------------------------------------------------------------- Rechnung

    private void rechnen(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        long schluessel = pos.toLong();
        if (proChunk.containsKey(schluessel)) {
            return;
        }

        // Welche Erze hier ueberhaupt vorkommen, haengt am Biom.
        Set<Erz> moegliche = new HashSet<>();
        for (List<Erz> liste : verzeichnis.values()) {
            moegliche.addAll(liste);
        }

        int chunkX = pos.x << 4;
        int chunkZ = pos.z << 4;
        WorldgenRandom zufall =
                new WorldgenRandom(WorldgenRandom.Algorithm.XOROSHIRO.newInstance(0L));
        long chunkStart = zufall.setDecorationSeed(seedInBenutzung, chunkX, chunkZ);

        Map<Erz.Art, Set<BlockPos>> ergebnis = new EnumMap<>(Erz.Art.class);

        for (Erz erz : moegliche) {
            if (erz.hoehe == null) {
                continue;
            }
            // Jedes Merkmal bekommt seine eigene Folge. Genau hier steckt
            // die ganze Vorhersagbarkeit.
            zufall.setFeatureSeed(chunkStart, erz.stelle, erz.schritt);

            int versuche = erz.anzahl.sample(zufall);
            for (int i = 0; i < versuche; i++) {
                if (erz.seltenheit != 1.0f && zufall.nextFloat() >= 1.0f / erz.seltenheit) {
                    continue;
                }
                int x = zufall.nextInt(16) + chunkX;
                int z = zufall.nextInt(16) + chunkZ;
                int y = erz.hoehe.sample(zufall, erz.hoehenRahmen);
                BlockPos ursprung = new BlockPos(x, y, z);

                ResourceKey<Biome> biom = chunk.getNoiseBiome(x, y, z).unwrapKey().orElse(null);
                if (biom != null && verzeichnis.containsKey(biom)
                        && !verzeichnis.get(biom).contains(erz)) {
                    continue;
                }

                List<BlockPos> bloecke = erz.zerstreut
                        ? zerstreut(zufall, ursprung, erz.groesse)
                        : ader(zufall, ursprung, erz.groesse, erz.verwerfenAnLuft);
                if (!bloecke.isEmpty()) {
                    ergebnis.computeIfAbsent(erz.art, a -> new HashSet<>()).addAll(bloecke);
                }
            }
        }
        proChunk.put(schluessel, ergebnis);
    }

    // ====================================================================
    // Ab hier ist es Mojangs Rechnung, Zeile fuer Zeile nachgezogen (ueber
    // Meteor Rejects). Die Buchstabennamen stehen so im Original; sie
    // umzubenennen hiesse, die Uebereinstimmung zu riskieren.
    // ====================================================================

    private List<BlockPos> ader(WorldgenRandom zufall, BlockPos pos, int groesse, float anLuft) {
        float f = zufall.nextFloat() * 3.1415927F;
        float g = (float) groesse / 8.0F;
        int i = Mth.ceil(((float) groesse / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = pos.getX() + Math.sin(f) * g;
        double e = pos.getX() - Math.sin(f) * g;
        double h = pos.getZ() + Math.cos(f) * g;
        double j = pos.getZ() - Math.cos(f) * g;
        double l = pos.getY() + zufall.nextInt(3) - 2;
        double m = pos.getY() + zufall.nextInt(3) - 2;
        int n = pos.getX() - Mth.ceil(g) - i;
        int o = pos.getY() - 2 - i;
        int p = pos.getZ() - Mth.ceil(g) - i;
        int q = 2 * (Mth.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; ++s) {
            for (int t = p; t <= p + q; ++t) {
                if (o <= level().getHeight(Heightmap.Types.MOTION_BLOCKING, s, t)) {
                    return aderTeil(zufall, groesse, d, e, h, j, l, m, n, o, p, q, r, anLuft);
                }
            }
        }
        return List.of();
    }

    private List<BlockPos> aderTeil(WorldgenRandom zufall, int groesse,
                                    double startX, double endX, double startZ, double endZ,
                                    double startY, double endY,
                                    int x, int y, int z, int size, int i, float anLuft) {
        BitSet belegt = new BitSet(size * i * size);
        BlockPos.MutableBlockPos wander = new BlockPos.MutableBlockPos();
        double[] ds = new double[groesse * 4];
        List<BlockPos> treffer = new ArrayList<>();

        int n;
        double p;
        double q;
        double r;
        double s;
        for (n = 0; n < groesse; ++n) {
            float f = (float) n / (float) groesse;
            p = Mth.lerp(f, startX, endX);
            q = Mth.lerp(f, startY, endY);
            r = Mth.lerp(f, startZ, endZ);
            s = zufall.nextDouble() * (double) groesse / 16.0D;
            double m = ((double) (Mth.sin(3.1415927F * f) + 1.0F) * s + 1.0D) / 2.0D;
            ds[n * 4] = p;
            ds[n * 4 + 1] = q;
            ds[n * 4 + 2] = r;
            ds[n * 4 + 3] = m;
        }

        for (n = 0; n < groesse - 1; ++n) {
            if (!(ds[n * 4 + 3] <= 0.0D)) {
                for (int o = n + 1; o < groesse; ++o) {
                    if (!(ds[o * 4 + 3] <= 0.0D)) {
                        p = ds[n * 4] - ds[o * 4];
                        q = ds[n * 4 + 1] - ds[o * 4 + 1];
                        r = ds[n * 4 + 2] - ds[o * 4 + 2];
                        s = ds[n * 4 + 3] - ds[o * 4 + 3];
                        if (s * s > p * p + q * q + r * r) {
                            if (s > 0.0D) {
                                ds[o * 4 + 3] = -1.0D;
                            } else {
                                ds[n * 4 + 3] = -1.0D;
                            }
                        }
                    }
                }
            }
        }

        for (n = 0; n < groesse; ++n) {
            double u = ds[n * 4 + 3];
            if (u < 0.0D) {
                continue;
            }
            double v = ds[n * 4];
            double w = ds[n * 4 + 1];
            double aa = ds[n * 4 + 2];
            int ab = Math.max(Mth.floor(v - u), x);
            int ac = Math.max(Mth.floor(w - u), y);
            int ad = Math.max(Mth.floor(aa - u), z);
            int ae = Math.max(Mth.floor(v + u), ab);
            int af = Math.max(Mth.floor(w + u), ac);
            int ag = Math.max(Mth.floor(aa + u), ad);

            for (int ah = ab; ah <= ae; ++ah) {
                double ai = ((double) ah + 0.5D - v) / u;
                if (ai * ai >= 1.0D) {
                    continue;
                }
                for (int aj = ac; aj <= af; ++aj) {
                    double ak = ((double) aj + 0.5D - w) / u;
                    if (ai * ai + ak * ak >= 1.0D) {
                        continue;
                    }
                    for (int al = ad; al <= ag; ++al) {
                        double am = ((double) al + 0.5D - aa) / u;
                        if (ai * ai + ak * ak + am * am >= 1.0D) {
                            continue;
                        }
                        int an = ah - x + (aj - y) * size + (al - z) * size * i;
                        if (belegt.get(an)) {
                            continue;
                        }
                        belegt.set(an);
                        wander.set(ah, aj, al);
                        if (aj < level().getMinY() || aj >= level().getMaxY()) {
                            continue;
                        }
                        if (!luftpruefung.is("Aus")
                                && !level().getBlockState(wander).canOcclude()) {
                            continue;
                        }
                        if (setzen(wander, anLuft, zufall)) {
                            treffer.add(new BlockPos(ah, aj, al));
                        }
                    }
                }
            }
        }
        return treffer;
    }

    private boolean setzen(BlockPos pos, float anLuft, WorldgenRandom zufall) {
        if (anLuft == 0.0F || (anLuft != 1.0F && zufall.nextFloat() >= anLuft)) {
            return true;
        }
        for (Direction seite : Direction.values()) {
            if (!level().getBlockState(pos.relative(seite)).canOcclude() && anLuft != 1.0F) {
                return false;
            }
        }
        return true;
    }

    private List<BlockPos> zerstreut(WorldgenRandom zufall, BlockPos pos, int groesse) {
        List<BlockPos> treffer = new ArrayList<>();
        int i = zufall.nextInt(groesse + 1);
        for (int j = 0; j < i; ++j) {
            int weite = Math.min(j, 7);
            int x = streuung(zufall, weite) + pos.getX();
            int y = streuung(zufall, weite) + pos.getY();
            int z = streuung(zufall, weite) + pos.getZ();
            BlockPos stelle = new BlockPos(x, y, z);
            if (!luftpruefung.is("Aus") && !level().getBlockState(stelle).canOcclude()) {
                continue;
            }
            if (setzen(stelle, 1.0F, zufall)) {
                treffer.add(stelle);
            }
        }
        return treffer;
    }

    private static int streuung(WorldgenRandom zufall, int weite) {
        return Math.round((zufall.nextFloat() - zufall.nextFloat()) * (float) weite);
    }

    // -------------------------------------------------------------- Zeichnen

    @Override
    public void onWorldRender(WeltRender render) {
        if (proChunk.isEmpty()) {
            return;
        }
        int gezeichnet = 0;
        int grenze = hoechstens.getInt();

        for (Map<Erz.Art, Set<BlockPos>> chunk : proChunk.values()) {
            for (Map.Entry<Erz.Art, Set<BlockPos>> eintrag : chunk.entrySet()) {
                BooleanSetting an = arten.get(eintrag.getKey());
                if (an == null || !an.get()) {
                    continue;
                }
                int farbe = eintrag.getKey().farbe();
                for (BlockPos pos : eintrag.getValue()) {
                    if (gezeichnet >= grenze) {
                        return;
                    }
                    render.box(new AABB(pos), farbe, false);
                    gezeichnet++;
                }
            }
        }
    }

    @Override
    public String hudSuffix() {
        if (seedInBenutzung == null) {
            return "kein Seed";
        }
        int summe = 0;
        for (Map<Erz.Art, Set<BlockPos>> chunk : proChunk.values()) {
            for (Set<BlockPos> stellen : chunk.values()) {
                summe += stellen.size();
            }
        }
        return String.valueOf(summe);
    }

    /** Wird nach einem Seed-Wechsel gerufen, damit die Karte neu entsteht. */
    public void seedGewechselt() {
        if (isEnabled()) {
            neuAufbauen();
        }
    }
}
