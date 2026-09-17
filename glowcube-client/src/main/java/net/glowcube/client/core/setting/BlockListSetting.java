package net.glowcube.client.core.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Eine Liste von Registry-IDs ("minecraft:diamond_ore") - das Herz von X-Ray.
 *
 * <p>Welche Registry gemeint ist, steht in {@link #registry()}. Voreingestellt
 * sind Bloecke; NoInteract fuehrt damit auch seine Wesenliste, denn eine
 * Liste von IDs bleibt eine Liste von IDs - nur die Vorschlaege im Fenster
 * kommen aus einem anderen Verzeichnis.
 */
public final class BlockListSetting extends Setting {
    /** "block" oder "entity_type". */
    private final String registry;
    private final Set<String> ids = new LinkedHashSet<>();

    public BlockListSetting(String name, String description, String... defaults) {
        this(name, description, "block", defaults);
    }

    public BlockListSetting(String name, String description, String registry, String... defaults) {
        super(name, description);
        this.registry = registry;
        for (String id : defaults) {
            ids.add(normalise(id));
        }
    }

    public String registry() {
        return registry;
    }

    public boolean istWesen() {
        return "entity_type".equals(registry);
    }

    private static String normalise(String id) {
        String trimmed = id.trim().toLowerCase(java.util.Locale.ROOT);
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    public boolean contains(String id) {
        return ids.contains(id);
    }

    public boolean add(String id) {
        return ids.add(normalise(id));
    }

    public boolean remove(String id) {
        return ids.remove(normalise(id));
    }

    public Set<String> ids() {
        return ids;
    }

    public int size() {
        return ids.size();
    }

    @Override
    public JsonElement save() {
        JsonArray array = new JsonArray();
        for (String id : ids) {
            array.add(id);
        }
        return array;
    }

    @Override
    public void load(JsonElement json) {
        if (json == null || !json.isJsonArray()) {
            return;
        }
        ids.clear();
        for (JsonElement element : json.getAsJsonArray()) {
            ids.add(normalise(element.getAsString()));
        }
    }
}
