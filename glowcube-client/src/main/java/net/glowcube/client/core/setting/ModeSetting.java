package net.glowcube.client.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.List;

/** Eine Auswahl aus festen Werten, im GUI durch Klick weitergeschaltet. */
public final class ModeSetting extends Setting {
    private final List<String> modes;
    private int index;

    public ModeSetting(String name, String description, String selected, String... modes) {
        super(name, description);
        this.modes = Arrays.asList(modes);
        this.index = Math.max(0, this.modes.indexOf(selected));
    }

    public String get() {
        return modes.get(index);
    }

    public boolean is(String mode) {
        return get().equalsIgnoreCase(mode);
    }

    public void cycle(int direction) {
        int size = modes.size();
        index = ((index + direction) % size + size) % size;
    }

    public List<String> modes() {
        return modes;
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(get());
    }

    @Override
    public void load(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            int found = modes.indexOf(json.getAsString());
            if (found >= 0) {
                index = found;
            }
        }
    }
}
