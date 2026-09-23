package net.glowcube.client.agent;

/** Was ein Agent tut. Die Zusatzangabe (Erzart, Ausruestung, Schematic ...) kommt aus dem Menue. */
public enum Auftrag {
    ERZ("Erz-Agent"),
    STEIN("Stein-Agent"),
    HOLZ("Holz-Agent"),
    WAECHTER("Guardian-Agent"),
    BAUMEISTER("Builder-Agent"),
    BAUER("Farm-Agent"),
    TUNNEL("Tunnel-Agent"),
    JAEGER("Jaeger-Agent");

    private final String name;

    Auftrag(String name) {
        this.name = name;
    }

    public String anzeigename() {
        return name;
    }

    /** Die drei Abbau-Agenten mit X-Ray und Abbau-Tempo. */
    public boolean baut() {
        return this == ERZ || this == STEIN || this == HOLZ;
    }

    /** Haben Beute dabei - bei vollem Inventar koennen sie sie in die Sammelkiste bringen. */
    public boolean sammelt() {
        return this != WAECHTER && this != BAUMEISTER;
    }

    /** Bleiben beim Spieler (Guardian, Jaeger) - der Einsatzort gilt fuer sie nicht. */
    public boolean beimSpieler() {
        return this == WAECHTER || this == JAEGER;
    }
}
