/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Biomes;
import de.adminfield.Ui;
import de.adminfield.menu.DeathFateMenu;
import de.adminfield.menu.Menu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class DeathTargetMenu
extends Menu {
    public DeathTargetMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <dark_red><bold>Death Note</bold></dark_red> <dark_gray>· Namen eintragen");
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
        ArrayList<Player> arrayList = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        arrayList.removeIf(player -> player.equals((Object)this.viewer));
        arrayList.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        this.divider(5);
        this.backButton(45);
        this.set(49, Ui.icon(Material.WRITABLE_BOOK, "<dark_red>Namen eintragen", List.of("<gray>Wähle den Spieler aus,", "<gray>dann im nächsten Schritt sein Schicksal.")));
        this.closeButton(53);
        if (arrayList.isEmpty()) {
            this.set(22, Ui.icon(Material.STRUCTURE_VOID, "<gray>Niemand sonst online"));
            this.fillEmpty();
            return;
        }
        for (int i = 0; i < arrayList.size() && i < 45; ++i) {
            Player player2 = (Player)arrayList.get(i);
            this.set(i, this.headOf(player2), inventoryClickEvent -> new DeathFateMenu(this.plugin, this, player2).open(this.viewer));
        }
        this.fillEmpty();
    }

    private ItemStack headOf(Player player) {
        Location location = player.getLocation();
        boolean bl = this.plugin.deathNote().isListed(player.getUniqueId());
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("<gray>Welt: <white>" + location.getWorld().getName());
        arrayList.add("<gray>Biom: <white>" + Biomes.name(location.getBlock().getBiome()));
        arrayList.add("<gray>Position: <white>" + Ui.pos(location));
        arrayList.add("<gray>Leben: <white>" + String.format("%.1f", player.getHealth()) + "  <dark_gray>·  <gray>Hunger: <white>" + player.getFoodLevel());
        arrayList.add("");
        if (bl) {
            arrayList.add("<dark_red>Steht bereits im Death Note.");
            arrayList.add("<dark_gray>Ein neuer Eintrag ersetzt den alten.");
            arrayList.add("");
        }
        arrayList.add("<yellow>➤ Klicken zum Auswählen");
        return Ui.head((OfflinePlayer)player, (bl ? "<dark_red>" : "<white>") + "<bold>" + player.getName() + "</bold>", arrayList);
    }
}

