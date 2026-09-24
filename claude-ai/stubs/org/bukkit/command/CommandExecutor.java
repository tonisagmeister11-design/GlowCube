// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.command;
public interface CommandExecutor { boolean onCommand(CommandSender s, Command c, String l, String[] a); }
