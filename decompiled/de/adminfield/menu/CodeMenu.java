/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.menu.MainMenu;
import de.adminfield.menu.Menu;
import de.adminfield.menu.RoleMenu;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

public final class CodeMenu
extends Menu {
    private final StringBuilder input = new StringBuilder();
    private String status = "";

    public CodeMenu(AdminFieldPlugin adminFieldPlugin) {
        super(adminFieldPlugin, null);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gradient:#ffd166:#ff8a00><bold>Owner-Code</bold></gradient>");
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void draw() {
        if (this.plugin.access().isLockedOut(this.viewer.getUniqueId())) {
            this.drawLockout();
            return;
        }
        if (this.plugin.access().ownerTaken()) {
            this.set(22, Ui.icon(Material.BARRIER, "<red><bold>Owner bereits vergeben</bold>", List.of("<gray>Aktueller Owner: <gold>" + this.plugin.access().ownerName())));
            this.set(40, Ui.icon(Material.ARROW, "<yellow>Zurück"), inventoryClickEvent -> new RoleMenu(this.plugin).open(this.viewer));
            this.fillEmpty();
            return;
        }
        this.set(4, this.display());
        this.digit(10, 1);
        this.digit(11, 2);
        this.digit(12, 3);
        this.digit(19, 4);
        this.digit(20, 5);
        this.digit(21, 6);
        this.digit(28, 7);
        this.digit(29, 8);
        this.digit(30, 9);
        this.digit(38, 0);
        this.set(14, Ui.icon(Material.SHEARS, "<yellow>Letzte Ziffer löschen"), inventoryClickEvent -> {
            if (!this.input.isEmpty()) {
                this.input.deleteCharAt(this.input.length() - 1);
            }
            this.status = "";
            this.redraw();
        });
        this.set(23, Ui.icon(Material.BUCKET, "<yellow>Alles löschen"), inventoryClickEvent -> {
            this.input.setLength(0);
            this.status = "";
            this.redraw();
        });
        this.set(32, Ui.glowing(Material.LIME_DYE, "<green><bold>Bestätigen</bold>", List.of("<gray>Prüft den eingegebenen Code.")), inventoryClickEvent -> this.submit());
        this.set(42, Ui.icon(Material.ARROW, "<yellow>Zurück"), inventoryClickEvent -> new RoleMenu(this.plugin).open(this.viewer));
        this.fillEmpty();
    }

    private void drawLockout() {
        long l = this.plugin.access().lockoutSeconds(this.viewer.getUniqueId());
        this.set(22, Ui.icon(Material.BARRIER, "<red><bold>Gesperrt</bold>", List.of("<gray>Zu viele Fehlversuche.", "", "<gray>Noch <white>" + l + " Sekunden<gray> warten.")));
        this.set(40, Ui.icon(Material.BARRIER, "<red>Schließen"), inventoryClickEvent -> this.viewer.closeInventory());
        this.fillEmpty();
    }

    private ItemStack display() {
        int n = this.plugin.access().codeLength();
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < n; ++i) {
            stringBuilder.append(i < this.input.length() ? "<white>● " : "<dark_gray>○ ");
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("<gray>Gib den Owner-Code über die Ziffern ein.");
        arrayList.add("");
        arrayList.add(stringBuilder.toString().trim());
        arrayList.add("");
        if (!this.status.isEmpty()) {
            arrayList.add(this.status);
            arrayList.add("");
        }
        arrayList.add("<dark_gray>Verbleibende Versuche: " + this.plugin.access().attemptsLeft(this.viewer.getUniqueId()));
        arrayList.add("<dark_gray>Einmal richtig – danach bist du dauerhaft Owner.");
        return Ui.glowing(Material.TRIPWIRE_HOOK, "<gold><bold>Code eingeben</bold>", arrayList);
    }

    private void digit(int n, int n2) {
        ItemStack itemStack = Ui.icon(Material.PAPER, "<white><bold>" + n2 + "</bold>", List.of("<dark_gray>Ziffer " + n2));
        itemStack.setAmount(Math.max(1, n2));
        this.set(n, itemStack, inventoryClickEvent -> {
            if (this.input.length() < this.plugin.access().codeLength()) {
                this.input.append(n2);
                this.viewer.playSound(this.viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
            }
            this.status = "";
            this.redraw();
        });
    }

    private void submit() {
        if (this.input.length() < this.plugin.access().codeLength()) {
            this.status = "<yellow>Der Code ist noch nicht vollständig.";
            this.redraw();
            return;
        }
        String string = this.input.toString();
        this.input.setLength(0);
        if (this.plugin.access().claimOwner(this.viewer, string)) {
            this.viewer.closeInventory();
            this.viewer.playSound(this.viewer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            this.viewer.showTitle(Title.title((Component)Ui.mm("<gradient:#ffd166:#ff8a00><bold>OWNER</bold></gradient>"), (Component)Ui.mm("<gray>Du hast jetzt Zugriff auf alles"), (Title.Times)Title.Times.times((Duration)Duration.ofMillis(200L), (Duration)Duration.ofSeconds(3L), (Duration)Duration.ofMillis(800L))));
            this.plugin.send((CommandSender)this.viewer, "<gold>Du bist jetzt der Owner dieses Servers.");
            new MainMenu(this.plugin).open(this.viewer);
            return;
        }
        this.viewer.playSound(this.viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
        if (this.plugin.access().isLockedOut(this.viewer.getUniqueId())) {
            this.viewer.closeInventory();
            this.plugin.send((CommandSender)this.viewer, "<red>Zu viele Fehlversuche. Gesperrt für <white>" + this.plugin.access().lockoutSeconds(this.viewer.getUniqueId()) + " Sekunden<red>.");
            return;
        }
        this.status = "<red>Falscher Code.";
        this.redraw();
    }
}

