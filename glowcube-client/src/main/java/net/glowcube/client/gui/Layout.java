package net.glowcube.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.glowcube.client.core.Category;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wo die Fenster liegen und was eingeklappt ist.
 *
 * <p>Absichtlich getrennt vom Bildschirm selbst: der wird bei jedem Oeffnen
 * neu gebaut, die Anordnung soll aber bleiben - auch ueber einen Neustart
 * hinweg. Deshalb liegt sie hier statisch und wandert mit in die
 * Konfigurationsdatei.
 */
public final class Layout {
    private static final Map<Category, Fenster> FENSTER = new LinkedHashMap<>();

    private Layout() {
    }

    /**
     * Die Anfangsanordnung: drei Spalten nebeneinander, von links nach
     * rechts gefuellt. Sieben Kategorien passen damit auf jeden Bildschirm,
     * auf dem man Minecraft spielen kann.
     */
    private static void anlegen() {
        if (!FENSTER.isEmpty()) {
            return;
        }
        // Je Bereich eine eigene Anordnung, jeweils von links oben. Weil im
        // ClickGUI immer nur ein Bereich gleichzeitig sichtbar ist, duerfen
        // sich die Fenster verschiedener Bereiche ruhig ueberlappen.
        for (Category.Bereich bereich : Category.Bereich.values()) {
            int i = 0;
            for (Category kategorie : Category.values()) {
                if (kategorie.bereich() != bereich) {
                    continue;
                }
                int spalte = i % 4;
                int zeile = i / 4;
                FENSTER.put(kategorie, new Fenster(kategorie,
                        16 + spalte * 162, 44 + zeile * 240));
                i++;
            }
        }
    }

    public static List<Fenster> alle() {
        anlegen();
        return new ArrayList<>(FENSTER.values());
    }

    /** Nur die Fenster des gewaehlten Bereichs - Hacks oder Kein Hack. */
    public static List<Fenster> imBereich(Category.Bereich bereich) {
        anlegen();
        List<Fenster> result = new ArrayList<>();
        for (Fenster fenster : FENSTER.values()) {
            if (fenster.kategorie.bereich() == bereich) {
                result.add(fenster);
            }
        }
        return result;
    }

    public static Fenster fuer(Category kategorie) {
        anlegen();
        return FENSTER.get(kategorie);
    }

    /** Alles zurueck auf die Anfangsanordnung. */
    public static void zuruecksetzen() {
        FENSTER.clear();
        anlegen();
    }

    public static JsonObject speichern() {
        anlegen();
        JsonObject wurzel = new JsonObject();
        for (Fenster fenster : FENSTER.values()) {
            JsonObject eintrag = new JsonObject();
            eintrag.addProperty("x", fenster.x);
            eintrag.addProperty("y", fenster.y);
            eintrag.addProperty("eingeklappt", fenster.eingeklappt);
            JsonArray offen = new JsonArray();
            fenster.offen.forEach(offen::add);
            eintrag.add("offen", offen);
            wurzel.add(fenster.kategorie.name(), eintrag);
        }
        return wurzel;
    }

    public static void laden(JsonObject wurzel) {
        anlegen();
        if (wurzel == null) {
            return;
        }
        for (Fenster fenster : FENSTER.values()) {
            if (!wurzel.has(fenster.kategorie.name())) {
                continue;
            }
            JsonObject eintrag = wurzel.getAsJsonObject(fenster.kategorie.name());
            if (eintrag.has("x")) {
                fenster.x = eintrag.get("x").getAsFloat();
            }
            if (eintrag.has("y")) {
                fenster.y = eintrag.get("y").getAsFloat();
            }
            if (eintrag.has("eingeklappt")) {
                fenster.eingeklappt = eintrag.get("eingeklappt").getAsBoolean();
            }
            fenster.offen.clear();
            if (eintrag.has("offen")) {
                eintrag.getAsJsonArray("offen").forEach(e -> fenster.offen.add(e.getAsString()));
            }
        }
    }
}
