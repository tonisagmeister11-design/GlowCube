package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.ban.BanChest;
import de.adminfield.offline.KnownPlayers;
import de.adminfield.offline.OfflineStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
        BanChest ban = BanChest.instance();
        List<OfflineStore.Entry> list = store == null
                ? new ArrayList<>()
                : new ArrayList<>(store.all());
        // Wer gerade wieder online ist, gehoert in die normale Spielerliste.
        list.removeIf(entry -> Bukkit.getPlayer(entry.id()) != null);

        // Dazu jeder, den der Server kennt, von dem es aber noch kein Abbild gibt. Sonst
        // stuende hier nach dem Hochladen erst einmal niemand - ausgerechnet dann, wenn man
        // jemanden dringend bearbeiten will.
        Set<UUID> withSnapshot = new HashSet<>();
        for (OfflineStore.Entry entry : list) {
            withSnapshot.add(entry.id());
        }
        List<KnownPlayers.Known> others = new ArrayList<>();
        for (KnownPlayers.Known known : KnownPlayers.all()) {
            if (!withSnapshot.contains(known.id()) && Bukkit.getPlayer(known.id()) == null) {
                others.add(known);
            }
        }
        int total = list.size() + others.size();

        this.divider(5);
        this.backButton(45);
        this.pager(total, PER_PAGE, 48, 50);
        this.set(49, Ui.icon(Material.ENDER_CHEST, "<gold>Offline-Spieler",
                List.of("<gray>Mit Abbild: <white>" + list.size(),
                        "<gray>Ohne Abbild: <white>" + others.size(),
                        "",
                        "<gray>Inventar und Enderkiste lassen sich",
                        "<gray>hier bearbeiten wie bei Anwesenden.",
                        "",
                        "<gray>Wer noch kein Abbild hat, war seit dem",
                        "<gray>Hochladen nicht da. Bei ihm lässt sich",
                        "<gray>das Leeren trotzdem vormerken.",
                        "",
                        "<dark_gray>Wirksam wird alles beim nächsten Einloggen.")));
        this.releaseButton();
        this.closeButton(53);

        if (store == null) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Offline-Verwaltung läuft nicht",
                    List.of("<gray>Sie ist beim Serverstart nicht hochgekommen.")));
            this.fillEmpty();
            return;
        }
        if (total == 0) {
            this.set(22, Ui.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Noch niemand",
                    List.of("<gray>Der Server kennt noch keine Spieler.")));
            this.fillEmpty();
            return;
        }

        int start = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < total; i++) {
            int index = start + i;
            if (index < list.size()) {
                this.drawSnapshot(i, list.get(index), ban);
            } else {
                this.drawUnknown(i, others.get(index - list.size()), store, ban);
            }
        }
    }

    /** Jemand mit Abbild - sein Inventar liegt vor und lässt sich bearbeiten. */
    private void drawSnapshot(int slot, OfflineStore.Entry entry, BanChest ban) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Offline seit: <white>" + Ui.ago(System.currentTimeMillis() - entry.saved()));
        lore.add("<gray>Gegenstände: <white>" + entry.items());
        if (entry.pending()) {
            lore.add("");
            lore.add("<yellow>▪ Änderung wartet auf seinen nächsten Login");
        }
        if (ban != null && ban.released(entry.id())) {
            lore.add("<green>▪ Freigegeben – die Bannkiste wird ihm beim Einloggen abgenommen");
        } else if (ban != null && ban.blocked(entry.id())) {
            lore.add("<red>▪ Gesperrt – er trägt die Bannkiste");
        }
        lore.add("");
        lore.add("<yellow>➤ Klicken zum Bearbeiten");
        this.set(slot, Ui.head(Bukkit.getOfflinePlayer(entry.id()),
                "<white><bold>" + entry.name() + "</bold>", lore),
                event -> new OfflineInspectMenu(this.plugin, this, entry.id(), entry.name(), false)
                        .open(this.viewer));
    }

    /** Jemand ohne Abbild - sein Inventar kennen wir nicht, vormerken geht trotzdem. */
    private void drawUnknown(int slot, KnownPlayers.Known known, OfflineStore store, BanChest ban) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>Noch kein Abbild");
        lore.add("<gray>Er war seit dem Hochladen nicht da.");
        if (store.queued(known.id(), false) || store.queued(known.id(), true)) {
            lore.add("");
            lore.add("<yellow>▪ Leeren ist vorgemerkt");
        }
        if (ban != null && ban.released(known.id())) {
            lore.add("<green>▪ Freigegeben – die Bannkiste wird ihm beim Einloggen abgenommen");
        }
        lore.add("");
        lore.add("<yellow>➤ Klicken für die Aufträge");
        this.set(slot, Ui.head(Bukkit.getOfflinePlayer(known.id()),
                "<gray><bold>" + known.name() + "</bold>", lore),
                event -> new OfflineInspectMenu(this.plugin, this, known.id(), known.name(), false)
                        .open(this.viewer));
    }

    /**
     * Die Bannkiste wieder abnehmen.
     *
     * <p>Ueber den Namen statt ueber die Liste: die Kiste liegt im echten Inventar des
     * Spielers, und der taucht hier nur auf, wenn von ihm ein Abbild da ist. Genau dann, wenn
     * es klemmt, ist es das nicht - deshalb muss der Weg auch ohne Liste funktionieren.
     */
    private void releaseButton() {
        if (!this.isOwner()) {
            return;
        }
        BanChest ban = BanChest.instance();
        int pending = ban == null ? 0 : ban.pending();
        this.set(47, Ui.icon(Material.NAME_TAG, "<green><bold>Bann aufheben</bold>",
                List.of("<gray>Nimmt einem Spieler die Bannkiste ab –",
                        "<gray>auch wenn er hier gar nicht auftaucht.",
                        "",
                        "<gray>Ist er offline, wird es vorgemerkt:",
                        "<gray>er kommt wieder herein und die Kiste",
                        "<gray>ist beim Einloggen weg.",
                        "",
                        "<gray>Wartende Freigaben: <white>" + pending,
                        "",
                        "<yellow>➤ Klicken für die Liste")),
                event -> new BanReleaseMenu(this.plugin, this).open(this.viewer));
    }

    private boolean isOwner() {
        try {
            return this.viewer != null && this.plugin.access().isOwner(this.viewer.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
