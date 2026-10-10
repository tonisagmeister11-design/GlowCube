package org.bukkit.command;
public interface CommandSender { void sendMessage(String m); String getName(); boolean hasPermission(String p); }
