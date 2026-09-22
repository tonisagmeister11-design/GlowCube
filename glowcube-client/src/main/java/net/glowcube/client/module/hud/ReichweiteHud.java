package net.glowcube.client.module.hud;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.hud.TextHudModul;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;

/**
 * Wie weit der letzte Schlag reichte - aus AxolotlClient ({@code ReachHud},
 * nach KronHUD). Gemessen wird vom Auge entlang des Blicks bis dorthin, wo er
 * die Trefferbox schneidet; nach zwei Sekunden ohne Schlag faellt die Anzeige
 * auf 0 zurueck.
 */
public final class ReichweiteHud extends TextHudModul {
    private final NumberSetting stellen = register(
            new NumberSetting("Nachkommastellen", "Wie genau die Reichweite angezeigt wird", 2, 0, 4, 1));

    private double letzte = -1;
    private long zeit;

    public ReichweiteHud() {
        super("Reichweite", "Zeigt die Reichweite deines letzten Schlags", Category.PVP_HUD);
    }

    @Override
    public boolean onEntityAttack(Entity ziel) {
        if (mc.player != null && ziel != null) {
            letzte = abstand(mc.player, ziel);
            zeit = System.currentTimeMillis();
        }
        return false;
    }

    private static double abstand(Entity angreifer, Entity ziel) {
        Vec3 auge = angreifer.getEyePosition(1.0f);
        Vec3 ende = auge.add(angreifer.getViewVector(1.0f).scale(6));
        AABB box = ziel.getBoundingBox();
        Optional<Vec3> treffer = box.clip(auge, ende);
        if (treffer.isPresent()) {
            return auge.distanceTo(treffer.get());
        }
        // Blick streift die Box nicht (Server-Latenz): naechster Punkt der Box.
        double x = Math.max(box.minX, Math.min(auge.x, box.maxX));
        double y = Math.max(box.minY, Math.min(auge.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(auge.z, box.maxZ));
        return auge.distanceTo(new Vec3(x, y, z));
    }

    @Override
    protected String text() {
        if (letzte < 0 || zeit + 2000 < System.currentTimeMillis()) {
            letzte = -1;
            return "0 Bloecke";
        }
        StringBuilder muster = new StringBuilder("0");
        if (stellen.getInt() > 0) {
            muster.append('.').append("0".repeat(stellen.getInt()));
        }
        DecimalFormat format = new DecimalFormat(muster.toString(), DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(letzte) + " Bloecke";
    }
}
