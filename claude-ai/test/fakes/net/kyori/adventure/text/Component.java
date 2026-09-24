package net.kyori.adventure.text;
public interface Component { String plain(); static TextComponent text(String s) { return () -> s; } }
