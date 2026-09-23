package net.glowcube.client.core.setting;

import com.google.gson.JsonElement;

/** Basis aller Einstellungen. Jede Einstellung kann sich selbst nach JSON schreiben und zurueck lesen. */
public abstract class Setting {
    private final String name;
    private final String description;
    private java.util.function.BooleanSupplier sichtbar = () -> true;

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

    /**
     * Nur sichtbar, solange die Bedingung gilt - etwa die Einstellungen eines
     * einzelnen Agenten, solange er im Menue ausgewaehlt ist. Gespeichert wird
     * sie trotzdem immer.
     */
    @SuppressWarnings("unchecked")
    public <T extends Setting> T sichtbarWenn(java.util.function.BooleanSupplier bedingung) {
        this.sichtbar = bedingung;
        return (T) this;
    }

    public boolean sichtbar() {
        return sichtbar.getAsBoolean();
    }

    public abstract JsonElement save();

    public abstract void load(JsonElement json);
}
