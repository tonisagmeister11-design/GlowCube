package net.glowcube.client.module.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.mixin.BlockBehaviourAccessor;
import net.glowcube.client.util.Render3D;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code HoleESP}.
 *
 * <p>Ein "Loch" ist eine Stelle, in der man eine Kristall-Explosion
 * ueberlebt. Die Pruefung des Originals ist genauer als die naheliegende:
 *
 * <ul>
 *   <li>Es zaehlt die Kollisionsflagge des Blocks, nicht seine Form. Eine
 *       Druckplatte hat eine Form, ist aber kein Hindernis.</li>
 *   <li>Es wird zwischen <b>Bedrock</b> (unzerstoerbar), <b>Obsidian</b>
 *       (sprengfest, aber abbaubar) und <b>gemischt</b> unterschieden - das
 *       ist im Kampf ein echter Unterschied und bekommt eigene Farben.</li>
 *   <li>Doppelloecher: eine der vier Seiten darf offen sein, wenn dahinter
 *       ein zweites, ebenso geschuetztes Feld liegt. Dann sind es acht
 *       feste Nachbarn statt fuenf.</li>
 * </ul>
 *
 * <p>Die gesuchte Hoehe ist einstellbar - ein Loch fuer einen geduckten
 * Spieler braucht nur ein Feld, eines zum Stehen zwei.
 *
 * <p>Gerechnet wird wie bisher im Tick, nicht im Bild: die Suche ueber
 * mehrere tausend Felder gehoert nicht in jeden Frame.
 */
public final class HoleEsp extends Module {
    private final NumberSetting waagrecht = register(new NumberSetting("Umkreis",
            "Wie weit zur Seite gesucht wird", 10, 2, 24, 1));
    private final NumberSetting senkrecht = register(new NumberSetting("Hoehe der Suche",
            "Wie weit nach oben und unten gesucht wird", 4, 1, 12, 1));
    private final NumberSetting lochHoehe = register(new NumberSetting("Lochhoehe",
            "Wie viele Felder ueber dem Loch frei sein muessen", 2, 1, 3, 1));
    private final BooleanSetting doppelte = register(new BooleanSetting("Doppelloecher",
            "Auch Loecher aus zwei Feldern zeigen", true));
    private final BooleanSetting spinnweben = register(new BooleanSetting("Spinnweben zulassen",
            "Spinnweben nicht als Hindernis zaehlen", false));
    private final BooleanSetting eigenesAus = register(new BooleanSetting("Eigenes auslassen",
            "Das Feld, in dem man selbst steht, nicht zeigen", true));
    private final NumberSetting kastenHoehe = register(new NumberSetting("Kastenhoehe",
            "Wie hoch der gezeichnete Kasten ist", 1.0, 0.1, 2.0, 0.1));

    private static final int FARBE_BEDROCK = 0xFF3BF0D4;
    private static final int FARBE_OBSIDIAN = 0xFF9B6BFF;
    private static final int FARBE_GEMISCHT = 0xFFFFC53D;

    private record Loch(BlockPos pos, int farbe) {
    }

    private volatile List<Loch> loecher = List.of();

    public HoleEsp() {
        super("HoleESP", "Zeigt Loecher, die Explosionen standhalten", Category.RENDER);
    }

    @Override
    public void onTick() {
        List<Loch> gefunden = new ArrayList<>();
        BlockPos mitte = player().blockPosition();
        int w = waagrecht.getInt();
        int h = senkrecht.getInt();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        for (int dx = -w; dx <= w; dx++) {
            for (int dy = -h; dy <= h; dy++) {
                for (int dz = -w; dz <= w; dz++) {
                    probe.set(mitte.getX() + dx, mitte.getY() + dy, mitte.getZ() + dz);
                    Loch loch = pruefen(probe.immutable());
                    if (loch != null) {
                        gefunden.add(loch);
                    }
                }
            }
        }
        // In einem Zug austauschen, damit das Zeichnen nie eine halb
        // gefuellte Liste sieht.
        loecher = gefunden;
    }

    private Loch pruefen(BlockPos pos) {
        if (!brauchbar(pos)) {
            return null;
        }

        int bedrock = 0;
        int obsidian = 0;
        Direction offen = null;

        for (Direction seite : Direction.values()) {
            if (seite == Direction.UP) {
                continue;
            }
            BlockPos nachbar = pos.relative(seite);
            Block block = level().getBlockState(nachbar).getBlock();
            boolean abbaubar = block.defaultDestroyTime() >= 0.0f;

            if (hatKollision(block) && !abbaubar) {
                bedrock++;
            } else if (block.getExplosionResistance() >= 600.0f && abbaubar) {
                obsidian++;
            } else if (seite == Direction.DOWN) {
                // Unten muss immer zu sein - sonst faellt man heraus.
                return null;
            } else if (doppelte.get() && offen == null && brauchbar(nachbar)) {
                // Eine offene Seite darf sein, wenn dahinter ein zweites,
                // ebenso geschuetztes Feld liegt.
                for (Direction weiter : Direction.values()) {
                    if (weiter == seite.getOpposite() || weiter == Direction.UP) {
                        continue;
                    }
                    Block dahinter = level().getBlockState(nachbar.relative(weiter)).getBlock();
                    boolean weich = dahinter.defaultDestroyTime() >= 0.0f;
                    if (hatKollision(dahinter) && !weich) {
                        bedrock++;
                    } else if (dahinter.getExplosionResistance() >= 600.0f && weich) {
                        obsidian++;
                    } else {
                        return null;
                    }
                }
                offen = seite;
            } else {
                return null;
            }
        }

        if (obsidian + bedrock == 5 && offen == null) {
            return new Loch(pos, farbe(obsidian, bedrock, 5));
        }
        if (obsidian + bedrock == 8 && doppelte.get() && offen != null) {
            return new Loch(pos, farbe(obsidian, bedrock, 8));
        }
        return null;
    }

    private static int farbe(int obsidian, int bedrock, int voll) {
        if (obsidian == voll) {
            return FARBE_OBSIDIAN;
        }
        if (bedrock == voll) {
            return FARBE_BEDROCK;
        }
        return FARBE_GEMISCHT;
    }

    private boolean brauchbar(BlockPos pos) {
        if (eigenesAus.get() && player().blockPosition().equals(pos)) {
            return false;
        }
        Block block = level().getBlockState(pos).getBlock();
        if (!spinnweben.get() && block == Blocks.COBWEB) {
            return false;
        }
        if (hatKollision(block)) {
            return false;
        }
        for (int i = 0; i < lochHoehe.getInt(); i++) {
            if (hatKollision(level().getBlockState(pos.above(i)).getBlock())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hatKollision(Block block) {
        return ((BlockBehaviourAccessor) block).glowcube$hatKollision();
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        for (Loch loch : loecher) {
            BlockPos p = loch.pos();
            Render3D.box(context, new AABB(
                    p.getX(), p.getY(), p.getZ(),
                    p.getX() + 1.0, p.getY() + kastenHoehe.get(), p.getZ() + 1.0),
                    loch.farbe(), true);
        }
    }

    @Override
    public void onDisable() {
        loecher = List.of();
    }

    @Override
    public String hudSuffix() {
        return loecher.isEmpty() ? null : String.valueOf(loecher.size());
    }
}
