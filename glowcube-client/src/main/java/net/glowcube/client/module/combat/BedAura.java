package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.BlockUtils;
import net.glowcube.client.util.DamageUtils;
import net.glowcube.client.util.FindItemResult;
import net.glowcube.client.util.Ids;
import net.glowcube.client.util.InvUtils;
import net.glowcube.client.util.Ziele;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * BedAura: im Nether und im End explodieren Betten, sobald man sie benutzt.
 * Dieses Modul legt ein Bett direkt neben das Ziel und zuendet es.
 *
 * <p>Ein Bett ist zwei Bloecke lang: das Fussende liegt dort, wo man klickt,
 * das Kopfende in Blickrichtung davor. Darum dreht sich das Modul vor dem
 * Legen so, dass das Kopfende genau neben das Ziel kommt, und meldet dem
 * Server diese Blickrichtung vorher eigens. Gezuendet wird am Kopfende.
 */
public final class BedAura extends Module {
    private static final Direction[] SEITEN = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final NumberSetting reichweite = register(new NumberSetting("Reichweite",
            "Wie weit Betten gelegt und gezuendet werden", 4.5, 2.0, 6.0, 0.5));
    private final NumberSetting zielReichweite = register(new NumberSetting("Zielreichweite",
            "Wie weit nach Zielen gesucht wird", 10, 4, 16, 1));
    private final NumberSetting minSchaden = register(new NumberSetting("Min Schaden",
            "So viel muss das Ziel mindestens abbekommen", 6, 0, 36, 0.5));
    private final NumberSetting maxSelbst = register(new NumberSetting("Max Selbstschaden",
            "So viel darf man selbst hoechstens abbekommen", 8, 0, 36, 0.5));
    private final NumberSetting pause = register(new NumberSetting("Pause",
            "Ticks zwischen zwei Betten", 8, 1, 40, 1));
    private final BooleanSetting spieler = register(new BooleanSetting("Spieler", "Spieler angreifen", true));
    private final BooleanSetting monster = register(new BooleanSetting("Monster", "Monster angreifen", false));
    private final BooleanSetting nurWoEsKnallt = register(new BooleanSetting("Nur Nether/End",
            "Nur dort, wo Betten wirklich explodieren", true));
    private final BooleanSetting nichtSterben = register(new BooleanSetting("Nicht sterben",
            "Nie etwas tun, das einen selbst toeten wuerde", true));

    private int warten;
    private LivingEntity ziel;

    public BedAura() {
        super("BedAura", "Legt Betten neben das Ziel und sprengt sie (Nether/End)", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (nurWoEsKnallt.get() && level().dimension() != Level.NETHER && level().dimension() != Level.END) {
            ziel = null;
            return;
        }
        if (warten > 0) {
            warten--;
            return;
        }
        ziel = Ziele.naechstes(zielReichweite.get(), spieler.get(), monster.get());
        if (ziel == null) {
            return;
        }
        if (zuenden()) {
            warten = pause.getInt();
            return;
        }
        if (legen()) {
            // Im naechsten Tick zuenden.
            warten = 1;
        }
    }

    private boolean lohnt(Vec3 knall) {
        if (DamageUtils.bettSchaden(ziel, knall) < minSchaden.get()) {
            return false;
        }
        float selbst = player().isCreative() || player().isSpectator() ? 0 : DamageUtils.bettSchaden(player(), knall);
        if (selbst > maxSelbst.get()) {
            return false;
        }
        return !nichtSterben.get() || selbst < player().getHealth() + player().getAbsorptionAmount();
    }

    /** Ein liegendes Bett in Reichweite zuenden, wenn es das Ziel genug trifft. */
    private boolean zuenden() {
        double r = reichweite.get();
        int ri = (int) Math.ceil(r);
        BlockPos mitte = player().blockPosition();
        Vec3 auge = player().getEyePosition();
        BlockPos bestes = null;
        double besterAbstand = Double.MAX_VALUE;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    BlockPos b = mitte.offset(dx, dy, dz);
                    if (!Ids.block(level().getBlockState(b)).endsWith("_bed")) {
                        continue;
                    }
                    Vec3 knall = Vec3.atCenterOf(b);
                    if (auge.distanceTo(knall) > r || !lohnt(knall)) {
                        continue;
                    }
                    double abstand = knall.distanceToSqr(ziel.position());
                    if (abstand < besterAbstand) {
                        besterAbstand = abstand;
                        bestes = b;
                    }
                }
            }
        }
        if (bestes == null) {
            return false;
        }
        BlockUtils.benutzen(new BlockHitResult(Vec3.atCenterOf(bestes), Direction.UP, bestes, false),
                InteractionHand.MAIN_HAND, true);
        return true;
    }

    private boolean legen() {
        FindItemResult bett = InvUtils.findeInHotbar(stack -> Ids.item(stack).endsWith("_bed"));
        if (!bett.found()) {
            return false;
        }
        BlockPos fuesse = ziel.blockPosition();
        Vec3 auge = player().getEyePosition();
        BlockPos besterFuss = null;
        Direction besteSeite = null;
        float besterSchaden = 0;
        for (int hoch = 0; hoch <= 1; hoch++) {
            for (Direction seite : SEITEN) {
                BlockPos kopf = fuesse.relative(seite).above(hoch);
                BlockPos fuss = kopf.relative(seite);
                if (!level().getBlockState(kopf).canBeReplaced() || !level().getBlockState(fuss).canBeReplaced()) {
                    continue;
                }
                if (auge.distanceTo(Vec3.atCenterOf(fuss)) > reichweite.get()) {
                    continue;
                }
                Vec3 knall = Vec3.atCenterOf(kopf);
                if (!lohnt(knall)) {
                    continue;
                }
                float schaden = DamageUtils.bettSchaden(ziel, knall);
                if (schaden > besterSchaden) {
                    besterSchaden = schaden;
                    besterFuss = fuss;
                    besteSeite = seite;
                }
            }
        }
        if (besterFuss == null) {
            return false;
        }
        // Das Kopfende liegt in Blickrichtung vor dem Fussende - also zum Ziel hin schauen.
        float gier = besteSeite.getOpposite().toYRot();
        float alt = player().getYRot();
        player().connection.send(new ServerboundMovePlayerPacket.Rot(gier, player().getXRot(),
                player().onGround(), player().horizontalCollision));
        player().setYRot(gier);
        boolean ok = BlockUtils.setzen(besterFuss, bett, false, 0, true, false);
        player().setYRot(alt);
        return ok;
    }

    @Override
    public String hudSuffix() {
        return ziel == null ? null : ziel.getName().getString();
    }
}
