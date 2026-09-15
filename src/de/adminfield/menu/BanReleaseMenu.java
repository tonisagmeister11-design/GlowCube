package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.ban.BanChest;
import de.adminfield.offline.KnownPlayers;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

/**
 * Wem die Bannkiste wieder abgenommen wird.
 *
 * <p>Die Liste kommt aus den Spielerdaten des Servers, nicht aus unseren eigenen Dateien.
 * Deshalb steht hier jeder, der jemals hier war - auch wenn das Plugin frisch hochgeladen wurde
 * und von ihm noch kein Abbild hat. Genau dann braucht man diese Liste ja.
 */
public final class BanReleaseMenu extends Menu {

    private static final int PER_PAGE = 45;

    public BanReleaseMenu(AdminFieldPlugin plugin, Menu parent) {
        super(plugin, parent);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <green><bold>Bann aufheben</bold></green>");
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
        BanChest ban = BanChest.instance();
        List<KnownPlayers.Known> list = KnownPlayers.all();

        this.divider(5);
        this.backButton(45);
        this.pager(list.size(), PER_PAGE, 48, 50);
        this.set(49, Ui.icon(Material.NAME_TAG, "<green>Bann aufheben",
                List.of("<gray>Bekannte Spieler: <white>" + list.size(),
                        "<gray>Wartende Freigaben: <white>" + (ban == null ? 0 : ban.pending()),
                        "",
                        "<gray>Klick nimmt dem Spieler die Bannkiste ab.",
                        "<gray>Ist er nicht da, wird es vorgemerkt und",
                        "<gray>beim nächsten Einloggen erledigt.")));
        this.set(47, Ui.icon(Material.WRITABLE_BOOK, "<yellow>Namen eintippen",
                List.of("<gray>Falls jemand hier nicht auftaucht.",
                        "",
                        "<yellow>➤ Klicken, dann Namen in den Chat")),
                event -> this.askForName());
        this.closeButton(53);

        if (ban == null) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Bannkiste läuft nicht",
                    List.of("<gray>Sie ist beim Serverstart nicht hochgekommen.")));
            this.fillEmpty();
            return;
        }
        if (list.isEmpty()) {
            this.set(22, Ui.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Niemand bekannt",
                    List.of("<gray>Der Server kennt keine Spielerdaten.",
                            "",
                            "<gray>Nimm den Weg über <white>Namen eintippen<gray>.")));
            this.fillEmpty();
            return;
        }

        int start = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            KnownPlayers.Known entry = list.get(start + i);
            boolean waiting = ban.released(entry.id());
            boolean locked = ban.blocked(entry.id());
            List<String> lore = new ArrayList<>();
            if (waiting) {
                lore.add("<green>▪ Freigabe wartet auf sein nächstes Einloggen");
            } else if (locked) {
                lore.add("<red>▪ Gesperrt – die Bannkiste liegt in seinem Abbild");
            } else {
                lore.add("<gray>Ob er die Kiste hat, zeigt sich erst,");
                lore.add("<gray>wenn er wieder da ist – freigeben");
                lore.add("<gray>kannst du ihn trotzdem.");
            }
            lore.add("");
            lore.add("<yellow>➤ Klicken zum Freigeben");
            this.set(i, Ui.head(Bukkit.getOfflinePlayer(entry.id()),
                    (locked ? "<red><bold>" : "<white><bold>") + entry.name() + "</bold>", lore),
                    event -> {
                        this.plugin.send((CommandSender) this.viewer,
                                ban.release(entry.id(), entry.name()));
                        this.redraw();
                    });
        }
    }

    private void askForName() {
        this.plugin.state().prompt(this.viewer, "Wen soll die Bannkiste wieder freigeben?", text -> {
            BanChest ban = BanChest.instance();
            if (ban == null) {
                this.plugin.send((CommandSender) this.viewer, "<red>Die Bannkiste läuft gerade nicht.");
                return;
            }
            this.plugin.send((CommandSender) this.viewer, ban.release(text));
        });
    }
}
