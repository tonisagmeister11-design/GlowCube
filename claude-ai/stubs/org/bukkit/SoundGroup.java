// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit;
public interface SoundGroup {
    Sound getBreakSound();
    Sound getPlaceSound();
    Sound getHitSound();
    Sound getStepSound();
}
