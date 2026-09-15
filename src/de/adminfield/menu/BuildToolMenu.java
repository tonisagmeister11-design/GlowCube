package de.adminfield.menu;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.build.BuildTool;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

/**
 * Bauen in grossem Massstab: zwei Ecken, ein Block, ein Auftrag.
 */
public final class BuildToolMenu extends Menu {

    public BuildToolMenu(AdminFieldPlugin plugin, Menu parent) {
        super(plugin, parent);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gradient:#7bdcff:#3a7bff><bold>Bauen</bold></gradient>");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    public boolean live() {
        return true;
    }

    private boolean mayBuild() {
        return this.viewer != null && this.viewer.hasPermission("adminfield.build");
    }

    @Override
    protected void draw() {
        BuildTool tool = BuildTool.instance();
        if (tool == null || !this.mayBuild()) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Nicht verfügbar",
                    List.of(tool == null ? "<gray>Das Bau-Werkzeug läuft nicht."
                                         : "<gray>Dir fehlt das Recht dafür.")));
            this.backButton(45);
            this.closeButton(53);
            this.fillEmpty();
            return;
        }

        BuildTool.Selection selection = tool.selection(this.viewer);
        Material chosen = tool.chosen(this.viewer);
        boolean ready = selection.complete();

        this.set(4, Ui.glowing(Material.BRICKS, "<gradient:#7bdcff:#3a7bff><bold>Bauen</bold></gradient>",
                this.status(selection, chosen)));

        this.set(10, Ui.icon(BuildTool.material("GOLDEN_AXE", Material.IRON_PICKAXE),
                "<aqua><bold>Bau-Stab holen</bold>",
                List.of("<gray>Linksklick auf einen Block: <white>Ecke 1",
                        "<gray>Rechtsklick auf einen Block: <white>Ecke 2",
                        "",
                        "<yellow>➤ Klicken zum Holen")),
                event -> this.giveWand(tool));

        this.set(12, Ui.icon(chosen == null ? Material.GRAY_DYE : chosen,
                chosen == null ? "<yellow><bold>Block wählen</bold>"
                               : "<white><bold>Block: " + pretty(chosen) + "</bold>",
                List.of("<gray>Woraus gebaut werden soll.",
                        "",
                        "<yellow>➤ Klicken zum Auswählen")),
                event -> new BuildBlockMenu(this.plugin, this).open(this.viewer));

        this.set(14, Ui.icon(Material.ARROW, "<aqua><bold>Auswahl vergrößern</bold>",
                List.of("<gray>In die Länge, in die Breite,",
                        "<gray>nach oben oder nach unten.",
                        "",
                        "<yellow>➤ Klicken")),
                event -> new BuildExpandMenu(this.plugin, this).open(this.viewer));

        this.set(16, Ui.icon(Material.BARRIER, "<red><bold>Auswahl löschen</bold>",
                List.of("<gray>Setzt beide Ecken zurück.")),
                event -> {
                    tool.clearSelection(this.viewer);
                    this.plugin.send((CommandSender) this.viewer, "<gray>Auswahl gelöscht.");
                    this.redraw();
                });

        this.job(19, tool, BuildTool.Job.FILL, Material.STONE, ready,
                List.of("<gray>Setzt den Block überall in der Auswahl.",
                        "<gray>Das ist das Ausfüllen."));
        this.job(20, tool, BuildTool.Job.WALLS, BuildTool.material("STONE_BRICKS", Material.BRICKS), ready,
                List.of("<gray>Nur die vier Seiten – eine Mauer",
                        "<gray>rundherum, oben und unten offen.",
                        "",
                        "<dark_gray>Ist die Auswahl nur einen Block dünn,",
                        "<dark_gray>wird daraus eine einzelne Wand."));
        this.job(21, tool, BuildTool.Job.FLOOR, BuildTool.material("SMOOTH_STONE", Material.STONE), ready,
                List.of("<gray>Nur die unterste Ebene –",
                        "<gray>eine Platte als Boden."));
        this.job(22, tool, BuildTool.Job.CEILING, BuildTool.material("SMOOTH_STONE", Material.STONE), ready,
                List.of("<gray>Nur die oberste Ebene –",
                        "<gray>eine Platte als Decke."));
        this.job(23, tool, BuildTool.Job.SHELL, Material.GLASS, ready,
                List.of("<gray>Seiten, Boden und Decke –",
                        "<gray>eine Schachtel, innen hohl."));
        this.job(24, tool, BuildTool.Job.CLEAR, Material.BARRIER, ready,
                List.of("<gray>Räumt die Auswahl leer.",
                        "<gray>Der gewählte Block spielt keine Rolle."));

        int steps = tool.undoSteps(this.viewer);
        String last = tool.lastJob(this.viewer);
        this.set(31, steps > 0
                ? Ui.glowing(Material.CLOCK, "<green><bold>Rückgängig</bold>",
                        List.of("<gray>Nimmt zurück: <white>" + last,
                                "<gray>Schritte im Speicher: <white>" + steps,
                                "",
                                "<gray>Stellt wieder her, was vorher dastand.",
                                "",
                                "<yellow>➤ Klicken"))
                : Ui.icon(Material.CLOCK, "<gray><bold>Rückgängig</bold>",
                        List.of("<dark_gray>Nichts zurückzunehmen.")),
                event -> {
                    tool.undo(this.viewer);
                    this.refreshLater();
                });

        this.divider(5);
        this.backButton(45);
        this.closeButton(53);
        this.fillEmpty();
    }

    private List<String> status(BuildTool.Selection selection, Material chosen) {
        List<String> lore = new ArrayList<>();
        if (!selection.complete()) {
            lore.add("<gray>Setze zwei Ecken mit dem Bau-Stab:");
            lore.add("<gray>Linksklick die erste, Rechtsklick die zweite.");
            if (selection.first() != null) {
                lore.add("");
                lore.add("<gray>Ecke 1: <white>" + point(selection.first()));
                lore.add("<dark_gray>Ecke 2 fehlt noch.");
            }
            return lore;
        }
        int[] size = selection.size();
        lore.add("<gray>Welt: <white>" + selection.world());
        lore.add("<gray>Ecke 1: <white>" + point(selection.first()));
        lore.add("<gray>Ecke 2: <white>" + point(selection.second()));
        lore.add("");
        lore.add("<gray>Länge × Höhe × Breite:");
        lore.add("<white>" + size[0] + " × " + size[1] + " × " + size[2]
                + " <gray>= <white>" + selection.volume() + "<gray> Blöcke");
        lore.add("");
        lore.add(chosen == null ? "<red>Noch kein Block gewählt."
                                : "<gray>Block: <white>" + pretty(chosen));
        return lore;
    }

    private void job(int slot, BuildTool tool, BuildTool.Job job, Material icon, boolean ready,
            List<String> description) {
        List<String> lore = new ArrayList<>(description);
        lore.add("");
        lore.add(ready ? "<yellow>➤ Klicken zum Ausführen"
                       : "<dark_gray>Erst beide Ecken setzen.");
        this.set(slot, ready ? Ui.icon(icon, "<gold><bold>" + job.label() + "</bold>", lore)
                             : Ui.icon(Material.GRAY_STAINED_GLASS_PANE,
                                     "<gray><bold>" + job.label() + "</bold>", lore),
                event -> {
                    if (!ready) {
                        this.plugin.send((CommandSender) this.viewer,
                                "<red>Setze zuerst beide Ecken mit dem Bau-Stab.");
                        return;
                    }
                    tool.run(this.viewer, job);
                    this.plugin.log().add(ActivityLog.Level.WARN, this.viewer.getName()
                            + " baute: " + job.label() + " (" + tool.selection(this.viewer).volume()
                            + " Blöcke)", this.viewer.getLocation(), null);
                    this.viewer.closeInventory();
                });
    }

    private void giveWand(BuildTool tool) {
        var wand = tool.wand();
        if (wand == null) {
            this.plugin.send((CommandSender) this.viewer, "<red>Der Bau-Stab liess sich nicht bauen.");
            return;
        }
        this.plugin.giveItems(this.viewer, wand);
        this.plugin.send((CommandSender) this.viewer, "<gray>Bau-Stab eingepackt.");
    }

    private static String point(int[] corner) {
        return corner[0] + " / " + corner[1] + " / " + corner[2];
    }

    /** Aus STONE_BRICKS wird "Stone Bricks" - lesbarer als das rohe Wort. */
    static String pretty(Material material) {
        String raw = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(raw.length());
        boolean start = true;
        for (char c : raw.toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ';
        }
        return out.toString();
    }
}
