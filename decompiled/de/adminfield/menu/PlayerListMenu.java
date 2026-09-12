/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Biomes;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import de.adminfield.menu.PlayerActionMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PlayerListMenu
extends Menu {
    private static final int PER_PAGE = 45;

    public PlayerListMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <aqua><bold>Spieler</bold></aqua> <dark_gray>· " + Bukkit.getOnlinePlayers().size() + " online");
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
        ArrayList<Player> arrayList = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        arrayList.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        this.divider(5);
        this.backButton(45);
        this.pager(arrayList.size(), 45, 48, 50);
        this.set(49, Ui.icon(Material.NAME_TAG, "<aqua>Spielerliste", List.of("<gray>Insgesamt online: <white>" + arrayList.size(), "<gray>Die Anzeige aktualisiert sich", "<gray>jede Sekunde von selbst.")));
        this.closeButton(53);
        int n = this.page * 45;
        for (int i = 0; i < 45 && n + i < arrayList.size(); ++i) {
            Player player = (Player)arrayList.get(n + i);
            this.set(i, this.headOf(player), inventoryClickEvent -> new PlayerActionMenu(this.plugin, this, player).open(this.viewer));
        }
        this.fillEmpty();
    }

    private ItemStack headOf(Player player) {
        Location location = player.getLocation();
        double d = 20.0;
        AttributeInstance attributeInstance = player.getAttribute(Attribute.MAX_HEALTH);
        if (attributeInstance != null) {
            d = attributeInstance.getValue();
        }
        double d2 = player.getHealth();
        int n = player.getFoodLevel();
        int n2 = player.getPing();
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("<gray>Welt: <white>" + location.getWorld().getName());
        arrayList.add("<gray>Biom: <white>" + Biomes.name(location.getBlock().getBiome()));
        arrayList.add("<gray>Position: <white>" + Ui.pos(location));
        if (this.viewer.getWorld().equals((Object)player.getWorld())) {
            int n3 = (int)Math.round(this.viewer.getLocation().distance(location));
            arrayList.add("<gray>Entfernung: <white>" + n3 + " Blöcke");
        } else {
            arrayList.add("<gray>Entfernung: <dark_gray>andere Welt");
        }
        arrayList.add("");
        arrayList.add("<gray>Leben  " + Ui.bar(d2, d, 10, "red", "dark_gray") + " <white>" + String.format("%.1f", d2) + "<dark_gray>/<white>" + String.format("%.0f", d));
        arrayList.add("<gray>Hunger " + Ui.bar(n, 20.0, 10, "gold", "dark_gray") + " <white>" + n + "<dark_gray>/<white>20");
        arrayList.add("<gray>Modus: <white>" + Ui.gameMode(player.getGameMode()) + "  <dark_gray>·  <gray>Level: <white>" + player.getLevel());
        arrayList.add("<gray>Ping: " + Ui.gradeLow(n2, 60.0, 140.0) + n2 + " ms");
        ArrayList<String> arrayList2 = new ArrayList<String>();
        if (this.plugin.jail().isJailed(player.getUniqueId())) {
            arrayList2.add("<red>Im Gefängnis");
        }
        if (this.plugin.state().isFrozen(player.getUniqueId())) {
            arrayList2.add("<aqua>Eingefroren");
        }
        if (this.plugin.state().isWatched(player.getUniqueId())) {
            arrayList2.add("<yellow>Beobachtet");
        }
        if (this.plugin.state().isGod(player.getUniqueId())) {
            arrayList2.add("<gold>Unverwundbar");
        }
        if (this.plugin.state().isVanished(player)) {
            arrayList2.add("<dark_gray>Vanish");
        }
        if (!arrayList2.isEmpty()) {
            arrayList.add("");
            arrayList.add(String.join((CharSequence)"<dark_gray> · ", arrayList2));
        }
        arrayList.add("");
        arrayList.add("<yellow>➤ Klicken für Aktionen");
        String string = player.equals((Object)this.viewer) ? "<aqua><bold>" + player.getName() + "</bold></aqua> <dark_gray>(du)" : "<white><bold>" + player.getName() + "</bold>";
        return Ui.head((OfflinePlayer)player, string, arrayList);
    }
}

