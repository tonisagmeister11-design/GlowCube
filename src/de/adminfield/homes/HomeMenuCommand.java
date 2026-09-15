package de.adminfield.homes;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.menu.MyHomesMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /homemenu} - oeffnet jedem Spieler sein eigenes Home-Menue.
 *
 * <p>Ein eigener Befehl mit eigener Klasse. Die Befehle des Home-Systems (/home, /homes,
 * /sethome, /delhome, /movehome) bleiben unangetastet.
 */
public final class HomeMenuCommand implements CommandExecutor {

    private final AdminFieldPlugin plugin;

    public HomeMenuCommand(AdminFieldPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            this.plugin.send(sender, "<gray>Das Home-Menü geht nur im Spiel.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("glowcube.home")) {
            this.plugin.send(sender, "<red>Dafür hast du keine Berechtigung.");
            return true;
        }
        new MyHomesMenu(this.plugin).open(player);
        return true;
    }
}
