package net.glowcube.client.agent;

/** Was ein Agent abbaut. Die Erzart waehlt beim Erz-Agenten die Einstellung "Erz". */
public enum Auftrag {
    ERZ("Erz-Agent"),
    STEIN("Stein-Agent"),
    HOLZ("Holz-Agent");

    private final String name;

    Auftrag(String name) {
        this.name = name;
    }

    public String anzeigename() {
        return name;
    }
}
