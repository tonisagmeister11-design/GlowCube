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

        this.set(4, Ui.glowing(Material.PLAYER_HEAD,
                "<gradient:#5ad1ff:#a06bff><bold>Fremder Name & Skin</bold></gradient>",
                List.of("<gray>Gib den Namen eines beliebigen",
                        "<gray>Minecraft-Kontos ein – auch von",
                        "<gray>jemandem, der gar nicht hier spielt.",
                        "",
                        "<gray>Der Skin wird bei Mojang geholt und",
                        "<gray>zusammen mit dem Namen übernommen:",
                        "<gray>über dem Kopf und in der Tabliste.",
                        "",
                        current == null ? "<gray>Aktuell: <white>dein eigener Name"
                                        : "<gray>Aktuell: <white>" + current)));

        this.set(20, Ui.icon(Material.NAME_TAG, "<aqua><bold>Namen eingeben</bold>",
                List.of("<gray>Zum Beispiel <white>Dream<gray>.",
                        "",
                        "<yellow>➤ Klicken, dann Namen in den Chat")),
                event -> this.askForName(names));

        if (current != null) {
            this.set(24, Ui.icon(Material.BARRIER, "<red><bold>Wieder du selbst sein</bold>",
                    List.of("<gray>Du gibst dich gerade als",
                            "<white>" + current + "<gray> aus.",
                            "",
                            "<red>➤ Klicken zum Zurücksetzen")),
                    event -> {
                        names.restore(this.viewer.getUniqueId());
                        this.plugin.send((CommandSender) this.viewer,
                                "<gray>Du bist wieder <white>" + this.viewer.getName() + "<gray>.");
                        this.redraw();
                    });
        } else {
            this.set(24, Ui.icon(Material.GRAY_DYE, "<dark_gray><bold>Wieder du selbst sein</bold>",
                    List.of("<gray>Du trägst gerade deinen eigenen Namen.")));
        }

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

    private void askForName(NameDisguise names) {
        if (MobDisguise.get(this.plugin).isDisguised(this.viewer.getUniqueId())) {
            this.plugin.send((CommandSender) this.viewer,
                    "<red>Hebe zuerst deine Mob-Verkleidung auf, sonst beißt sich das.");
            return;
        }
        this.plugin.state().prompt(this.viewer, "Als wen willst du dich ausgeben?", text -> {
            String wanted = text == null ? "" : text.trim();
            names.disguiseAsync(this.viewer, wanted,
                    message -> this.plugin.send((CommandSender) this.viewer, message));
        });
    }
}
