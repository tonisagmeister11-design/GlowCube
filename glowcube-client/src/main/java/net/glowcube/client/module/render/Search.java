package net.glowcube.client.module.render;

import net.glowcube.client.render.WeltRender;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.ColorUtil;
import net.glowcube.client.util.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uebertragen aus BleachHack (GPL-3.0), Modul {@code Search}.
 *
 * <p>Der entscheidende Teil ist der Aufbau, nicht das Zeichnen. Das Original
 * sucht nicht jeden Tick von neuem - es durchsucht jeden Chunk <b>einmal</b>,
 * wenn er geladen wird, merkt sich die Fundstellen und haelt sie danach
 * aktuell:
 *
 * <ul>
 *   <li>Chunk geladen: einmal ueber die volle Hoehe durchsuchen.</li>
 *   <li>Chunk entladen: alle Fundstellen darin wegwerfen.</li>
 *   <li>Block geaendert: nur diese eine Stelle nachziehen.</li>
 * </ul>
 *
 * <p>Das ist der Grund, warum das Original auch mit 32 Chunks Sichtweite
 * fluessig bleibt. Die frueher hier stehende Fassung rechnete jeden Tick
 * einen Wuerfel durch und war schon bei 16 Bloecken Radius die teuerste
 * Stelle im Spiel.
 *
 * <p>Die Suche laeuft in einem eigenen Faden, damit ein frisch geladener
 * Chunk nicht den Spielfaden anhaelt; die Fundstellen liegen deshalb in
 * einer nebenlaeufigen Menge.
 *
 * <p><b>Nicht uebernommen:</b> das Mitschreiben der Funde in eine Datei.
 */
public final class Search extends Module {
    private final ModeSetting darstellung = register(new ModeSetting("Darstellung",
            "Kasten, Linie zum Fund, oder beides", "Kasten", "Kasten", "Kasten+Linie", "Linie"));
    private final BooleanSetting schimmer = register(new BooleanSetting("Schimmer",
            "Zusaetzlicher blasser Rahmen um jeden Fund", true));
    private final NumberSetting hoechstens = register(new NumberSetting("Hoechstens",
            "Wie viele Funde gleichzeitig gezeichnet werden", 3000, 100, 20000, 100));
    private final BlockListSetting bloecke = register(new BlockListSetting("Bloecke",
            "Wonach gesucht wird",
            "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore",
            "ancient_debris", "diamond_block", "emerald_block"));

    /** Die Fundstellen. Nebenlaeufig, weil ein eigener Faden sie fuellt. */
    private static final Set<BlockPos> FUNDE = ConcurrentHashMap.newKeySet();
    /** Noch zu durchsuchende Chunks. */
    private static final Deque<ChunkPos> WARTESCHLANGE = new ArrayDeque<>();

    private static Search instanz;
    private Set<String> letzteListe = Set.of();

    public Search() {
        super("Search", "Markiert gesuchte Bloecke, ohne die Sicht zu veraendern",
                Category.RENDER, com.mojang.blaze3d.platform.InputConstants.KEY_L);
        instanz = this;
    }

    @Override
    public void onEnable() {
        alleNeu();
    }

    @Override
    public void onDisable() {
        FUNDE.clear();
        synchronized (WARTESCHLANGE) {
            WARTESCHLANGE.clear();
        }
    }

    /**
     * Alles verwerfen und die geladenen Chunks neu einreihen. Noetig beim
     * Einschalten und immer, wenn sich die Blockliste geaendert hat.
     */
    public void alleNeu() {
        FUNDE.clear();
        synchronized (WARTESCHLANGE) {
            WARTESCHLANGE.clear();
            if (!inGame()) {
                return;
            }
            int sicht = mc.options.getEffectiveRenderDistance();
            ChunkPos mitte = player().chunkPosition();
            for (int dx = -sicht; dx <= sicht; dx++) {
                for (int dz = -sicht; dz <= sicht; dz++) {
                    WARTESCHLANGE.add(new ChunkPos(net.glowcube.client.render.Netz.chunkX(mitte) + dx, net.glowcube.client.render.Netz.chunkZ(mitte) + dz));
                }
            }
        }
    }

    @Override
    public void onTick() {
        // Aenderung der Blockliste bemerken - dann gilt alles Gefundene nicht mehr.
        Set<String> jetzt = Set.copyOf(bloecke.ids());
        if (!jetzt.equals(letzteListe)) {
            letzteListe = jetzt;
            alleNeu();
            return;
        }

        // Ein Chunk je Tick. Mehr braucht es nicht: die Abschnittspruefung
        // unten wirft das meiste ohnehin sofort weg.
        ChunkPos naechster;
        synchronized (WARTESCHLANGE) {
            naechster = WARTESCHLANGE.poll();
        }
        if (naechster != null) {
            durchsuchen(naechster);
        }
    }

    /**
     * Einen Chunk durchsuchen.
     *
     * <p>Ein Chunk hat ueber die volle Hoehe rund 98.000 Bloecke - die alle
     * einzeln abzufragen waere die teuerste Stelle im ganzen Client. Deshalb
     * wird zuerst je 16er-Abschnitt gefragt, ob dort ueberhaupt einer der
     * gesuchten Bloecke <em>vorkommen kann</em>. Diese Auskunft gibt der
     * Abschnitt aus seiner eigenen Farbtabelle, ohne einen einzigen Block
     * anzufassen. Uebrig bleiben die zwei, drei Abschnitte, in denen wirklich
     * etwas liegt.
     */
    private void durchsuchen(ChunkPos chunkPos) {
        if (!inGame() || !level().getChunkSource().hasChunk(net.glowcube.client.render.Netz.chunkX(chunkPos), net.glowcube.client.render.Netz.chunkZ(chunkPos))) {
            return;
        }
        LevelChunk chunk = level().getChunk(net.glowcube.client.render.Netz.chunkX(chunkPos), net.glowcube.client.render.Netz.chunkZ(chunkPos));
        LevelChunkSection[] abschnitte = chunk.getSections();
        int basis = chunk.getMinY();

        for (int i = 0; i < abschnitte.length; i++) {
            LevelChunkSection abschnitt = abschnitte[i];
            if (abschnitt == null || abschnitt.hasOnlyAir()) {
                continue;
            }
            if (!abschnitt.maybeHas(this::gesucht)) {
                continue;
            }
            int unten = basis + i * 16;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState zustand = abschnitt.getBlockState(x, y, z);
                        if (zustand.isAir() || !gesucht(zustand)) {
                            continue;
                        }
                        FUNDE.add(new BlockPos(
                                chunkPos.getMinBlockX() + x, unten + y, chunkPos.getMinBlockZ() + z));
                    }
                }
            }
        }
    }

    private boolean gesucht(BlockState zustand) {
        return bloecke.contains(BuiltInRegistries.BLOCK.getKey(zustand.getBlock()).toString());
    }

    // ------------------------------------------------- Meldungen von aussen

    /** Wird vom LevelChunkMixin bei jeder Blockaenderung gerufen. */
    public static void blockGeaendert(BlockPos pos, BlockState zustand) {
        if (instanz == null || !instanz.isEnabled()) {
            return;
        }
        if (instanz.gesucht(zustand)) {
            FUNDE.add(pos.immutable());
        } else {
            FUNDE.remove(pos);
        }
    }

    /** Wird beim Laden eines Chunks gerufen. */
    public static void chunkGeladen(ChunkPos pos) {
        if (instanz == null || !instanz.isEnabled()) {
            return;
        }
        synchronized (WARTESCHLANGE) {
            WARTESCHLANGE.add(pos);
        }
    }

    /** Wird beim Entladen eines Chunks gerufen. */
    public static void chunkEntladen(ChunkPos pos) {
        if (instanz == null) {
            return;
        }
        FUNDE.removeIf(p -> (p.getX() >> 4) == pos.x && (p.getZ() >> 4) == pos.z);
    }

    // ------------------------------------------------------------- Zeichnen

    @Override
    public void onWorldRender(WeltRender render) {
        if (FUNDE.isEmpty()) {
            return;
        }
        Vec3 auge = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        int farbe = Theme.accentStart();
        int linienFarbe = ColorUtil.fade(farbe, 0.5f);
        int gezeichnet = 0;
        int grenze = hoechstens.getInt();
        // Nur, was ohnehin in Sichtweite liegt - der Rest kostet nur Bilder.
        double weite = Math.pow(Minecraft.getInstance().options.getEffectiveRenderDistance() * 16, 2);

        for (BlockPos pos : FUNDE) {
            if (gezeichnet >= grenze) {
                break;
            }
            Vec3 mitte = Vec3.atCenterOf(pos);
            if (mitte.distanceToSqr(auge) > weite) {
                continue;
            }
            if (!darstellung.is("Linie")) {
                render.box(new AABB(pos), farbe, schimmer.get());
            }
            if (!darstellung.is("Kasten")) {
                render.tracer(mitte, linienFarbe);
            }
            gezeichnet++;
        }
    }

    @Override
    public String hudSuffix() {
        return FUNDE.isEmpty() ? null : String.valueOf(FUNDE.size());
    }
}
