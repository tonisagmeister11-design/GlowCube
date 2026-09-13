package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.disguise.MobDisguise;
import de.adminfield.disguise.NameDisguise;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

/**
 * Sich als ein beliebiges Minecraft-Konto ausgeben. Nur der Owner kommt hier herein.
 */
public final class NameDisguiseMenu extends Menu {

    public NameDisguiseMenu(AdminFieldPlugin plugin, Menu parent) {
        super(plugin, parent);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gradient:#5ad1ff:#a06bff><bold>Fremder Name</bold></gradient>");
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
        if (!this.plugin.access().isOwner(this.viewer.getUniqueId())) {
            this.viewer.closeInventory();
            return;
        }
        NameDisguise names = NameDisguise.get(this.plugin);
        String current = names.nameOf(this.viewer.getUniqueId());
        String last = names.lastName(this.viewer.getUniqueId());

        this.set(4, Ui.glowing(Material.PLAYER_HEAD,
                "<gradient:#5ad1ff:#a06bff><bold>Fremder Name & Skin</bold></gradient>",
                List.of("<gray>Gib dich als beliebiges Minecraft-Konto",
                        "<gray>aus – auch als jemand, der gerade",
                        "<gray>hier online ist.",
                        "",
                        "<gray>Übernommen werden Name und Skin:",
                        "<gray>über dem Kopf, in der Tabliste,",
                        "<gray>im Chat und in Todesmeldungen.",
                        "",
                        current == null ? "<gray>Aktuell: <white>dein eigener Name"
                                        : "<gray>Aktuell: <white>" + current)));

        this.set(19, Ui.icon(Material.NAME_TAG, "<aqua><bold>Namen eingeben</bold>",
                List.of("<gray>Irgendein Minecraft-Name,",
                        "<gray>zum Beispiel <white>Dream<gray>.",
                        "",
                        "<yellow>➤ Klicken, dann Namen in den Chat")),
                event -> this.askForName(names));

        this.set(21, Ui.icon(Material.PLAYER_HEAD, "<gold><bold>Online-Spieler wählen</bold>",
                List.of("<gray>Nimm das Aussehen von jemandem an,",
                        "<gray>der gerade mitspielt.",
                        "",
                        "<yellow>➤ Klicken zum Öffnen")),
                event -> new NameTargetMenu(this.plugin, this).open(this.viewer));

        this.drawToggle(names, current, last);

        this.set(31, Ui.icon(Material.BOOK, "<gray>Gut zu wissen",
                List.of("<gray>Deinen eigenen neuen Skin siehst du",
                        "<gray>erst nach einem Neu-Verbinden –",
                        "<gray>alle anderen sofort.",
                        "",
                        "<gray>Die Verkleidung überlebt einen Rejoin",
                        "<gray>und wird beim Serverstopp aufgehoben.")));

        this.backButton(36);
        this.closeButton(44);
        this.fillEmpty();
    }

    /** Ein Schalter: an setzt den zuletzt benutzten Namen wieder auf, aus macht dich zu dir. */
    private void drawToggle(NameDisguise names, String current, String last) {
        boolean on = current != null;
        List<String> lore = on
                ? List.of("<gray>Du gibst dich gerade aus als",
                          "<white>" + current + "<gray>.")
                : last == null
                        ? List.of("<gray>Noch kein Name gewählt.",
                                  "<dark_gray>Erst oben einen eintragen.")
                        : List.of("<gray>Zuletzt benutzt: <white>" + last,
                                  "<dark_gray>Ein Klick setzt ihn wieder auf.");

        if (!on && last == null) {
            this.set(23, Ui.icon(Material.GRAY_DYE, "<dark_gray><bold>Verkleidung</bold>", lore));
            return;
        }
        this.set(23, Ui.toggle(on, "<bold>Verkleidung", lore), event -> {
            if (on) {
                names.restore(this.viewer.getUniqueId());
                this.plugin.send((CommandSender) this.viewer,
                        "<gray>Du bist wieder <white>" + this.viewer.getName() + "<gray>.");
                this.redraw();
                return;
            }
            if (this.blockedByMob()) {
                return;
            }
            names.disguiseAsync(this.viewer, last, message -> {
                this.plugin.send((CommandSender) this.viewer, message);
                this.refreshLater();
            });
        });
    }

    private void askForName(NameDisguise names) {
        if (this.blockedByMob()) {
            return;
        }
        this.plugin.state().prompt(this.viewer, "Als wen willst du dich ausgeben?", text -> {
            String wanted = text == null ? "" : text.trim();
            names.disguiseAsync(this.viewer, wanted,
                    message -> this.plugin.send((CommandSender) this.viewer, message));
        });
    }

    /** Beides gleichzeitig beisst sich: als Mob ist der Spieler ja ausgeblendet. */
    private boolean blockedByMob() {
        if (!MobDisguise.get(this.plugin).isDisguised(this.viewer.getUniqueId())) {
            return false;
        }
        this.plugin.send((CommandSender) this.viewer,
                "<red>Hebe zuerst deine Mob-Verkleidung auf, sonst beißt sich das.");
        return true;
    }
}
