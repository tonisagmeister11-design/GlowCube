package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.offline.OfflineStore;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;

/**
 * Die Spieler, die gerade nicht da sind, deren Sachen sich aber trotzdem bearbeiten lassen.
 */
public final class OfflinePlayerMenu extends Menu {

    private static final int PER_PAGE = 45;

    public OfflinePlayerMenu(AdminFieldPlugin plugin, Menu parent) {
        super(plugin, parent);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gold><bold>Offline-Spieler</bold></gold>");
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
        OfflineStore store = OfflineStore.instance();
        List<OfflineStore.Entry> list = store == null
                ? new ArrayList<>()
                : new ArrayList<>(store.all());
        // Wer gerade wieder online ist, gehoert in die normale Spielerliste.
        list.removeIf(entry -> Bukkit.getPlayer(entry.id()) != null);

        this.divider(5);
        this.backButton(45);
        this.pager(list.size(), PER_PAGE, 48, 50);
        this.set(49, Ui.icon(Material.ENDER_CHEST, "<gold>Offline-Spieler",
                List.of("<gray>Bekannt: <white>" + list.size(),
                        "",
                        "<gray>Inventar und Enderkiste lassen sich",
                        "<gray>hier bearbeiten wie bei Anwesenden.",
                        "",
                        "<dark_gray>Wirksam wird es beim nächsten Einloggen.")));
        this.closeButton(53);

        if (store == null) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Offline-Verwaltung läuft nicht",
                    List.of("<gray>Sie ist beim Serverstart nicht hochgekommen.")));
            this.fillEmpty();
            return;
        }
        if (list.isEmpty()) {
            this.set(22, Ui.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Noch niemand",
                    List.of("<gray>Hier erscheint jeder, der den Server",
                            "<gray>verlässt – ab dieser Plugin-Fassung.",
                            "",
                            "<dark_gray>Wer seither nicht online war, hat noch",
                            "<dark_gray>kein Abbild und fehlt deshalb.")));
            this.fillEmpty();
            return;
        }

        int start = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            OfflineStore.Entry entry = list.get(start + i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Offline seit: <white>" + Ui.ago(System.currentTimeMillis() - entry.saved()));
            lore.add("<gray>Gegenstände: <white>" + entry.items());
            if (entry.pending()) {
                lore.add("");
                lore.add("<yellow>▪ Änderung wartet auf seinen nächsten Login");
            }
            lore.add("");
            lore.add("<yellow>➤ Klicken zum Bearbeiten");
            this.set(i, Ui.head(Bukkit.getOfflinePlayer(entry.id()),
                    "<white><bold>" + entry.name() + "</bold>", lore),
                    event -> new OfflineInspectMenu(this.plugin, this, entry.id(), entry.name(), false)
                            .open(this.viewer));
        }
    }
}
