package net.glowcube.client.mixin;

/** Ein Bewegungspaket, dessen "ich stehe"-Flagge sich noch aendern laesst. */
public interface AmBodenSetzbar {
    void glowcube$setzeAmBoden(boolean wert);
}
