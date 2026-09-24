// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package net.kyori.adventure.text.serializer.plain;
public interface PlainTextComponentSerializer {
    static PlainTextComponentSerializer plainText() { return null; }
    String serialize(net.kyori.adventure.text.Component c);
}
