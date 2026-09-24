package net.kyori.adventure.text.serializer.plain;
public interface PlainTextComponentSerializer {
    static PlainTextComponentSerializer plainText() { return c -> c.plain(); }
    String serialize(net.kyori.adventure.text.Component c);
}
