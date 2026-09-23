package net.glowcube.client.agent;

/** Was ein Agent tut. Die Zusatzangabe (Erzart, Ausruestung, Schematic) kommt aus dem Menue. */
public enum Auftrag {
    ERZ("Erz-Agent"),
    STEIN("Stein-Agent"),
    HOLZ("Holz-Agent"),
    WAECHTER("Guardian-Agent"),
    BAUMEISTER("Builder-Agent");

    private final String name;

    Auftrag(String name) {
        this.name = name;
    }

    public String anzeigename() {
        return name;
    }

    /** Die drei Abbau-Agenten - sie sammeln Beute und haben X-Ray. */
    public boolean baut() {
        return this == ERZ || this == STEIN || this == HOLZ;
    }
}
