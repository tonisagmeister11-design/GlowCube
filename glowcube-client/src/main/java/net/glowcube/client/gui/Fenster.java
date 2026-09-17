package net.glowcube.client.gui;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

import java.util.HashSet;
import java.util.Set;

/**
 * Ein Fenster im ClickGUI - eines je Kategorie.
 *
 * <p>Der Aufbau ist der von Meteor: kein grosses Panel mit Reitern, sondern
 * mehrere kleine Fenster, die man frei hinschiebt, wohin man sie haben will.
 * Wer nur Combat und Render braucht, klappt den Rest ein und hat sie beim
 * naechsten Start noch genauso liegen.
 */
public final class Fenster {
    public final Category kategorie;
    public float x;
    public float y;
    public boolean eingeklappt;
    /** Wie weit die Liste in diesem Fenster gescrollt ist. */
    public float scroll;

    /** Module, deren Einstellungen gerade ausgeklappt sind. */
    public final Set<String> offen = new HashSet<>();

    /** Gemessene Hoehe des letzten Bildes - fuer Klicks und Scrollgrenzen. */
    public float hoehe;

    public Fenster(Category kategorie, float x, float y) {
        this.kategorie = kategorie;
        this.x = x;
        this.y = y;
    }

    public boolean istOffen(Module module) {
        return offen.contains(module.name());
    }

    public void umschalten(Module module) {
        if (!offen.remove(module.name())) {
            offen.add(module.name());
        }
    }
}
