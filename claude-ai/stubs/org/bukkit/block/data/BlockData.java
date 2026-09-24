// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.block.data;
public interface BlockData extends Cloneable {
    org.bukkit.Material getMaterial();
    String getAsString();
    org.bukkit.SoundGroup getSoundGroup();
    BlockData clone();
}
