package net.glowcube.client.module.hud;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.util.Theme;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Session-Statistik: was man seit dem Einschalten (bzw. dem Zuruecksetzen)
 * geschafft hat - Spielzeit, abgebaute Bloecke, Kills, Tode und die
 * zurueckgelegte Strecke.
 */
public final class SessionStatistik extends HudModul {
    private final BooleanSetting zuruecksetzen = register(new BooleanSetting("Zuruecksetzen",
            "Einschalten setzt alle Zahlen auf null (schaltet sich selbst wieder aus)", false));

    private long start;
    private int bloecke;
    private int kills;
    private int tode;
    private double strecke;
    private Vec3 letzte;
    private BlockPos abbau;
    private boolean warTot;
    private final Map<Entity, Long> geschlagen = new HashMap<>();
    private final String[] zeilen = new String[5];

    public SessionStatistik() {
        super("Session-Statistik", "Spielzeit, Bloecke, Kills, Tode und Strecke dieser Sitzung", false, Category.HUD);
    }

    @Override
    public void onEnable() {
        nullen();
    }

    private void nullen() {
        start = System.currentTimeMillis();
        bloecke = 0;
        kills = 0;
        tode = 0;
        strecke = 0;
        letzte = null;
        abbau = null;
        geschlagen.clear();
    }

    @Override
    public boolean onBlockBreak(BlockPos pos) {
        abbau = pos;
        return false;
    }

    @Override
    public boolean onEntityAttack(Entity ziel) {
        geschlagen.put(ziel, System.currentTimeMillis());
        return false;
    }

    @Override
    public void onTick() {
        if (zuruecksetzen.get()) {
            nullen();
            zuruecksetzen.set(false);
        }
        if (abbau != null && level().getBlockState(abbau).isAir()) {
            bloecke++;
            abbau = null;
        }
        long jetzt = System.currentTimeMillis();
        Iterator<Map.Entry<Entity, Long>> it = geschlagen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Long> e = it.next();
            if (e.getKey() instanceof LivingEntity lebend && lebend.isDeadOrDying()) {
                kills++;
                it.remove();
            } else if (jetzt - e.getValue() > 5000 || e.getKey().isRemoved()) {
                it.remove();
            }
        }
        boolean tot = player().isDeadOrDying();
        if (tot && !warTot) {
            tode++;
        }
        warTot = tot;
        Vec3 hier = player().position();
        if (letzte != null) {
            double dx = hier.x - letzte.x;
            double dz = hier.z - letzte.z;
            double schritt = Math.sqrt(dx * dx + dz * dz);
            // Teleports und Weltwechsel nicht mitzaehlen.
            if (schritt < 10) {
                strecke += schritt;
            }
        }
        letzte = hier;
    }

    @Override
    public void vorbereiten() {
        long sekunden = (System.currentTimeMillis() - start) / 1000;
        zeilen[0] = String.format(Locale.ROOT, "Zeit %d:%02d:%02d", sekunden / 3600, sekunden / 60 % 60, sekunden % 60);
        zeilen[1] = "Bloecke " + bloecke;
        zeilen[2] = "Kills " + kills;
        zeilen[3] = "Tode " + tode;
        zeilen[4] = strecke >= 1000
                ? String.format(Locale.ROOT, "Strecke %.2f km", strecke / 1000)
                : String.format(Locale.ROOT, "Strecke %d m", (int) strecke);
    }

    @Override
    public float breite(HudZeichner z) {
        float w = 70;
        for (String zeile : zeilen) {
            if (zeile != null) {
                w = Math.max(w, z.breite(zeile) + 8);
            }
        }
        return w;
    }

    @Override
    public float hoehe() {
        return 5 * 10 + 14;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        z.text("Session", x + 4, y + 3, Theme.ACCENT_A, true);
        for (int i = 0; i < zeilen.length; i++) {
            if (zeilen[i] != null) {
                z.text(zeilen[i], x + 4, y + 14 + i * 10, Theme.TEXT, true);
            }
        }
    }
}
