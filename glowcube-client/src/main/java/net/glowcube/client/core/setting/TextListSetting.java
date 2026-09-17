package net.glowcube.client.core.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Eine Liste freier Texte - Spammer fuehrt damit seine Nachrichten.
 *
 * <p>Absichtlich getrennt von der Registry-Liste: dort gibt es eine feste
 * Menge gueltiger Werte und Vorschlaege, hier darf alles stehen.
 */
public final class TextListSetting extends Setting {
    private final List<String> werte = new ArrayList<>();

    public TextListSetting(String name, String description, String... defaults) {
        super(name, description);
        werte.addAll(Arrays.asList(defaults));
    }

    public List<String> werte() {
        return werte;
    }

    public void add(String text) {
        String sauber = text.trim();
        if (!sauber.isEmpty()) {
            werte.add(sauber);
        }
    }

    public void remove(int index) {
        if (index >= 0 && index < werte.size()) {
            werte.remove(index);
        }
    }

    public int size() {
        return werte.size();
    }

    @Override
    public JsonElement save() {
        JsonArray array = new JsonArray();
        for (String wert : werte) {
            array.add(wert);
        }
        return array;
    }

    @Override
    public void load(JsonElement json) {
        if (json == null || !json.isJsonArray()) {
            return;
        }
        werte.clear();
        for (JsonElement element : json.getAsJsonArray()) {
            werte.add(element.getAsString());
        }
    }
}
