package net.glowcube.client.module.karte;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.hud.Meldungen;
import net.glowcube.client.render.Netz;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Todespunkt: stirbt man, wird die Stelle sofort als Wegpunkt "Tod-hh.mm.ss"
 * gespeichert und die Koordinaten stehen im Chat - so findet man seine
 * Sachen wieder. Aeltere Todespunkte werden nach und nach entfernt.
 */
public final class Todespunkt extends Module {
    private final NumberSetting behalten = register(new NumberSetting("Behalten",
            "So viele Todespunkte je Welt bleiben gespeichert", 3, 1, 10, 1));

    private boolean warTot;

    public Todespunkt() {
        super("Todespunkt", "Merkt sich, wo du gestorben bist", Category.KARTE);
    }

    @Override
    public void onTick() {
        boolean tot = player().isDeadOrDying();
        if (tot && !warTot) {
            int x = player().getBlockX();
            int y = player().getBlockY();
            int z = player().getBlockZ();
            String name = "Tod-" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH.mm.ss"));
            aufraeumen();
            Wegpunkte.hinzufuegen(name, x, y, z);
            Netz.nachricht(Component.literal("[GlowCube] Gestorben bei " + x + " " + y + " " + z
                    + " - als Wegpunkt \"" + name + "\" gespeichert."), false);
            Meldungen.melden("Todespunkt: " + x + " " + y + " " + z, 0xFFFF5F6D);
        }
        warTot = tot;
    }

    private void aufraeumen() {
        List<Wegpunkte.Punkt> tode = Wegpunkte.hier().stream().filter(p -> p.name().startsWith("Tod-")).toList();
        for (int i = 0; i <= tode.size() - behalten.getInt(); i++) {
            Wegpunkte.entfernen(tode.get(i).name());
        }
    }

    @Override
    public boolean bleibtNachWeltwechsel() {
        return true;
    }
}
