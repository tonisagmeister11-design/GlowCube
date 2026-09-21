package net.glowcube.client.module.world;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.BlockUtils;
import net.glowcube.client.util.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code VeinMiner}.
 *
 * <p>Die Abbau-Aura: ein Erz anschlagen, und die ganze Ader bricht mit. Das
 * ist genau dieselbe Bauart wie KillAura - nur werden statt Gegnern in
 * Reichweite die gleichartigen Nachbarbloecke gesammelt und der Reihe nach
 * abgetragen.
 *
 * <p>Uebernommen sind die drei Dinge, die es genau macht:
 * <ul>
 *   <li>Die 26 Nachbarn eines Blocks (alle drei Ebenen um ihn herum), damit
 *       auch diagonal verbundenes Erz mitgeht.</li>
 *   <li>Die Tiefenbegrenzung: von jedem Fund aus wird nur so viele Schritte
 *       weit weitergesucht. Ohne sie wuerde ein einziger Steinblock den
 *       halben Berg einreissen.</li>
 *   <li>Das Abtragen ueber mehrere Ticks mit einstellbarer Pause - ein Block
 *       nach dem anderen, mit gemeldeter Blickrichtung.</li>
 * </ul>
 *
 * <p>Angestossen wird die Aura ueber denselben Haken, an dem NoInteract
 * haengt: sobald der Spieler anfaengt, einen Block zu schlagen. Der Schlag
 * selbst geht durch - VeinMiner faengt ihn nicht ab, es haengt sich nur dran.
 *
 * <p><b>Nicht uebernommen:</b> das Einzeichnen der Ader (GlowCube zeichnet
 * ueber ein anderes System) und der Vergleich ueber {@code asItem} - hier
 * werden Bloecke direkt verglichen, was fuer den Zweck aufs Gleiche
 * herauskommt.
 */
public final class VeinMiner extends Module {
    /** Die 26 Nachbarn - alle Felder um einen Block, ihn selbst ausgenommen. */
    private static final List<Vec3i> NACHBARN = new ArrayList<>();

    static {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        NACHBARN.add(new Vec3i(x, y, z));
                    }
                }
            }
        }
    }

    private final BlockListSetting bloecke = register(new BlockListSetting("Bloecke",
            "Die Liste zur eingestellten Listenart",
            "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore",
            "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
            "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore",
            "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore",
            "nether_gold_ore", "nether_quartz_ore", "ancient_debris"));
    private final ModeSetting listenArt = register(new ModeSetting("Listenart",
            "Weiss bricht nur die Liste, Schwarz alles ausser ihr",
            "Weissliste", "Weissliste", "Schwarzliste"));
    private final NumberSetting tiefe = register(new NumberSetting("Tiefe",
            "Wie viele Schritte weit die Ader verfolgt wird", 4, 1, 15, 1));
    private final NumberSetting pause = register(new NumberSetting("Pause",
            "Ticks zwischen zwei Bloecken", 0, 0, 20, 1));
    private final BooleanSetting drehen = register(new BooleanSetting("Drehen",
            "Dem Server die passende Blickrichtung melden", true));
    private final BooleanSetting schwingen = register(new BooleanSetting("Schwingen",
            "Die Hand sichtbar bewegen", true));

    /** Die noch abzutragenden Stellen, in Fundreihenfolge. */
    private final List<BlockPos> ader = new ArrayList<>();
    private boolean angeschlagen;
    private int takt;

    public VeinMiner() {
        super("VeinMiner", "Baut die ganze Ader ab, wenn du ein Erz anschlaegst",
                Category.WORLD);
    }

    @Override
    public void onDisable() {
        ader.clear();
        takt = 0;
        angeschlagen = false;
    }

    /**
     * Der Anstoss. Liefert immer false - der Schlag des Spielers soll
     * durchgehen, VeinMiner sammelt nur die uebrige Ader dazu.
     */
    @Override
    public boolean onBlockBreak(BlockPos pos) {
        if (!inGame()) {
            return false;
        }
        BlockState zustand = level().getBlockState(pos);
        if (zustand.getDestroySpeed(level(), pos) < 0.0f) {
            return false;
        }
        if (!gesucht(zustand)) {
            return false;
        }
        if (ader.stream().anyMatch(p -> p.equals(pos))) {
            return false;
        }

        Block ziel = zustand.getBlock();
        Set<BlockPos> gefunden = new LinkedHashSet<>();
        sammeln(ziel, pos, tiefe.getInt(), gefunden);
        // Der angeschlagene Block selbst bleibt draussen - den bricht der
        // Spieler mit seinem eigenen Schlag; wir haengen nur die Nachbarn an.
        gefunden.remove(pos);
        ader.addAll(gefunden);
        return false;
    }

    private void sammeln(Block ziel, BlockPos pos, int tiefe, Set<BlockPos> gefunden) {
        if (tiefe <= 0 || gefunden.contains(pos)) {
            return;
        }
        gefunden.add(pos);
        // Reichweite: alles, was zu weit weg liegt, wuerde der Server
        // ohnehin ablehnen. 6 Bloecke Augenabstand sind grosszuegig genug.
        if (player().getEyePosition().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5) > 36.0) {
            return;
        }
        for (Vec3i versatz : NACHBARN) {
            BlockPos nachbar = pos.offset(versatz);
            if (level().getBlockState(nachbar).getBlock() == ziel) {
                sammeln(ziel, nachbar, tiefe - 1, gefunden);
            }
        }
    }

    @Override
    public void onTick() {
        // Verschwundene Stellen (schon abgebaut, oder Chunk weg) rauswerfen.
        ader.removeIf(pos -> level().getBlockState(pos).isAir());
        if (ader.isEmpty()) {
            takt = 0;
            angeschlagen = false;
            return;
        }

        if (!angeschlagen && takt < pause.getInt()) {
            takt++;
            return;
        }
        takt = 0;

        BlockPos pos = ader.get(0);
        if (!angeschlagen) {
            net.glowcube.client.render.Netz.schwingen(InteractionHand.MAIN_HAND);
            angeschlagen = true;
        }
        if (drehen.get()) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 50,
                    () -> BlockUtils.abbauen(pos, schwingen.get()));
        } else {
            BlockUtils.abbauen(pos, schwingen.get());
        }

        // Sobald der Block wirklich weg ist, zum naechsten - und der naechste
        // faengt wieder ohne die Pause an, damit die Ader zuegig durchlaeuft.
        if (level().getBlockState(pos).isAir()) {
            ader.remove(0);
            angeschlagen = false;
        }
    }

    private boolean gesucht(BlockState zustand) {
        String id = BuiltInRegistries.BLOCK.getKey(zustand.getBlock()).toString();
        boolean drauf = bloecke.contains(id);
        return listenArt.is("Weissliste") ? drauf : !drauf;
    }

    @Override
    public String hudSuffix() {
        return ader.isEmpty() ? null : String.valueOf(ader.size());
    }
}
