package net.glowcube.plugin;

/** Was ein Agent abbaut - dieselben Namen wie im Client. */
enum Auftrag {
    ERZ("Erz-Agent"),
    STEIN("Stein-Agent"),
    HOLZ("Holz-Agent");

    final String anzeigename;

    Auftrag(String anzeigename) {
        this.anzeigename = anzeigename;
    }
}
