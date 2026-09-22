package net.glowcube.client.module.hud;

import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.TextHudModul;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Geschwindigkeit in Bloecken pro Sekunde - aus AxolotlClient ({@code SpeedHud}, nach KronHUD). */
public final class TempoHud extends TextHudModul {
    private static final DecimalFormat FORMAT = new DecimalFormat("#0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final BooleanSetting waagerecht = register(
            new BooleanSetting("Nur waagerecht", "Fallen und Springen nicht mitzaehlen", true));

    public TempoHud() {
        super("Tempo", "Zeigt deine Geschwindigkeit in Bloecken pro Sekunde");
    }

    @Override
    protected String text() {
        if (mc.player == null) {
            return "4.35 BPS";
        }
        Entity wesen = mc.player.getVehicle() != null ? mc.player.getVehicle() : mc.player;
        Vec3 v = wesen.getDeltaMovement();
        if (waagerecht.get() || (wesen.onGround() && v.y < 0)) {
            v = new Vec3(v.x, 0, v.z);
        }
        return FORMAT.format(v.length() * 20) + " BPS";
    }
}
