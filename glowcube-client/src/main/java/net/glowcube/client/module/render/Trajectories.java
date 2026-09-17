package net.glowcube.client.module.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.ColorUtil;
import net.glowcube.client.util.Render3D;
import net.glowcube.client.util.Theme;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code Trajectories} und
 * dessen {@code ProjectileEntitySimulator}.
 *
 * <p>Das Wertvolle daran ist nicht das Zeichnen, sondern die Tabelle und die
 * Reihenfolge. Jedes Wurfgeschoss hat eigene Zahlen fuer Anfangstempo,
 * Neigungszuschlag, Schwerkraft und Luft- beziehungsweise Wasserwiderstand -
 * und, was man leicht uebersieht, eine eigene <em>Reihenfolge</em>, in der
 * das Spiel sie je Tick anwendet:
 *
 * <ul>
 *   <li>Geworfenes (Ei, Perle, Schneeball, Trank): erst Schwerkraft, dann
 *       Widerstand, dann Bewegung.</li>
 *   <li>Pfeile und Dreizack: erst Bewegung, dann Widerstand, dann
 *       Schwerkraft.</li>
 *   <li>Rakete und Angelhaken: erst Schwerkraft, dann Bewegung, dann
 *       Widerstand.</li>
 * </ul>
 *
 * <p>Wer das vertauscht, bekommt eine Kurve, die fast stimmt und auf dreissig
 * Bloecken einen halben daneben liegt. Deshalb steht sie hier genau so.
 *
 * <p><b>Nicht uebernommen:</b> der zweite und dritte Pfeil bei Multischuss,
 * die Einfaerbung nach Trefferart, und die Vorschau fuer andere Spieler.
 */
public final class Trajectories extends Module {
    private final BooleanSetting genau = register(new BooleanSetting("Genau",
            "Die eigene Geschwindigkeit einrechnen", true));
    private final BooleanSetting abgeschossene = register(new BooleanSetting("Fliegende",
            "Auch Bahnen bereits fliegender Geschosse zeichnen", true));
    private final NumberSetting schritte = register(new NumberSetting("Schritte",
            "Wie weit vorausgerechnet wird", 300, 20, 600, 10));

    /**
     * Die Zahlen des Originals, unveraendert. {@code tempo} ist das
     * Anfangstempo, {@code neigung} ein Zuschlag auf den Blickwinkel (Traenke
     * fliegen bewusst hoeher), dann Schwerkraft, Luft- und Wasserwiderstand.
     */
    private record Bewegung(float tempo, float neigung, double schwerkraft,
                            float luft, float wasser, Art art) {
    }

    /** In welcher Reihenfolge Schwerkraft, Widerstand und Bewegung greifen. */
    private enum Art {
        GEWORFEN,
        PFEIL,
        SONSTIGES
    }

    private static final Bewegung EI = new Bewegung(1.5f, 0, 0.03, 0.99f, 0.8f, Art.GEWORFEN);
    private static final Bewegung PERLE = new Bewegung(1.5f, 0, 0.03, 0.99f, 0.8f, Art.GEWORFEN);
    private static final Bewegung SCHNEEBALL = new Bewegung(1.5f, 0, 0.03, 0.99f, 0.8f, Art.GEWORFEN);
    private static final Bewegung ERFAHRUNG = new Bewegung(0.7f, -20, 0.07, 0.99f, 0.8f, Art.GEWORFEN);
    private static final Bewegung VERWEILTRANK = new Bewegung(0.5f, -20, 0.05, 0.99f, 0.8f, Art.GEWORFEN);
    private static final Bewegung WURFTRANK = new Bewegung(0.5f, -20, 0.05, 0.99f, 0.8f, Art.GEWORFEN);
    private static final Bewegung WINDLADUNG = new Bewegung(1.5f, 0, 0, 1, 1, Art.GEWORFEN);
    private static final Bewegung PFEIL = new Bewegung(0, 0, 0.05, 0.99f, 0.6f, Art.PFEIL);
    private static final Bewegung DREIZACK = new Bewegung(2.5f, 0, 0.05, 0.99f, 0.99f, Art.PFEIL);
    private static final Bewegung RAKETE = new Bewegung(0, 0, 0, 1, 1, Art.SONSTIGES);

    private final List<Vec3> bahn = new ArrayList<>();

    public Trajectories() {
        super("Trajectories", "Zeigt, wo Pfeil, Perle oder Trank landen", Category.RENDER);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (!inGame()) {
            return;
        }

        ItemStack inHand = player().getMainHandItem();
        Bewegung daten = zuItem(inHand);
        if (daten == null) {
            inHand = player().getOffhandItem();
            daten = zuItem(inHand);
        }
        if (daten != null) {
            rechnen(daten, inHand);
            zeichnen(context, Theme.accentStart());
        }

        if (abgeschossene.get()) {
            for (Entity wesen : level().entitiesForRendering()) {
                if (!(wesen instanceof Projectile geschoss)) {
                    continue;
                }
                Bewegung art = zuWesen(geschoss);
                if (art == null) {
                    continue;
                }
                rechnenAbFlug(geschoss, art);
                zeichnen(context, ColorUtil.fade(Theme.accentEnd(), 0.55f));
            }
        }
    }

    // --------------------------------------------------------------- Tabelle

    private Bewegung zuItem(ItemStack stack) {
        if (stack.getItem() instanceof BowItem) {
            // Der Bogen wird staerker, je laenger man zieht. Voll gespannt
            // sind es drei; unter einem Zehntel spannt man noch gar nicht.
            int gehalten = player().getTicksUsingItem();
            float ladung = BowItem.getPowerForTime(gehalten);
            if (ladung <= 0.1f) {
                ladung = 1.0f;
            }
            return new Bewegung(ladung * 3.0f, PFEIL.neigung(), PFEIL.schwerkraft(),
                    PFEIL.luft(), PFEIL.wasser(), Art.PFEIL);
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return new Bewegung(3.15f, PFEIL.neigung(), PFEIL.schwerkraft(),
                    PFEIL.luft(), PFEIL.wasser(), Art.PFEIL);
        }
        if (stack.is(Items.TRIDENT)) {
            return DREIZACK;
        }
        if (stack.is(Items.SNOWBALL)) {
            return SCHNEEBALL;
        }
        if (stack.is(Items.EGG)) {
            return EI;
        }
        if (stack.is(Items.ENDER_PEARL)) {
            return PERLE;
        }
        if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            return ERFAHRUNG;
        }
        if (stack.is(Items.SPLASH_POTION)) {
            return WURFTRANK;
        }
        if (stack.is(Items.LINGERING_POTION)) {
            return VERWEILTRANK;
        }
        if (stack.is(Items.WIND_CHARGE)) {
            return WINDLADUNG;
        }
        if (stack.is(Items.FIREWORK_ROCKET)) {
            return RAKETE;
        }
        return null;
    }

    private Bewegung zuWesen(Projectile geschoss) {
        String typ = geschoss.getType().toString();
        if (typ.contains("arrow")) {
            return PFEIL;
        }
        if (typ.contains("trident")) {
            return DREIZACK;
        }
        if (typ.contains("ender_pearl")) {
            return PERLE;
        }
        if (typ.contains("snowball")) {
            return SCHNEEBALL;
        }
        if (typ.contains("egg")) {
            return EI;
        }
        if (typ.contains("experience_bottle")) {
            return ERFAHRUNG;
        }
        if (typ.contains("potion")) {
            return WURFTRANK;
        }
        return null;
    }

    // -------------------------------------------------------------- Rechnung

    private void rechnen(Bewegung daten, ItemStack stack) {
        bahn.clear();
        double gier = Math.toRadians(player().getYRot());
        double neigung = Math.toRadians(player().getXRot() + daten.neigung());

        Vec3 ort = player().getEyePosition().add(0.0, -0.1, 0.0);
        Vec3 tempo = new Vec3(
                -Math.sin(gier) * Math.cos(Math.toRadians(player().getXRot())),
                -Math.sin(neigung),
                Math.cos(gier) * Math.cos(Math.toRadians(player().getXRot())))
                .normalize().scale(daten.tempo());

        if (genau.get()) {
            Vec3 eigen = player().getDeltaMovement();
            tempo = tempo.add(eigen.x, player().onGround() ? 0.0 : eigen.y, eigen.z);
        }

        simulieren(ort, tempo, daten);
    }

    private void rechnenAbFlug(Entity geschoss, Bewegung daten) {
        bahn.clear();
        simulieren(geschoss.position(), geschoss.getDeltaMovement(), daten);
    }

    /**
     * Ein Tick der Vanilla-Physik, in der Reihenfolge, die zur Art gehoert.
     * Abgebrochen wird beim ersten Treffer, unter der Welt oder am Rand des
     * geladenen Gebiets.
     */
    private void simulieren(Vec3 ort, Vec3 tempo, Bewegung daten) {
        bahn.add(ort);
        int hoechstens = schritte.getInt();

        for (int i = 0; i < hoechstens; i++) {
            Vec3 vorher = ort;
            boolean imWasser = !level().getBlockState(
                    net.minecraft.core.BlockPos.containing(ort)).getFluidState().isEmpty();
            float widerstand = imWasser ? daten.wasser() : daten.luft();

            switch (daten.art()) {
                case GEWORFEN -> {
                    tempo = tempo.subtract(0.0, daten.schwerkraft(), 0.0).scale(widerstand);
                    ort = ort.add(tempo);
                }
                case PFEIL -> {
                    ort = ort.add(tempo);
                    tempo = tempo.scale(widerstand).subtract(0.0, daten.schwerkraft(), 0.0);
                }
                default -> {
                    tempo = tempo.subtract(0.0, daten.schwerkraft(), 0.0);
                    ort = ort.add(tempo);
                    tempo = tempo.scale(widerstand);
                }
            }

            if (ort.y < level().getMinY()) {
                return;
            }
            if (!level().getChunkSource().hasChunk((int) (ort.x / 16.0), (int) (ort.z / 16.0))) {
                return;
            }

            bahn.add(ort);

            BlockHitResult treffer = level().clip(new ClipContext(
                    vorher, ort, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player()));
            if (treffer.getType() != HitResult.Type.MISS) {
                bahn.set(bahn.size() - 1, treffer.getLocation());
                return;
            }
        }
    }

    private void zeichnen(WorldRenderContext context, int farbe) {
        for (int i = 1; i < bahn.size(); i++) {
            Render3D.line(context, bahn.get(i - 1), bahn.get(i), farbe);
        }
        if (bahn.size() > 1) {
            Vec3 ende = bahn.get(bahn.size() - 1);
            Render3D.box(context, new AABB(ende.subtract(0.15, 0.15, 0.15),
                    ende.add(0.15, 0.15, 0.15)), farbe, true);
        }
    }
}
