package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.render.Netz;
import net.glowcube.client.util.DamageUtils;
import net.glowcube.client.util.FindItemResult;
import net.glowcube.client.util.Ids;
import net.glowcube.client.util.InvUtils;
import net.glowcube.client.util.Ziele;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * CrystalAura: legt End-Kristalle neben das Ziel und zuendet sie.
 *
 * <p>Jede moegliche Stelle (Obsidian oder Grundgestein mit Luft darueber)
 * wird mit der Vanilla-Explosionsformel durchgerechnet: wie viel trifft das
 * Ziel, wie viel einen selbst. Gelegt wird dort, wo das Ziel am meisten und
 * man selbst hoechstens "Max Selbstschaden" abbekommt. Kristalle, die schon
 * liegen, werden nach derselben Rechnung zerschlagen. Im Kreativmodus zaehlt
 * der eigene Schaden nicht - dort nimmt man keinen.
 */
public final class CrystalAura extends Module {
    private final NumberSetting reichweite = register(new NumberSetting("Reichweite",
            "Wie weit gelegt und geschlagen wird", 4.5, 2.0, 6.0, 0.5));
    private final NumberSetting zielReichweite = register(new NumberSetting("Zielreichweite",
            "Wie weit nach Zielen gesucht wird", 10, 4, 16, 1));
    private final NumberSetting minSchaden = register(new NumberSetting("Min Schaden",
            "So viel muss das Ziel mindestens abbekommen", 6, 0, 36, 0.5));
    private final NumberSetting maxSelbst = register(new NumberSetting("Max Selbstschaden",
            "So viel darf man selbst hoechstens abbekommen", 8, 0, 36, 0.5));
    private final NumberSetting legePause = register(new NumberSetting("Lege-Pause",
            "Ticks zwischen zwei gelegten Kristallen", 2, 0, 10, 1));
    private final NumberSetting schlagPause = register(new NumberSetting("Schlag-Pause",
            "Ticks zwischen zwei zerschlagenen Kristallen", 2, 0, 10, 1));
    private final BooleanSetting spieler = register(new BooleanSetting("Spieler", "Spieler angreifen", true));
    private final BooleanSetting monster = register(new BooleanSetting("Monster", "Monster angreifen", false));
    private final BooleanSetting nichtSterben = register(new BooleanSetting("Nicht sterben",
            "Nie etwas tun, das einen selbst toeten wuerde", true));
    private final BooleanSetting wechseln = register(new BooleanSetting("Wechseln",
            "Von selbst auf die Kristalle in der Hotbar wechseln", true));

    private int legeWarten;
    private int schlagWarten;
    private LivingEntity ziel;

    public CrystalAura() {
        super("CrystalAura", "Legt und zuendet End-Kristalle am Ziel", Category.COMBAT);
    }

    @Override
    public void onTick() {
        ziel = Ziele.naechstes(zielReichweite.get(), spieler.get(), monster.get());
        if (ziel == null) {
            return;
        }
        if (schlagWarten > 0) {
            schlagWarten--;
        } else {
            zerschlagen();
        }
        if (legeWarten > 0) {
            legeWarten--;
        } else {
            legen();
        }
    }

    private boolean lohnt(Vec3 knall) {
        float aufsZiel = DamageUtils.kristallSchaden(ziel, knall);
        if (aufsZiel < minSchaden.get()) {
            return false;
        }
        float selbst = selbstSchaden(knall);
        if (selbst > maxSelbst.get()) {
            return false;
        }
        return !nichtSterben.get() || selbst < player().getHealth() + player().getAbsorptionAmount();
    }

    private float selbstSchaden(Vec3 knall) {
        return player().isCreative() || player().isSpectator() ? 0 : DamageUtils.kristallSchaden(player(), knall);
    }

    private void zerschlagen() {
        double r = reichweite.get();
        for (Entity wesen : level().entitiesForRendering()) {
            if (!Ids.wesen(wesen).equals("end_crystal") || player().distanceTo(wesen) > r) {
                continue;
            }
            if (lohnt(wesen.position())) {
                mc.gameMode.attack(player(), wesen);
                Netz.schwingen(InteractionHand.MAIN_HAND);
                schlagWarten = schlagPause.getInt();
                return;
            }
        }
    }

    private void legen() {
        FindItemResult kristall = InvUtils.findeInHotbar(stack -> Ids.item(stack).equals("end_crystal"));
        if (!kristall.found()) {
            return;
        }
        double r = reichweite.get();
        int ri = (int) Math.ceil(r);
        BlockPos mitte = player().blockPosition();
        Vec3 auge = player().getEyePosition();
        BlockPos beste = null;
        float besterSchaden = 0;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    BlockPos b = mitte.offset(dx, dy, dz);
                    String id = Ids.block(level().getBlockState(b));
                    if (!id.equals("obsidian") && !id.equals("bedrock")) {
                        continue;
                    }
                    BlockPos oben = b.above();
                    if (!level().getBlockState(oben).isAir() || auge.distanceTo(Vec3.atCenterOf(b)) > r) {
                        continue;
                    }
                    if (!level().getEntities((Entity) null, new AABB(oben).expandTowards(0, 1, 0)).isEmpty()) {
                        continue;
                    }
                    Vec3 knall = new Vec3(b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5);
                    if (!lohnt(knall)) {
                        continue;
                    }
                    float schaden = DamageUtils.kristallSchaden(ziel, knall);
                    if (schaden > besterSchaden) {
                        besterSchaden = schaden;
                        beste = b;
                    }
                }
            }
        }
        if (beste == null) {
            return;
        }
        InteractionHand hand = kristall.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (hand == InteractionHand.MAIN_HAND && player().getInventory().getSelectedSlot() != kristall.slot()) {
            if (!wechseln.get()) {
                return;
            }
            InvUtils.tausche(kristall.slot(), false);
        }
        BlockHitResult treffer = new BlockHitResult(Vec3.atCenterOf(beste).add(0, 0.5, 0), Direction.UP, beste, false);
        mc.gameMode.useItemOn(player(), hand, treffer);
        Netz.schwingen(hand);
        legeWarten = legePause.getInt();
    }

    @Override
    public String hudSuffix() {
        return ziel == null ? null : ziel.getName().getString();
    }
}
