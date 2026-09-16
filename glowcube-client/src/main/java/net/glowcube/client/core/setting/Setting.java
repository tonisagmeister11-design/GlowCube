package net.glowcube.client.core.setting;

import com.google.gson.JsonElement;

/** Basis aller Einstellungen. Jede Einstellung kann sich selbst nach JSON schreiben und zurueck lesen. */
public abstract class Setting {
    private final String name;
    private final String description;

    protected Setting(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public abstract JsonElement save();

    public abstract void load(JsonElement json);
}
