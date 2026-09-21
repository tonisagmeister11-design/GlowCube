package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Einfache Fortbewegung zu einem Ziel, mit Hindernis-Handhabung.
 *
 * <p><b>Was das ist - und was nicht.</b> Das ist <em>kein</em> A*-Wegfinder
 * wie Baritone. Es kennt keine Karte, es plant keinen Weg um einen See herum,
 * es findet nicht aus einer Hoehle heraus. Es geht stur in Richtung Ziel und
 * loest dabei genau die Faelle, die im offenen Gelaende staendig vorkommen:
 *
 * <ul>
 *   <li><b>Laufen.</b> Blick aufs Ziel drehen und vorwaerts druecken.</li>
 *   <li><b>Stufe.</b> Ein Block hoch vor den Fuessen - drueberspringen.</li>
 *   <li><b>Wand.</b> Zwei oder mehr Bloecke hoch - hochbauen (Turm): springen
 *       und im Steigen einen Block unter sich setzen, bis man oben ist.</li>
 *   <li><b>Luecke.</b> Loch oder Abgrund voraus - ueberbruecken: einen Block
 *       in die Luecke setzen und erst dann weiter.</li>
 * </ul>
 *
 * <p>Zum Bauen (Turm, Bruecke) braucht es einen vollen Block in der Hotbar -
 * Pflasterstein, Erde, was da ist. Ohne bleibt es an Wand und Luecke stehen,
 * statt in den Tod zu laufen.
 *
 * <p>In verwinkeltem Gelaende (Ueberhaenge, tiefe Schluchten, Wasser) bleibt
 * es haengen. Das ist die ehrliche Grenze ohne echten Wegfinder.
 */
public final class Navigation {
    /** Wie nah ans Ziel, bevor es als erreicht gilt (in Bloecken, quadriert). */
    private static final double ANKUNFT = 2.25;

    // --- Zustand des A*-Weges (nur laufeZu nutzt das) --------------------
    private static java.util.List<BlockPos> pfad;
    private static int pfadIndex;
    private static BlockPos pfadZiel;
    private static int neuRechnen;       // Sperre, damit nicht jeden Tick neu geplant wird
    private static BlockPos letztePos;   // fuer die Steckenerkennung
    private static int stehtFest;

    private Navigation() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Alle uebernommenen Bewegungstasten loslassen. */
    public static void stopp() {
        mc().options.keyUp.setDown(false);
        mc().options.keyJump.setDown(false);
    }

    /**
     * Wie {@link #laufe(Vec3)}, aber mit echtem Wegfinder: erst plant
     * {@link PfadFinder} einen Weg zum Ziel, dann werden dessen Wegpunkte der
     * Reihe nach angesteuert. Findet der Wegfinder nichts (zu verwinkelt, Ziel
     * eingemauert), faellt es auf das gerade-drauf-zu von {@link #laufe(Vec3)}
     * zurueck. Liefert true, wenn das Ziel erreicht ist.
     */
    public static boolean laufeZu(BlockPos ziel) {
        var spieler = mc().player;
        if (spieler == null || mc().level == null) {
            return false;
        }

        // Angekommen? (waagrecht nah genug und hoechstens gut einen Block Hoehe daneben)
        double dx = ziel.getX() + 0.5 - spieler.getX();
        double dz = ziel.getZ() + 0.5 - spieler.getZ();
        if (dx * dx + dz * dz <= ANKUNFT && Math.abs(ziel.getY() - spieler.getY()) <= 1.5) {
            stopp();
            pfad = null;
            return true;
        }

        if (neuRechnen > 0) {
            neuRechnen--;
        }
        steckenPruefen(spieler.blockPosition());

        boolean zielNeu = pfadZiel == null || !pfadZiel.equals(ziel);
        boolean fertig = pfad != null && pfadIndex >= pfad.size();
        if (pfad == null || zielNeu || fertig) {
            if (zielNeu || neuRechnen <= 0) {
                pfad = PfadFinder.finde(spieler.blockPosition(), ziel);
                pfadIndex = 0;
                pfadZiel = ziel;
                neuRechnen = 40;   // hoechstens etwa alle zwei Sekunden neu planen
            }
        }

        // Kein Weg gefunden: gerade drauf zu (baut Stufen/Bruecken selbst).
        if (pfad == null || pfad.isEmpty()) {
            return laufe(Vec3.atCenterOf(ziel));
        }

        if (pfadIndex >= pfad.size()) {
            pfadIndex = pfad.size() - 1;
        }
        BlockPos wegpunkt = pfad.get(pfadIndex);
        boolean erreicht = laufe(Vec3.atCenterOf(wegpunkt));
        if (erreicht && pfadIndex < pfad.size() - 1) {
            pfadIndex++;
        }
        return false;
    }

    /** Bewegt sich der Spieler nicht mehr, wird beim naechsten Mal neu geplant. */
    private static void steckenPruefen(BlockPos jetzt) {
        if (letztePos != null && letztePos.equals(jetzt)) {
            stehtFest++;
            if (stehtFest > 25) {           // gut eine Sekunde ohne Fortschritt
                neuRechnen = 0;             // Neuplanung freigeben
                mc().options.keyJump.setDown(true);   // einmal huepfen, oft loest das
                stehtFest = 0;
            }
        } else {
            stehtFest = 0;
        }
        letztePos = jetzt;
    }

    /**
     * Ein Schritt Richtung Ziel. Liefert true, wenn das Ziel erreicht ist.
     */
    public static boolean laufe(Vec3 ziel) {
        var spieler = mc().player;
        if (spieler == null) {
            return false;
        }

        double dx = ziel.x - spieler.getX();
        double dz = ziel.z - spieler.getZ();
        double waagrechtQuadrat = dx * dx + dz * dz;
        if (waagrechtQuadrat <= ANKUNFT) {
            stopp();
            return true;
        }

        // Blick aufs Ziel - so laeuft "vorwaerts" auch dorthin.
        float gier = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        spieler.setYRot(gier);

        // Der Block direkt vor den Fuessen, in Laufrichtung.
        double laenge = Math.sqrt(waagrechtQuadrat);
        double nx = dx / laenge;
        double nz = dz / laenge;
        BlockPos fuss = spieler.blockPosition();
        BlockPos vorFuss = BlockPos.containing(spieler.getX() + nx * 0.6, spieler.getY(),
                spieler.getZ() + nz * 0.6);
        BlockPos vorKopf = vorFuss.above();
        BlockPos unterVorFuss = vorFuss.below();

        // Wasser oder Lava voraus - stehenbleiben statt hineinlaufen. Ohne
        // echten Wegfinder ist Anhalten die einzige sichere Antwort; sonst
        // laeuft der Spieler in die Lava.
        if (fluessig(vorFuss) || fluessig(unterVorFuss)) {
            stopp();
            return false;
        }

        boolean wandUnten = !frei(vorFuss);
        boolean wandOben = !frei(vorKopf);
        boolean lueckeVoraus = frei(vorFuss) && frei(unterVorFuss) && frei(unterVorFuss.below());

        // Immer erst mal vorwaerts.
        mc().options.keyUp.setDown(true);
        mc().options.keyJump.setDown(false);

        if (wandUnten && wandOben) {
            // Zwei-Block-Wand: hochbauen.
            mc().options.keyUp.setDown(false);
            turmSchritt();
            return false;
        }
        if (wandUnten) {
            // Ein-Block-Stufe: drueberspringen.
            mc().options.keyJump.setDown(true);
            return false;
        }
        if (lueckeVoraus) {
            // Luecke: nicht hineinlaufen, erst ueberbruecken.
            mc().options.keyUp.setDown(false);
            bruecke(unterVorFuss);
            return false;
        }
        return false;
    }

    /**
     * Ein Block ist frei begehbar, wenn er keine feste Kollision hat - Luft,
     * Gras, Blumen zaehlen als frei.
     */
    private static boolean frei(BlockPos pos) {
        return mc().level.getBlockState(pos).getCollisionShape(mc().level, pos).isEmpty();
    }

    /** Wasser oder Lava - hat keine feste Kollision, ist aber toedlich/nass. */
    private static boolean fluessig(BlockPos pos) {
        return !mc().level.getBlockState(pos).getFluidState().isEmpty();
    }

    /**
     * Turm-Schritt: springen und im Steigen einen Block unter sich setzen.
     * Zusammen hebt das den Spieler eine Ebene an - so oft wiederholt, bis
     * die Wand ueberwunden ist.
     */
    private static void turmSchritt() {
        var spieler = mc().player;
        mc().options.keyJump.setDown(true);
        // Nahe am Scheitel, wenn die Aufwaertsbewegung fast steht: Block unter
        // die Fuesse setzen.
        if (!spieler.onGround() && spieler.getDeltaMovement().y > 0.0 && spieler.getDeltaMovement().y < 0.15) {
            BlockPos unten = spieler.blockPosition().below();
            if (frei(unten)) {
                setze(unten);
            }
        }
    }

    /** Einen Block in die Luecke setzen, damit man weitergehen kann. */
    private static void bruecke(BlockPos luecke) {
        if (frei(luecke)) {
            setze(luecke);
        }
    }

    private static void setze(BlockPos pos) {
        FindItemResult fund = InvUtils.findeInHotbar(Navigation::istBauklotz);
        if (!fund.found()) {
            return;
        }
        BlockUtils.setzen(pos, fund, true, 40, true, true);
    }

    /** Ein tauglicher Bauklotz: ein voller, nicht fallender Block. */
    private static boolean istBauklotz(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
        // Ein paar, die sich sicher setzen lassen und nicht fallen.
        return id.equals("cobblestone") || id.equals("dirt") || id.equals("stone")
                || id.equals("cobbled_deepslate") || id.equals("netherrack")
                || id.endsWith("_planks");
    }
}
