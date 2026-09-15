package de.adminfield.homes;

import com.glowcube.utils.Home;
import com.glowcube.utils.HomeCommands;
import com.glowcube.utils.HomeManager;
import com.glowcube.utils.JoinListener;
import com.glowcube.utils.SidebarManager;
import com.glowcube.utils.TeleportManager;
import de.adminfield.AdminFieldPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Haengt das frueher eigenstaendige GlowCubeUtils in dieses Plugin ein.
 *
 * <p>Dessen Klassen sind unveraendert uebernommen. Moeglich ist das, weil sie alle ein
 * schlichtes {@link JavaPlugin} entgegennehmen und nie die alte Hauptklasse verlangen - die
 * wird hier also gar nicht gebraucht. Aufgebaut und wieder abgeraeumt wird genau in der
 * Reihenfolge, die deren eigenes onEnable und onDisable hatten, damit sich am Verhalten von
 * /sethome, /home und dem Rest nichts aendert.
 */
public final class Homes {

    /** Die fuenf Befehle, die frueher GlowCubeUtils angemeldet hat. */
    private static final String[] COMMANDS = {"sethome", "home", "delhome", "homes", "movehome"};

    /** Wo das alte Plugin seine Daten liegen hatte. */
    private static final String OLD_FOLDER = "GlowCubeUtils";

    private static Homes instance;

    private final AdminFieldPlugin plugin;
    private final HomeManager homeManager;
    private final SidebarManager sidebar;
    private final TeleportManager teleports;

    private Homes(AdminFieldPlugin plugin) {
        this.plugin = plugin;
        this.homeManager = new HomeManager(plugin);
        this.sidebar = new SidebarManager(this.homeManager);
        this.teleports = new TeleportManager(plugin);
    }

    /** Beim Serverstart aufbauen. Entspricht dem alten GlowCubeUtils.onEnable. */
    public static synchronized void start(AdminFieldPlugin plugin) {
        if (instance != null) {
            stop(instance.plugin);
        }
        Homes fresh = new Homes(plugin);
        instance = fresh;
        fresh.install();
    }

    /** Beim Herunterfahren abraeumen. Entspricht dem alten GlowCubeUtils.onDisable. */
    public static synchronized void stop(AdminFieldPlugin plugin) {
        if (instance == null || instance.plugin != plugin) {
            return;
        }
        Homes going = instance;
        instance = null;
        try {
            going.teleports.cancelAll();
        } catch (Throwable ignored) {
        }
        try {
            going.homeManager.save();
        } catch (Throwable ignored) {
        }
        try {
            going.sidebar.clearAll();
        } catch (Throwable ignored) {
        }
    }

    /** Der laufende Verwalter, oder {@code null}, wenn das Home-System nicht hochkam. */
    public static Homes instance() {
        return instance;
    }

    private void install() {
        this.adoptOldData();
        this.homeManager.load();

        this.plugin.getServer().getPluginManager()
                .registerEvents((Listener) new JoinListener(this.plugin, this.sidebar), (Plugin) this.plugin);
        this.plugin.getServer().getPluginManager()
                .registerEvents((Listener) this.teleports, (Plugin) this.plugin);

        HomeCommands commands = new HomeCommands(this.homeManager, this.teleports, this.sidebar);
        for (String name : COMMANDS) {
            PluginCommand command = this.plugin.getCommand(name);
            if (command == null) {
                this.plugin.getLogger().warning("Befehl /" + name + " fehlt in der plugin.yml");
                continue;
            }
            command.setExecutor((CommandExecutor) commands);
            command.setTabCompleter((TabCompleter) commands);
        }
        PluginCommand menu = this.plugin.getCommand("homemenu");
        if (menu == null) {
            this.plugin.getLogger().warning("Befehl /homemenu fehlt in der plugin.yml");
        } else {
            menu.setExecutor((CommandExecutor) new HomeMenuCommand(this.plugin));
        }

        this.sidebar.updateAll();
        this.plugin.getLogger().info("Home-System aktiv (" + this.homeManager.totalHomes() + " Homes).");
    }

    // ------------------------------------------------------------------ Umzug der Altdaten

    /**
     * Holt Homes und Einstellungen aus dem alten Plugin-Ordner herueber.
     *
     * <p>Das Home-System liest seine Dateien jetzt aus dem AdminField-Ordner. Ohne diesen
     * Schritt waeren nach dem Zusammenlegen alle gesetzten Homes scheinbar verschwunden.
     */
    private void adoptOldData() {
        File oldFolder = new File(this.plugin.getDataFolder().getParentFile(), OLD_FOLDER);
        if (!oldFolder.isDirectory()) {
            return;
        }
        this.adoptHomesFile(oldFolder);
        this.adoptSettings(oldFolder);
    }

    private void adoptHomesFile(File oldFolder) {
        File target = new File(this.plugin.getDataFolder(), "homes.yml");
        if (target.exists()) {
            return;
        }
        File source = new File(oldFolder, "homes.yml");
        if (!source.isFile()) {
            return;
        }
        try {
            if (!this.plugin.getDataFolder().exists()) {
                this.plugin.getDataFolder().mkdirs();
            }
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            this.plugin.getLogger().info("Homes aus plugins/" + OLD_FOLDER + "/homes.yml uebernommen.");
        } catch (IOException e) {
            this.plugin.getLogger().severe("Homes aus plugins/" + OLD_FOLDER
                    + "/homes.yml konnten nicht uebernommen werden: " + e.getMessage());
        }
    }

    /**
     * Uebernimmt die alten Home-Einstellungen in die laufende Konfiguration.
     *
     * <p>Bewusst nur im Arbeitsspeicher: Speichern wuerde die AdminField-config.yml neu
     * schreiben und dabei saemtliche Kommentare darin verlieren.
     */
    private void adoptSettings(File oldFolder) {
        File source = new File(oldFolder, "config.yml");
        if (!source.isFile()) {
            return;
        }
        try {
            YamlConfiguration old = YamlConfiguration.loadConfiguration(source);
            boolean any = false;
            for (String key : new String[]{"max-homes", "max-home-name-length", "blocked-environments",
                    "teleport-delay", "cancel-on-move", "teleport-safety"}) {
                if (!old.contains(key)) {
                    continue;
                }
                this.plugin.getConfig().set(key, old.get(key));
                any = true;
            }
            if (any) {
                this.plugin.getLogger().info("Home-Einstellungen aus plugins/" + OLD_FOLDER
                        + "/config.yml uebernommen. Wer sie kuenftig in der AdminField-config.yml"
                        + " pflegen will, loescht den alten Ordner.");
            }
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Alte Home-Einstellungen liessen sich nicht lesen ("
                    + t.getClass().getSimpleName() + ") - es gelten die aus der AdminField-config.yml.");
        }
    }

    // ------------------------------------------------------------------ Fuer das Spielermenue

    /** Namen wie beim Setzen eines Homes: Buchstaben, Zahlen, Unterstrich und Bindestrich. */
    private static final java.util.regex.Pattern VALID_NAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9_\\-]+");

    /**
     * Benennt ein bestehendes Home um, ohne es zu verschieben.
     *
     * <p>Das Home-System kennt dafuer keinen Befehl - hier wird der Eintrag deshalb ueber
     * seine oeffentliche Verwaltung neu abgelegt und der alte entfernt. Die Regeln fuer den
     * Namen sind dieselben wie bei /sethome.
     *
     * @return {@code null}, wenn es geklappt hat, sonst die fertige Fehlermeldung
     */
    public String rename(Player player, String oldName, String newName) {
        UUID id = player.getUniqueId();
        Home existing = this.homeManager.getHome(id, oldName);
        if (existing == null) {
            return "<red>Das Home <white>" + oldName + "<red> gibt es nicht mehr.";
        }
        String wanted = newName == null ? "" : newName.trim();
        if (wanted.isEmpty() || !VALID_NAME.matcher(wanted).matches()) {
            return "<red>Ungültiger Name. Erlaubt sind Buchstaben, Zahlen, <yellow>_<red> und <yellow>-<red>.";
        }
        int max = this.homeManager.getMaxNameLength();
        if (wanted.length() > max) {
            return "<red>Der Name ist zu lang (maximal <yellow>" + max + "<red> Zeichen).";
        }
        boolean sameName = wanted.equalsIgnoreCase(oldName);
        if (!sameName && this.homeManager.hasHome(id, wanted)) {
            return "<red>Du hast schon ein Home namens <white>" + wanted + "<red>.";
        }

        this.homeManager.setHome(id, new Home(wanted, existing.worldId(), existing.worldName(),
                existing.x(), existing.y(), existing.z(), existing.yaw(), existing.pitch()));
        if (!sameName) {
            // Nur wenn sich der Schluessel wirklich unterscheidet - sonst wuerde das gerade
            // gesetzte Home gleich wieder geloescht.
            this.homeManager.deleteHome(id, oldName);
        }
        this.refreshSidebar(player);
        return null;
    }

    /** Zieht die Seitenleiste nach, damit die Home-Zahl darin stimmt. */
    public void refreshSidebar(Player player) {
        try {
            this.sidebar.update(player);
        } catch (Throwable ignored) {
        }
    }

    /** Die Homes eines Spielers, aufsteigend nach Namen. */
    public List<Home> of(UUID player) {
        List<Home> found = this.homeManager.getHomes(player);
        if (found == null || found.isEmpty()) {
            return Collections.emptyList();
        }
        found.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        return found;
    }
}
