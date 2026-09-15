package de.adminfield.menu;

import com.glowcube.utils.Home;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.homes.Homes;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

/**
 * Das Home-Menue fuer jeden Spieler - eigene Homes ansehen, anspringen, verschieben, loeschen
 * und neue setzen.
 *
 * <p>Jede Aktion laeuft ueber den zugehoerigen Befehl des Home-Systems. Dadurch gelten
 * ueberall dieselben Regeln wie beim Tippen: Home-Limit, gesperrte Dimensionen, Countdown,
 * Sicherheitspruefung, Meldungen und Toene. Am Verhalten von /home und Co. aendert sich
 * dadurch nichts - das Menue ist nur ein bequemerer Weg dorthin.
 */
public final class MyHomesMenu extends Menu {

    private static final int PER_PAGE = 45;

    public MyHomesMenu(AdminFieldPlugin plugin) {
        super(plugin, null);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <green><bold>Deine Homes</bold></green>");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        Homes homes = Homes.instance();
        List<Home> list = homes == null
                ? new ArrayList<>()
                : new ArrayList<>(homes.of(this.viewer.getUniqueId()));

        this.divider(5);
        this.pager(list.size(), PER_PAGE, 45, 53);
        this.drawNewButton(list.size());
        this.set(49, Ui.icon(Material.COMPASS, "<green><bold>Deine Homes</bold>",
                List.of("<gray>Gesetzt: <white>" + list.size() + this.limitText(),
                        "",
                        "<gray>Du kannst so viele Homes haben,",
                        "<gray>wie du magst – jedes mit eigenem Namen.")));
        this.closeButton(51);

        if (homes == null) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Home-System nicht verfügbar",
                    List.of("<gray>Es ist beim Serverstart nicht hochgekommen.")));
            this.fillEmpty();
            return;
        }
        if (list.isEmpty()) {
            this.set(22, Ui.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Noch kein Home",
                    List.of("<gray>Setze unten dein erstes.",
                            "<gray>Du kannst beliebig viele anlegen.")));
            this.fillEmpty();
            return;
        }

        int start = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            Home home = list.get(start + i);
            this.drawHome(i, home);
        }
        this.fillEmpty();
    }

    private void drawHome(int slot, Home home) {
        Location at = home.toLocation();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Welt: <white>" + home.worldName());
        if (at == null) {
            lore.add("<red>Diese Welt ist gerade nicht geladen.");
        } else {
            lore.add("<gray>Ort: <white>" + Ui.pos(at));
        }
        lore.add("");
        lore.add("<yellow>➤ Linksklick <gray>· hinteleportieren");
        lore.add("<green>➤ Shift + Linksklick <gray>· umbenennen");
        lore.add("<aqua>➤ Rechtsklick <gray>· hierher verschieben");
        lore.add("<red>➤ Shift + Rechtsklick <gray>· löschen");

        this.set(slot, Ui.icon(at == null ? Material.GRAY_DYE : Material.RED_BED,
                "<green><bold>" + home.name() + "</bold>", lore), event -> {
            if (!event.isRightClick()) {
                if (event.isShiftClick()) {
                    this.askRename(home.name());
                    return;
                }
                this.viewer.closeInventory();
                this.run("home " + home.name());
                return;
            }
            if (event.isShiftClick()) {
                this.run("delhome " + home.name());
            } else {
                this.run("movehome " + home.name());
            }
            this.redraw();
        });
    }

    private void drawNewButton(int count) {
        this.set(47, Ui.glowing(Material.LIME_DYE, "<green><bold>Neues Home setzen</bold>",
                List.of("<gray>Setzt hier, wo du gerade stehst,",
                        "<gray>ein weiteres Home.",
                        "",
                        "<gray>Du hast <white>" + count + "<gray>. Es gibt kein",
                        "<gray>Limit – gib jedem nur einen Namen.",
                        "",
                        "<yellow>➤ Klicken, dann Namen in den Chat")),
                event -> this.plugin.state().prompt(this.viewer, "Wie soll das neue Home heißen?",
                        text -> this.run("sethome " + text.trim())));
    }

    /** Fragt den neuen Namen im Chat ab und benennt das Home dann um. */
    private void askRename(String oldName) {
        this.plugin.state().prompt(this.viewer, "Wie soll \"" + oldName + "\" künftig heißen?", text -> {
            Homes homes = Homes.instance();
            if (homes == null) {
                this.plugin.send((CommandSender) this.viewer, "<red>Das Home-System läuft gerade nicht.");
                return;
            }
            String problem = homes.rename(this.viewer, oldName, text);
            this.plugin.send((CommandSender) this.viewer, problem != null ? problem
                    : "<green>Home <white>" + oldName + "<green> heißt jetzt <white>" + text.trim() + "<green>.");
            new MyHomesMenu(this.plugin).open(this.viewer);
        });
    }

    /** Fuehrt den passenden Befehl des Home-Systems aus - so gelten ueberall dieselben Regeln. */
    private void run(String command) {
        try {
            this.viewer.performCommand(command);
        } catch (Throwable t) {
            this.plugin.send((CommandSender) this.viewer,
                    "<red>Das hat nicht geklappt (" + t.getClass().getSimpleName() + ").");
        }
    }

    private String limitText() {
        int max = this.plugin.getConfig().getInt("max-homes", 0);
        return max > 0 ? "<dark_gray>/<white>" + max : "";
    }
}
