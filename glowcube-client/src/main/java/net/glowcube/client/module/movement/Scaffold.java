package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.BlockUtils;
import net.glowcube.client.util.FindItemResult;
import net.glowcube.client.util.InvUtils;
import net.glowcube.client.util.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code Scaffold}.
 *
 * <p>Der Kern ist die Wahl der Stelle, und die ist im Original genauer, als
 * man denkt: erst der Punkt unter dem Spieler, aber um die eigene
 * Geschwindigkeit nach vorn verschoben (sonst baut man immer einen halben
 * Block zu spaet); dann, wenn dort kein Halt ist, die naechstgelegene
 * Stelle im Umkreis, nach Abstand zum Wunschziel sortiert. Dazu die
 * Sonderfaelle: geduckt eine Stufe tiefer, nie ueber der eigenen Fusshoehe.
 *
 * <p>Der Schnellturm ist ebenfalls uebernommen - inklusive des Teils, den
 * man leicht uebersieht: ist ueber dem Kopf schon ein Block, wird der
 * Spieler nach unten auf die gesetzte Flaeche gezogen und als "am Boden"
 * gemeldet. Ohne das haengt man zwischen zwei Bloecken fest.
 *
 * <p><b>Nicht uebernommen:</b> das Einzeichnen der gesetzten Bloecke -
 * GlowCube zeichnet ueber ein anderes System.
 */
public final class Scaffold extends Module {
    private final BlockListSetting bloecke = register(new BlockListSetting("Bloecke",
            "Welche Bloecke gesetzt werden duerfen"));
    private final ModeSetting listenArt = register(new ModeSetting("Listenart",
            "Schwarz sperrt die Liste, Weiss erlaubt nur sie",
            "Schwarzliste", "Schwarzliste", "Weissliste"));
    private final BooleanSetting schnellturm = register(new BooleanSetting("Schnellturm",
            "Beim Halten der Sprungtaste zuegig nach oben bauen", false));
    private final NumberSetting turmTempo = register(new NumberSetting("Turmtempo",
            "Wie schnell dabei gestiegen wird", 0.5, 0.0, 1.0, 0.05));
    private final BooleanSetting turmInBewegung = register(new BooleanSetting("Turm in Bewegung",
            "Auch tuermen, waehrend man laeuft", false));
    private final BooleanSetting nurBeiKlick = register(new BooleanSetting("Nur bei Klick",
            "Nur setzen, solange die rechte Maustaste gehalten wird", false));
    private final BooleanSetting schwingen = register(new BooleanSetting("Schwingen",
            "Die Hand sichtbar bewegen", false));
    private final BooleanSetting tauschen = register(new BooleanSetting("Umgreifen",
            "Von selbst auf einen passenden Block wechseln", true));
    private final BooleanSetting drehen = register(new BooleanSetting("Drehen",
            "Dem Server die passende Blickrichtung melden", true));
    private final BooleanSetting inDieLuft = register(new BooleanSetting("Luftbau",
            "Auch ohne Nachbarblock setzen - laesst sich nur auf eigenen Servern nutzen", false));
    private final NumberSetting vorlauf = register(new NumberSetting("Vorlauf",
            "Wie weit vor den Fuessen gesetzt wird", 0.0, 0.0, 1.0, 0.05));
    private final NumberSetting reichweite = register(new NumberSetting("Umkreis",
            "Wie weit entfernt eine Ersatzstelle liegen darf", 4.0, 0.0, 8.0, 0.5));
    private final NumberSetting radius = register(new NumberSetting("Luftbau-Radius",
            "Wie breit im Luftbau gesetzt wird", 0.0, 0.0, 6.0, 1.0));
    private final NumberSetting proTick = register(new NumberSetting("Bloecke je Tick",
            "Wie viele Bloecke im Luftbau gleichzeitig", 3, 1, 12, 1));

    private final BlockPos.MutableBlockPos stelle = new BlockPos.MutableBlockPos();

    public Scaffold() {
        super("Scaffold", "Baut den Boden unter dir mit", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (nurBeiKlick.get() && !mc.options.keyUse.isDown()) {
            return;
        }

        Vec3 unterFuss = player().position().add(player().getDeltaMovement()).add(0.0, -0.75, 0.0);
        if (inDieLuft.get()) {
            stelle.set(unterFuss.x, unterFuss.y, unterFuss.z);
        } else {
            Vec3 pos = player().position();
            // Der Vorlauf greift nur, wenn man nicht gerade tuermt und
            // wirklich auf etwas steht - sonst baut man ins Leere voraus.
            if (vorlauf.get() != 0.0 && !tuermt()
                    && !level().getBlockState(player().blockPosition().below())
                            .getCollisionShape(level(), player().blockPosition()).isEmpty()) {
                Vec3 richtung = Vec3.directionFromRotation(0.0f, player().getYRot())
                        .multiply(vorlauf.get(), 0.0, vorlauf.get());
                if (mc.options.keyUp.isDown()) {
                    pos = pos.add(richtung.x, 0.0, richtung.z);
                }
                if (mc.options.keyDown.isDown()) {
                    pos = pos.add(-richtung.x, 0.0, -richtung.z);
                }
                if (mc.options.keyLeft.isDown()) {
                    pos = pos.add(richtung.z, 0.0, -richtung.x);
                }
                if (mc.options.keyRight.isDown()) {
                    pos = pos.add(-richtung.z, 0.0, richtung.x);
                }
            }
            stelle.set(pos.x, unterFuss.y, pos.z);
        }

        // Geduckt eine Stufe tiefer - so kommt man ueber Kanten hinunter,
        // ohne dass sich der Boden gleich wieder schliesst.
        if (mc.options.keyShift.isDown() && !mc.options.keyJump.isDown()
                && player().getY() + unterFuss.y > -1.0) {
            stelle.setY(stelle.getY() - 1);
        }
        // Nie ueber der eigenen Fusshoehe bauen.
        if (stelle.getY() >= player().blockPosition().getY()) {
            stelle.setY(player().blockPosition().getY() - 1);
        }
        BlockPos wunsch = stelle.immutable();

        if (!inDieLuft.get() && BlockUtils.setzSeite(stelle) == null) {
            BlockPos ersatz = naechsteMoegliche(wunsch);
            if (ersatz == null) {
                return;
            }
            stelle.set(ersatz);
        }

        if (inDieLuft.get()) {
            luftbau();
        } else {
            setzen(stelle);
        }

        schnellturmSchritt();
    }

    /**
     * Steht unter den Fuessen nichts, an das sich ein Block anlehnen kann,
     * wird die naechstgelegene Stelle mit Halt gesucht - sortiert nach
     * Abstand zum eigentlichen Ziel, nicht zum Spieler. So waechst der Weg
     * in die richtige Richtung.
     */
    private BlockPos naechsteMoegliche(BlockPos wunsch) {
        List<BlockPos> treffer = new ArrayList<>();
        double weite = reichweite.get();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        for (int x = (int) (player().getX() - weite); x < player().getX() + weite; x++) {
            for (int z = (int) (player().getZ() - weite); z < player().getZ() + weite; z++) {
                int vonY = (int) Math.max(level().getMinY(), player().getY() - weite);
                int bisY = (int) Math.min(level().getMaxY(), player().getY() + weite);
                for (int y = vonY; y < bisY; y++) {
                    probe.set(x, y, z);
                    if (BlockUtils.setzSeite(probe) == null) {
                        continue;
                    }
                    if (!BlockUtils.kannSetzen(probe)) {
                        continue;
                    }
                    // Sechs Bloecke im Quadrat - Vanillas Reichweite.
                    var seite = BlockUtils.naechsteSetzSeite(probe);
                    if (seite == null) {
                        continue;
                    }
                    if (player().getEyePosition()
                            .distanceToSqr(Vec3.atCenterOf(probe.relative(seite))) > 36.0) {
                        continue;
                    }
                    treffer.add(probe.immutable());
                }
            }
        }
        if (treffer.isEmpty()) {
            return null;
        }
        treffer.sort(Comparator.comparingDouble(p -> p.distSqr(wunsch)));
        return treffer.get(0);
    }

    private void luftbau() {
        List<BlockPos> ziele = new ArrayList<>();
        double r = radius.get();
        for (int x = (int) (stelle.getX() - r); x <= stelle.getX() + r; x++) {
            for (int z = (int) (stelle.getZ() - r); z <= stelle.getZ() + r; z++) {
                BlockPos pos = new BlockPos(x, stelle.getY(), z);
                if (player().position().distanceTo(Vec3.atCenterOf(pos)) <= r
                        || (x == stelle.getX() && z == stelle.getZ())) {
                    ziele.add(pos);
                }
            }
        }
        if (ziele.isEmpty()) {
            return;
        }
        ziele.sort(Comparator.comparingDouble(PlayerUtils::abstandQuadrat));
        int gesetzt = 0;
        for (BlockPos pos : ziele) {
            if (setzen(pos)) {
                gesetzt++;
            }
            if (gesetzt >= proTick.getInt()) {
                break;
            }
        }
    }

    private void schnellturmSchritt() {
        FindItemResult fund = InvUtils.findeInHotbar(stack -> tauglich(stack, stelle));
        if (!schnellturm.get() || !mc.options.keyJump.isDown() || mc.options.keyShift.isDown()
                || !fund.found() || (!tauschen.get() && fund.hand() == null)) {
            return;
        }

        Vec3 tempo = player().getDeltaMovement();
        AABB kasten = player().getBoundingBox();
        boolean freiDarueber = !level().getBlockCollisions(player(), kasten.move(0.0, 1.0, 0.0))
                .iterator().hasNext();

        if (freiDarueber) {
            // Ueber dem Kopf ist Platz: anheben, damit der naechste Block
            // darunter passt.
            if (turmInBewegung.get() || !PlayerUtils.istInBewegung()) {
                tempo = new Vec3(tempo.x, turmTempo.get(), tempo.z);
            }
            player().setDeltaMovement(tempo);
        } else {
            // Ueber dem Kopf ist zu: genau auf die gesetzte Flaeche
            // herunterziehen, sonst klemmt man dazwischen.
            player().setDeltaMovement(tempo.x,
                    Math.ceil(player().getY()) - player().getY(), tempo.z);
            player().setOnGround(true);
        }
    }

    /** Ob gerade wirklich getuermt wird - der Vorlauf haengt daran. */
    public boolean tuermt() {
        FindItemResult fund = InvUtils.findeInHotbar(stack -> tauglich(stack, stelle));
        return isEnabled() && schnellturm.get() && mc.options.keyJump.isDown()
                && !mc.options.keyShift.isDown()
                && (turmInBewegung.get() || !PlayerUtils.istInBewegung())
                && fund.found() && (tauschen.get() || fund.hand() != null);
    }

    /**
     * Ein tauglicher Block ist ein voller Block, der nicht faellt. Sand als
     * Bruecke waere ein kurzes Vergnuegen.
     */
    private boolean tauglich(ItemStack stack, BlockPos pos) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        boolean drauf = bloecke.contains(id);
        if (listenArt.is("Weissliste") ? !drauf : drauf) {
            return false;
        }
        if (!Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(level(), pos))) {
            return false;
        }
        return !(block instanceof FallingBlock) || !FallingBlock.isFree(level().getBlockState(pos));
    }

    private boolean setzen(BlockPos pos) {
        FindItemResult fund = InvUtils.findeInHotbar(stack -> tauglich(stack, pos));
        if (!fund.found()) {
            return false;
        }
        if (fund.hand() == null && !tauschen.get()) {
            return false;
        }
        return BlockUtils.setzen(pos, fund, drehen.get(), 50, schwingen.get(), true);
    }
}
