package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.disguise.MobDisguise;
import de.adminfield.disguise.MobKind;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Einstieg in die Mob-Verkleidung. Nur der Owner kommt hier herein.
 */
public final class MobDisguiseMenu extends Menu {

    public MobDisguiseMenu(AdminFieldPlugin plugin, Menu parent) {
        super(plugin, parent);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gradient:#7bffb0:#00a86b><bold>Mob-Verkleidung</bold></gradient>");
    }

    @Override
    protected int rows() {
        return 6;
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
        MobDisguise disguise = MobDisguise.get(this.plugin);
        MobKind own = disguise.kindOf(this.viewer.getUniqueId());

        this.set(4, Ui.glowing(Material.CREEPER_HEAD, "<gradient:#7bffb0:#00a86b><bold>Mob-Verkleidung</bold></gradient>",
                List.of("<gray>Macht aus einem Spieler für alle",
                        "<gray>anderen einen echten Mob – nicht nur",
                        "<gray>ein anderer Skin.",
                        "",
                        "<gray>Der Verkleidete bekommt <white>keine",
                        "<gray>Nachricht und keinen Namen mehr",
                        "<gray>über dem Kopf.",
                        "",
                        "<gray>Verkleidet gerade: <white>" + disguise.count())));

        this.set(20, Ui.icon(Material.PLAYER_HEAD, "<aqua><bold>Dich selbst verkleiden</bold>",
                List.of("<gray>Du siehst dich weiter normal,",
                        "<gray>alle anderen sehen den Mob.",
                        "",
                        own == null ? "<gray>Aktuell: <white>nicht verkleidet"
                                   : "<gray>Aktuell: <white>" + own.label(),
                        "<yellow>➤ Klicken zum Aussuchen")),
                event -> new MobPickMenu(this.plugin, this, this.viewer).open(this.viewer));

        this.set(22, Ui.icon(Material.NAME_TAG, "<gold><bold>Anderen Spieler verkleiden</bold>",
                List.of("<gray>Er merkt nichts davon: kein Chat,",
                        "<gray>keine Meldung, kein Hinweis.",
                        "",
                        "<gray>Erst wenn er sich selbst von außen",
                        "<gray>sieht, fällt es ihm auf.",
                        "<yellow>➤ Klicken zum Auswählen")),
                event -> new MobTargetMenu(this.plugin, this).open(this.viewer));

        if (own != null) {
            this.set(24, Ui.icon(Material.BARRIER, "<red><bold>Eigene Verkleidung aufheben</bold>",
                    List.of("<gray>Du bist gerade ein <white>" + own.label() + "<gray>.",
                            "",
                            "<red>➤ Klicken, um wieder du zu sein")),
                    event -> {
                        disguise.undisguise(this.viewer.getUniqueId());
                        this.plugin.send((CommandSender) this.viewer, "<gray>Deine Verkleidung ist aufgehoben.");
                        this.redraw();
                    });
        } else {
            this.set(24, Ui.icon(Material.GRAY_DYE, "<dark_gray><bold>Eigene Verkleidung aufheben</bold>",
                    List.of("<gray>Du bist gerade nicht verkleidet.")));
        }

        this.divider(3);
        this.drawActiveList(disguise);

        this.set(48, Ui.icon(Material.BUCKET, "<yellow>Reste aufräumen",
                List.of("<gray>Entfernt Mob-Hüllen, die nach einem",
                        "<gray>Serverabsturz übrig geblieben sind.",
                        "",
                        "<dark_gray>Laufende Verkleidungen bleiben.",
                        "<yellow>➤ Klicken")),
                event -> {
                    int removed = disguise.removeStrayMobs();
                    this.plugin.send((CommandSender) this.viewer, removed == 0
                            ? "<gray>Es lagen keine alten Hüllen herum."
                            : "<gray>Aufgeräumt: <white>" + removed + "<gray> alte Hülle(n).");
                });

        this.set(49, Ui.icon(Material.GLOWSTONE_DUST, "<aqua>Unsichtbare sichtbar machen",
                List.of("<gray>Macht jeden Spieler wieder sichtbar,",
                        "<gray>der irgendwo hängen geblieben ist.",
                        "",
                        "<dark_gray>Verkleidete und Vanish bleiben in Ruhe.",
                        "<yellow>➤ Klicken")),
                event -> {
                    int repaired = disguise.repairVisibility(true);
                    this.plugin.send((CommandSender) this.viewer, repaired == 0
                            ? "<gray>Es hing niemand fest."
                            : "<green>" + repaired + "<gray> Spieler wieder sichtbar gemacht.");
                });

        if (disguise.count() > 0) {
            this.set(50, Ui.icon(Material.TNT, "<red><bold>Alle Verkleidungen aufheben</bold>",
                    List.of("<gray>Setzt alle <white>" + disguise.count() + "<gray> Verkleidungen zurück.",
                            "",
                            "<red>➤ Klicken")),
                    event -> {
                        disguise.undisguiseAll();
                        this.plugin.send((CommandSender) this.viewer, "<gray>Alle Verkleidungen sind aufgehoben.");
                        this.redraw();
                    });
        }

        this.backButton(45);
        this.closeButton(53);
        this.fillEmpty();
    }

    /** Zeile 5: wer ist gerade als was unterwegs. */
    private void drawActiveList(MobDisguise disguise) {
        List<MobDisguise.Active> entries = new ArrayList<>(disguise.all());
        if (entries.isEmpty()) {
            this.set(31, Ui.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Niemand ist verkleidet",
                    List.of("<dark_gray>Hier stehen später alle verkleideten",
                            "<dark_gray>Spieler mit einem Klick zum Aufheben.")));
            return;
        }
        int slot = 27;
        for (MobDisguise.Active entry : entries) {
            if (slot > 35) {
                break;
            }
            Player online = Bukkit.getPlayer(entry.player());
            String name = online != null ? online.getName() : entry.playerName();
            String label = entry.kind() == null ? "unbekannt" : entry.kind().label();
            List<String> lore = List.of(
                    "<gray>Sieht für alle aus wie ein <white>" + label,
                    online == null ? "<dark_gray>gerade offline" : "<dark_gray>online",
                    "",
                    "<red>➤ Klicken zum Aufheben");
            this.set(slot, online != null ? Ui.head(online, "<white><bold>" + name + "</bold>", lore)
                                          : Ui.icon(Material.SKELETON_SKULL, "<gray><bold>" + name + "</bold>", lore),
                    event -> {
                        disguise.undisguise(entry.player());
                        this.plugin.send((CommandSender) this.viewer,
                                "<gray>Verkleidung von <white>" + name + "<gray> aufgehoben.");
                        this.redraw();
                    });
            slot++;
        }
    }
}
