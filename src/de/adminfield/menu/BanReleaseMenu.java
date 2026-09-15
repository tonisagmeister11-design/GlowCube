package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.ban.BanChest;
import de.adminfield.offline.OfflineStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

    /** Ein bekannter Spieler: Kennung und der Name, unter dem er zuletzt hier war. */
    private record Known(UUID id, String name) {
    }

    /**
     * Alle, die der Server kennt - aus mehreren Quellen zusammengetragen, damit niemand
     * durchrutscht: Anwesende, unsere Abbilder und die Spielerdaten des Servers.
     */
    private List<Known> known() {
        Map<UUID, Known> found = new LinkedHashMap<>();
        try {
            for (Player online : Bukkit.getOnlinePlayers()) {
                found.put(online.getUniqueId(), new Known(online.getUniqueId(), online.getName()));
            }
        } catch (Throwable ignored) {
        }
        OfflineStore store = OfflineStore.instance();
        if (store != null) {
            try {
                for (OfflineStore.Entry entry : store.all()) {
                    found.putIfAbsent(entry.id(), new Known(entry.id(), entry.name()));
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            for (OfflinePlayer past : Bukkit.getOfflinePlayers()) {
                if (past == null || past.getName() == null) {
                    continue;
                }
                found.putIfAbsent(past.getUniqueId(), new Known(past.getUniqueId(), past.getName()));
            }
        } catch (Throwable ignored) {
            // Aeltere Server geben die Liste nicht her - dann bleibt der Weg ueber den Namen.
        }
        List<Known> out = new ArrayList<>(found.values());
        out.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        return out;
    }

    @Override
    protected void draw() {
        BanChest ban = BanChest.instance();
        List<Known> list = this.known();

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
            Known entry = list.get(start + i);
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
