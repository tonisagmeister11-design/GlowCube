package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.disguise.MobDisguise;
import de.adminfield.disguise.MobKind;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Die Mob-Auswahl fuer einen bestimmten Spieler.
 */
public final class MobPickMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final Player target;
    private MobKind.Group filter;

    public MobPickMenu(AdminFieldPlugin plugin, Menu parent, Player target) {
        super(plugin, parent);
        this.target = target;
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gradient:#7bffb0:#00a86b><bold>Mob wählen</bold></gradient> <dark_gray>· "
                + this.target.getName());
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        if (!this.plugin.access().isOwner(this.viewer.getUniqueId())) {
            this.viewer.closeInventory();
            return;
        }
        MobDisguise disguise = MobDisguise.get(this.plugin);
        MobKind current = disguise.kindOf(this.target.getUniqueId());
        List<MobKind> kinds = MobKind.available(this.filter);

        this.divider(5);
        this.backButton(45);
        this.groupButton(46, null, "Alle", Material.BOOK);
        this.groupButton(47, MobKind.Group.MONSTER, MobKind.Group.MONSTER.label(), Material.ROTTEN_FLESH);
        this.groupButton(48, MobKind.Group.ANIMAL, MobKind.Group.ANIMAL.label(), Material.WHEAT);
        this.groupButton(49, MobKind.Group.SPECIAL, MobKind.Group.SPECIAL.label(), Material.EMERALD);
        this.pager(kinds.size(), PER_PAGE, 51, 52);
        this.closeButton(53);

        if (current != null) {
            this.set(50, Ui.icon(Material.BARRIER, "<red><bold>Verkleidung aufheben</bold>",
                    List.of("<gray><white>" + this.target.getName() + "<gray> ist gerade",
                            "<gray>ein <white>" + current.label() + "<gray>.",
                            "",
                            "<red>➤ Klicken")),
                    event -> {
                        disguise.undisguise(this.target.getUniqueId());
                        this.plugin.send((CommandSender) this.viewer, this.target.equals(this.viewer)
                                ? "<gray>Deine Verkleidung ist aufgehoben."
                                : "<gray>Verkleidung von <white>" + this.target.getName() + "<gray> aufgehoben.");
                        this.redraw();
                    });
        }

        int start = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < kinds.size(); i++) {
            MobKind kind = kinds.get(start + i);
            boolean active = kind == current;
            List<String> lore = new ArrayList<>();
            lore.add(kind.group().color() + kind.group().label());
            if (kind.baby()) {
                lore.add("<light_purple>Baby-Variante");
            }
            lore.add("");
            if (active) {
                lore.add("<green>▪ Genau so sieht ihn gerade jeder.");
            } else {
                lore.add("<gray>Alle anderen sehen dann einen");
                lore.add("<gray>echten <white>" + kind.label() + "<gray>.");
                lore.add("<yellow>➤ Klicken zum Verkleiden");
            }
            String name = (active ? "<green><bold>" : "<white><bold>") + kind.label() + "</bold>";
            ItemStack item = active ? Ui.glowing(kind.icon(), name, lore) : Ui.icon(kind.icon(), name, lore);
            this.set(i, item, event -> this.apply(disguise, kind));
        }
    }

    private void apply(MobDisguise disguise, MobKind kind) {
        if (!this.target.isOnline()) {
            this.plugin.send((CommandSender) this.viewer, "<red>Der Spieler ist nicht mehr online.");
            return;
        }
        if (!disguise.disguise(this.target, kind)) {
            this.plugin.send((CommandSender) this.viewer,
                    "<red>Diesen Mob kennt die Serverversion nicht: <white>" + kind.label());
            return;
        }
        // Absichtlich nur an den Owner. Das Ziel soll nichts mitbekommen.
        this.plugin.send((CommandSender) this.viewer, this.target.equals(this.viewer)
                ? "<gray>Für alle anderen bist du jetzt ein <white>" + kind.label() + "<gray>."
                : "<gray><white>" + this.target.getName() + "<gray> ist für alle anderen jetzt ein <white>"
                        + kind.label() + "<gray> – ohne es zu merken.");
        this.redraw();
    }

    private void groupButton(int slot, MobKind.Group group, String label, Material icon) {
        boolean selected = this.filter == group;
        int count = MobKind.available(group).size();
        List<String> lore = List.of(
                "<gray>Auswahl: <white>" + count + "<gray> Mobs",
                "",
                selected ? "<green>▪ Wird gerade angezeigt" : "<yellow>➤ Klicken zum Umschalten");
        String name = (selected ? "<green><bold>" : "<gray><bold>") + label + "</bold>";
        this.set(slot, selected ? Ui.glowing(icon, name, lore) : Ui.icon(icon, name, lore), event -> {
            this.filter = group;
            this.page = 0;
            this.redraw();
        });
    }
}
