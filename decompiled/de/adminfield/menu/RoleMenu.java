/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.AdminRole;
import de.adminfield.Ui;
import de.adminfield.menu.CodeMenu;
import de.adminfield.menu.MainMenu;
import de.adminfield.menu.Menu;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;

public final class RoleMenu
extends Menu {
    public RoleMenu(AdminFieldPlugin adminFieldPlugin) {
        super(adminFieldPlugin, null);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gradient:#5ad1ff:#a06bff><bold>AdminField</bold></gradient> <dark_gray>· Rang wählen");
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void draw() {
        this.set(4, Ui.glowing(Material.NETHER_STAR, "<gradient:#5ad1ff:#a06bff><bold>Willkommen bei AdminField</bold></gradient>", List.of("<gray>Bevor es losgeht, wähle deinen Rang.", "<gray>Die Wahl gilt dauerhaft.", "", "<gray>Angemeldet als <white>" + this.viewer.getName())));
        this.ownerButton();
        this.adminButton();
        this.closeButton(40);
        this.fillEmpty();
    }

    private void ownerButton() {
        boolean bl = this.plugin.access().ownerTaken();
        boolean bl2 = this.plugin.access().isLockedOut(this.viewer.getUniqueId());
        if (bl) {
            this.set(20, Ui.icon(Material.BARRIER, "<dark_gray><bold>Owner <red>vergeben", List.of("<gray>Es kann nur <white>einen<gray> Owner geben.", "", "<gray>Aktueller Owner: <gold>" + this.plugin.access().ownerName(), "", "<dark_gray>Wähle stattdessen Admin.")));
            return;
        }
        if (bl2) {
            this.set(20, Ui.icon(Material.BARRIER, "<red><bold>Gesperrt", List.of("<gray>Zu viele falsche Codes.", "", "<gray>Noch <white>" + this.plugin.access().lockoutSeconds(this.viewer.getUniqueId()) + " Sekunden<gray> warten.")));
            return;
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("<gray>Der höchste Rang – <white>einmal pro Server<gray>.");
        arrayList.add("");
        arrayList.add("<gold>Zugriff auf alles:");
        arrayList.add("<gray>· Spieler, Bauwerke, Verlauf, Server");
        arrayList.add("<gray>· Welt, Gefängnis, eigene Werkzeuge");
        arrayList.add("<gray>· <white>Lucky Blocks");
        arrayList.add("<gray>· <white>Owner-Ausrüstung");
        arrayList.add("");
        arrayList.add("<gray>Zum Freischalten brauchst du den");
        arrayList.add("<gray>Owner-Code (" + this.plugin.access().codeLength() + " Ziffern).");
        arrayList.add("");
        arrayList.add("<yellow>➤ Klicken und Code eingeben");
        this.set(20, Ui.glowing(Material.GOLDEN_HELMET, "<gradient:#ffd166:#ff8a00><bold>OWNER</bold></gradient>", arrayList), inventoryClickEvent -> new CodeMenu(this.plugin).open(this.viewer));
    }

    private void adminButton() {
        if (!this.viewer.isOp()) {
            this.set(24, Ui.icon(Material.BARRIER, "<dark_gray><bold>Admin <red>gesperrt", List.of("<gray>Für den Admin-Rang brauchst du", "<gray>echte <white>Operator-Rechte<gray> auf dem Server.")));
            return;
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("<gray>Für Moderatoren – <white>beliebig viele<gray>.");
        arrayList.add("");
        arrayList.add("<aqua>Zugriff auf:");
        arrayList.add("<gray>· Spieler, Bauwerke, Verlauf, Server");
        arrayList.add("<gray>· Welt, Gefängnis, eigene Werkzeuge");
        arrayList.add("");
        arrayList.add("<dark_gray>Kein Code nötig – deine OP-Rechte reichen.");
        arrayList.add("");
        arrayList.add("<yellow>➤ Klicken zum Übernehmen");
        this.set(24, Ui.glowing(Material.IRON_HELMET, "<gradient:#5ad1ff:#a06bff><bold>ADMIN</bold></gradient>", arrayList), inventoryClickEvent -> {
            if (!this.plugin.access().claimAdmin(this.viewer)) {
                this.plugin.send((CommandSender)this.viewer, "<red>Dafür brauchst du Operator-Rechte.");
                return;
            }
            this.viewer.closeInventory();
            this.viewer.playSound(this.viewer.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
            this.viewer.showTitle(Title.title((Component)Ui.mm(AdminRole.ADMIN.gradient() + "<bold>Admin</bold></gradient>"), (Component)Ui.mm("<gray>AdminField ist jetzt für dich freigeschaltet"), (Title.Times)Title.Times.times((Duration)Duration.ofMillis(200L), (Duration)Duration.ofSeconds(3L), (Duration)Duration.ofMillis(600L))));
            this.plugin.send((CommandSender)this.viewer, "<gray>Du bist jetzt <aqua>Admin<gray>. Viel Spaß.");
            new MainMenu(this.plugin).open(this.viewer);
        });
    }
}

