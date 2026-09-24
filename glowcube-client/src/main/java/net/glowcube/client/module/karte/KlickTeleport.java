package net.glowcube.client.module.karte;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.hud.Meldungen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Klick-Teleport: mit der mittleren Maustaste (Block auswaehlen) auf einen
 * Block zeigen und klicken - man steht oben drauf. Laeuft ueber den
 * normalen /tp-Befehl, geht also im Einzelspieler mit Cheats und auf
 * Servern mit OP-Rechten. Das ist kein Hack, nur eine Abkuerzung.
 */
public final class KlickTeleport extends Module {
    private final NumberSetting reichweite = register(new NumberSetting("Reichweite",
            "Wie weit das Ziel entfernt sein darf", 300, 16, 1000, 8));

    private boolean warUnten;

    public KlickTeleport() {
        super("Klick-Teleport", "Mittlere Maustaste teleportiert dorthin, wohin du schaust", Category.KARTE);
    }

    @Override
    public void onTick() {
        boolean unten = mc.options.keyPickItem.isDown();
        if (unten && !warUnten && net.glowcube.client.render.Netz.bildschirm() == null) {
            springen();
        }
        warUnten = unten;
    }

    private void springen() {
        HitResult treffer = player().pick(reichweite.get(), 1.0f, false);
        if (!(treffer instanceof BlockHitResult block) || treffer.getType() != HitResult.Type.BLOCK) {
            Meldungen.melden("Klick-Teleport: kein Block in Sicht", 0xFFFFC53D);
            return;
        }
        BlockPos ziel = block.getBlockPos().above();
        player().connection.sendCommand("tp @s " + ziel.getX() + ".5 " + ziel.getY() + " " + ziel.getZ() + ".5");
        Meldungen.melden("Teleport nach " + ziel.getX() + " " + ziel.getY() + " " + ziel.getZ(), 0xFF6BE08A);
    }
}
