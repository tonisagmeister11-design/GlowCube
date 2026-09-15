package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.build.BuildTool;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Die Auswahl in eine Richtung weiterziehen.
 *
 * <p>Immer nur nach aussen, nie ueber die gegenueberliegende Ecke hinweg - so bleibt die
 * Auswahl unter der Hand berechenbar.
 */
public final class BuildExpandMenu extends Menu {

    public BuildExpandMenu(AdminFieldPlugin plugin, Menu parent) {
        super(plugin, parent);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <aqua><bold>Auswahl vergrößern</bold></aqua>");
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void draw() {
        BuildTool tool = BuildTool.instance();
        if (tool == null) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Das Bau-Werkzeug läuft nicht."));
            this.backButton(36);
            this.closeButton(44);
            this.fillEmpty();
            return;
        }

        BuildTool.Selection selection = tool.selection(this.viewer);
        int[] size = selection.size();
        this.set(4, Ui.glowing(Material.ARROW, "<aqua><bold>Auswahl vergrößern</bold>",
                List.of(selection.complete()
                                ? "<gray>Jetzt: <white>" + size[0] + " × " + size[1] + " × " + size[2]
                                        + " <gray>(" + selection.volume() + " Blöcke)"
                                : "<red>Setze zuerst beide Ecken.",
                        "",
                        "<gray>Klick: <white>+1<gray>   Shift-Klick: <white>+10")));

        this.direction(11, tool, "<aqua>Nach Norden", 2, -1, "In die Länge, Richtung Norden");
        this.direction(15, tool, "<aqua>Nach Süden", 2, 1, "In die Länge, Richtung Süden");
        this.direction(19, tool, "<aqua>Nach Westen", 0, -1, "In die Breite, Richtung Westen");
        this.direction(23, tool, "<aqua>Nach Osten", 0, 1, "In die Breite, Richtung Osten");
        this.direction(13, tool, "<green>Nach oben", 1, 1, "Zieht die Auswahl in die Höhe");
        this.direction(31, tool, "<gold>Nach unten", 1, -1, "Zieht die Auswahl in die Tiefe");

        this.backButton(36);
        this.closeButton(44);
        this.fillEmpty();
    }

    private void direction(int slot, BuildTool tool, String title, int axis, int sign, String what) {
        this.set(slot, Ui.icon(Material.SPECTRAL_ARROW, "<bold>" + title + "</bold>",
                List.of("<gray>" + what + ".",
                        "",
                        "<yellow>➤ Klick +1 <dark_gray>·<yellow> Shift +10")),
                event -> {
                    tool.grow(this.viewer, axis, sign * step(event));
                    this.redraw();
                });
    }

    private static int step(InventoryClickEvent event) {
        try {
            return event.isShiftClick() ? 10 : 1;
        } catch (Throwable ignored) {
            return 1;
        }
    }
}
