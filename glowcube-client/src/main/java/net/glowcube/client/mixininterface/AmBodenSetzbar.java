package net.glowcube.client.mixininterface;

/*
 * Schnittstellen, die ein Mixin einer Spielklasse anhaengt, liegen bewusst
 * nicht im mixin-Ordner: dort steht nur, was auch in glowcube.mixins.json
 * eingetragen ist. Sonst laesst sich nicht mehr pruefen, ob ein Mixin
 * vergessen wurde.
 */

/** Ein Bewegungspaket, dessen "ich stehe"-Flagge sich noch aendern laesst. */
public interface AmBodenSetzbar {
    void glowcube$setzeAmBoden(boolean wert);
}
