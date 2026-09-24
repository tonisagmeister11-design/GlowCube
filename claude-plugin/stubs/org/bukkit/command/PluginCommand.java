// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.command;
public final class PluginCommand extends Command {
    public void setExecutor(CommandExecutor executor) {}
    public void setTabCompleter(TabCompleter completer) {}
}
