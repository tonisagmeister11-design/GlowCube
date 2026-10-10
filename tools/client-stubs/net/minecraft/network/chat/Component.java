package net.minecraft.network.chat;
public interface Component {
    String getString();
    static MutableComponent literal(String s) { return null; }
}
