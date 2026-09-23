package net.glowcube.plugin;

/** Was ein Agent tut - dieselben Namen wie im Client. */
enum Auftrag {
    ERZ("Erz-Agent"),
    STEIN("Stein-Agent"),
    HOLZ("Holz-Agent"),
    WAECHTER("Guardian-Agent"),
    BAUMEISTER("Builder-Agent"),
    BAUER("Farm-Agent"),
    TUNNEL("Tunnel-Agent"),
    JAEGER("Jaeger-Agent");

    final String anzeigename;

    Auftrag(String anzeigename) {
        this.anzeigename = anzeigename;
    }
}
