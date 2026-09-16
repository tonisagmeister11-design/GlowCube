package net.glowcube.client.module.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Render3D;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet die Flugbahn dessen, was man gerade in der Hand haelt.
 *
 * Gerechnet wird dieselbe Schrittfolge, die das Spiel fuer Wurfgeschosse
 * benutzt: jede Runde Schwerkraft abziehen, Luftwiderstand anwenden, Position
 * fortschreiben. Sobald die Bahn in einen Block laeuft, ist Schluss - dort
 * sitzt der Einschlag. Nachempfunden Trajectories aus BleachHack (GPL-3.0).
 */
public final class Trajectories extends Module {
    private static final int BAHN = 0xFFFFD166;
    private static final int TREFFER = 0xFFFF5F6D;

    private final NumberSetting steps = register(new NumberSetting("Steps",
            "Wie weit vorausgerechnet wird", 160, 20, 400, 20));

    public Trajectories() {
        super("Trajectories", "Zeigt, wo das Geschoss landet", Category.RENDER);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (!inGame()) {
            return;
        }
        ItemStack hand = player().getMainHandItem();
        double schwerkraft = schwerkraftFuer(hand);
        if (schwerkraft <= 0.0) {
            return;
        }

        Vec3 blick = player().getViewVector(1.0f);
        Vec3 punkt = player().getEyePosition(1.0f);
        // Wurfgeschosse starten mit etwa dieser Geschwindigkeit; Pfeile sind
        // schneller, aber ohne Spannungsgrad ist das die brauchbare Naeherung.
        double tempo = hand.getItem() instanceof BowItem || hand.getItem() instanceof TridentItem ? 2.5 : 1.5;
        Vec3 fahrt = blick.scale(tempo);

        int schritte = steps.getInt();
        for (int i = 0; i < schritte; i++) {
            Vec3 naechster = punkt.add(fahrt);

            if (!level().getBlockState(BlockPos.containing(naechster)).isAir()) {
                Render3D.box(context,
                        new AABB(naechster.x - 0.15, naechster.y - 0.15, naechster.z - 0.15,
                                naechster.x + 0.15, naechster.y + 0.15, naechster.z + 0.15),
                        TREFFER, true);
                return;
            }

            Render3D.line(context, punkt, naechster, BAHN);
            punkt = naechster;
            fahrt = fahrt.scale(0.99).subtract(0.0, schwerkraft, 0.0);
        }
    }

    /** 0 heisst: damit wirft man nicht. */
    private double schwerkraftFuer(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof BowItem || item instanceof TridentItem) {
            return 0.05;
        }
        if (item instanceof SnowballItem || item instanceof EnderpearlItem) {
            return 0.03;
        }
        if (item instanceof ThrowablePotionItem) {
            return 0.05;
        }
        return 0.0;
    }
}
