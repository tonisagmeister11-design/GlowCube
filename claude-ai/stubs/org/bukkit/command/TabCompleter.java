// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.command;
public interface TabCompleter { java.util.List<String> onTabComplete(CommandSender s, Command c, String l, String[] a); }
